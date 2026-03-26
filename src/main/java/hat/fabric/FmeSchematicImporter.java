package hat.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.datafixer.fix.BlockStateFlattening;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class FmeSchematicImporter {
    private static final int MAX_BLOCKS = 200_000;
    private static final java.util.ArrayList<Long> LAST_PASTE_KEYS = new java.util.ArrayList<>();

    private FmeSchematicImporter() {
    }

    static PasteResult pasteFromFile(Path file, BlockPos origin, int rotationTurns) {
        if (file == null) {
            return PasteResult.error("Missing schematic file.");
        }
        if (!Files.exists(file)) {
            return PasteResult.error("Schematic not found: " + file);
        }
        if (origin == null) {
            return PasteResult.error("Missing paste origin.");
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return PasteResult.error("No world loaded.");
        }

        NbtCompound root;
        try {
            root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
        } catch (IOException ex) {
            return PasteResult.error("Failed to read schematic: " + ex.getMessage());
        }
        if (root == null) {
            return PasteResult.error("Schematic is empty.");
        }

        SchematicData data = decodeSchematic(root);
        if (data == null) {
            return PasteResult.error("Unsupported schematic format.");
        }
        if (data.width <= 0 || data.height <= 0 || data.length <= 0) {
            return PasteResult.error("Invalid schematic size.");
        }
        long volume = (long) data.width * data.height * data.length;
        if (volume > MAX_BLOCKS) {
            return PasteResult.error("Schematic too large (" + volume + " blocks). Max is " + MAX_BLOCKS + ".");
        }

        int turns = Math.floorMod(rotationTurns, 4);
        int rotatedWidth = turns % 2 == 0 ? data.width : data.length;
        int rotatedLength = turns % 2 == 0 ? data.length : data.width;
        int[] rotatedOffset = rotateOffset(data.offsetX, data.offsetZ, data.width, data.length, turns);
        int placed = 0;
        int skipped = 0;
        LAST_PASTE_KEYS.clear();
        java.util.HashSet<Long> seenKeys = new java.util.HashSet<>();
        FmeManager.beginBatch();
        try {
            for (int y = 0; y < data.height; y++) {
                for (int z = 0; z < data.length; z++) {
                    for (int x = 0; x < data.width; x++) {
                        int index = (y * data.length + z) * data.width + x;
                        BlockEntry entry = data.blockAt(index);
                        if (entry == null || entry.block == null) {
                            skipped++;
                            continue;
                        }
                        if (entry.block.getDefaultState().isAir()) {
                            skipped++;
                            continue;
                        }
                        int[] rotated = rotatePos(x, z, data.width, data.length, turns);
                        BlockPos pos = origin.add(
                            rotated[0] + rotatedOffset[0],
                            y + data.offsetY,
                            rotated[1] + rotatedOffset[1]
                        );
                        Block target = client.world.getBlockState(pos).getBlock();
                        if (FmeManager.applyReplacementDirect(pos, target, entry.block, entry.rotation)) {
                            placed++;
                            long key = FmeManager.configKeyFor(pos);
                            if (seenKeys.add(key)) {
                                LAST_PASTE_KEYS.add(key);
                            }
                        } else {
                            skipped++;
                        }
                    }
                }
            }
        } finally {
            FmeManager.endBatch();
        }

        return new PasteResult(placed, skipped, false, null);
    }

    static PasteResult undoLastPaste() {
        if (LAST_PASTE_KEYS.isEmpty()) {
            return PasteResult.error("No schematic paste to undo.");
        }
        int removed = 0;
        FmeManager.beginBatch();
        try {
            for (Long key : LAST_PASTE_KEYS) {
                if (key != null && FmeManager.clearReplacementKey(key)) {
                    removed++;
                }
            }
        } finally {
            FmeManager.endBatch();
        }
        LAST_PASTE_KEYS.clear();
        return new PasteResult(removed, 0, false, null);
    }

    static Path resolveSchematicPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        Path input = Path.of(trimmed);
        if (input.isAbsolute()) {
            return input;
        }
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path schemDir = gameDir.resolve("schematics");
        return schemDir.resolve(input);
    }

    static List<String> listSchematics() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path schemDir = gameDir.resolve("schematics");
        if (!Files.isDirectory(schemDir)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(schemDir)) {
            stream
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.endsWith(".schem") || name.endsWith(".schematic");
                })
                .forEach(path -> names.add(path.getFileName().toString()));
        } catch (IOException ignored) {
        }
        names.sort(Comparator.comparing(String::toLowerCase, String.CASE_INSENSITIVE_ORDER));
        return names;
    }

    private static SchematicData decodeSchematic(NbtCompound root) {
        if (root.contains("Palette") && root.contains("BlockData")) {
            return decodeSponge(root);
        }
        if (root.contains("Blocks")) {
            return decodeMcedit(root);
        }
        return null;
    }

    private static SchematicData decodeSponge(NbtCompound root) {
        int width = getDimension(root, "Width");
        int height = getDimension(root, "Height");
        int length = getDimension(root, "Length");
        if (width <= 0 || height <= 0 || length <= 0) {
            return null;
        }

        NbtCompound palette = root.getCompound("Palette").orElse(null);
        if (palette == null) {
            return null;
        }
        String[] paletteById = buildPalette(palette);
        if (paletteById.length == 0) {
            return null;
        }

        byte[] data = root.getByteArray("BlockData").orElse(null);
        if (data == null) {
            return null;
        }
        int volume = width * height * length;
        int[] ids = decodeVarIntArray(data, volume);
        if (ids == null) {
            return null;
        }

        int[] offset = readOffset(root);
        return new SchematicData(
            width,
            height,
            length,
            offset[0],
            offset[1],
            offset[2],
            ids,
            paletteById,
            new BlockEntry[paletteById.length],
            null
        );
    }

    private static SchematicData decodeMcedit(NbtCompound root) {
        int width = getDimension(root, "Width");
        int height = getDimension(root, "Height");
        int length = getDimension(root, "Length");
        if (width <= 0 || height <= 0 || length <= 0) {
            return null;
        }

        byte[] blocks = root.getByteArray("Blocks").orElse(null);
        if (blocks == null) {
            return null;
        }
        byte[] add = root.contains("AddBlocks")
            ? root.getByteArray("AddBlocks").orElse(new byte[0])
            : new byte[0];

        int volume = width * height * length;
        if (blocks.length < volume) {
            return null;
        }

        int[] ids = new int[volume];
        for (int i = 0; i < volume; i++) {
            int id = blocks[i] & 0xFF;
            if (add.length > 0) {
                int addIndex = i >> 1;
                if (addIndex < add.length) {
                    int addNibble = (add[addIndex] >> ((i & 1) * 4)) & 0xF;
                    id |= addNibble << 8;
                }
            }
            ids[i] = id;
        }

        byte[] legacyData = root.getByteArray("Data").orElse(null);
        if (legacyData == null || legacyData.length < volume) {
            legacyData = new byte[volume];
        }

        int[] offset = readOffset(root);
        return new SchematicData(width, height, length, offset[0], offset[1], offset[2], ids, null, null, legacyData);
    }

    private static int[] decodeVarIntArray(byte[] data, int expected) {
        int[] out = new int[expected];
        int index = 0;
        int i = 0;
        while (index < expected && i < data.length) {
            int value = 0;
            int shift = 0;
            byte b;
            do {
                if (i >= data.length) {
                    return null;
                }
                b = data[i++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            out[index++] = value;
        }
        return index == expected ? out : null;
    }

    private static String[] buildPalette(NbtCompound palette) {
        int max = -1;
        for (String key : palette.getKeys()) {
            int id = palette.getInt(key, -1);
            if (id > max) {
                max = id;
            }
        }
        if (max < 0) {
            return new String[0];
        }
        String[] out = new String[max + 1];
        for (String key : palette.getKeys()) {
            int id = palette.getInt(key, -1);
            if (id >= 0 && id < out.length) {
                out[id] = key;
            }
        }
        return out;
    }

    private static int[] readOffset(NbtCompound root) {
        int x = 0;
        int y = 0;
        int z = 0;
        if (root.contains("WEOffsetX")) {
            x = root.getInt("WEOffsetX", 0);
            y = root.getInt("WEOffsetY", 0);
            z = root.getInt("WEOffsetZ", 0);
        } else if (root.contains("Offset")) {
            int[] arr = root.getIntArray("Offset").orElse(new int[0]);
            if (arr.length >= 3) {
                x = arr[0];
                y = arr[1];
                z = arr[2];
            }
        }
        return new int[] { x, y, z };
    }

    private static int getDimension(NbtCompound root, String key) {
        if (root.getInt(key).isPresent()) {
            return root.getInt(key).orElse(0);
        }
        if (root.getShort(key).isPresent()) {
            return root.getShort(key).orElse((short) 0);
        }
        return 0;
    }

    private static int[] rotatePos(int x, int z, int width, int length, int turns) {
        return switch (turns) {
            case 1 -> new int[] { length - 1 - z, x };
            case 2 -> new int[] { width - 1 - x, length - 1 - z };
            case 3 -> new int[] { z, width - 1 - x };
            default -> new int[] { x, z };
        };
    }

    private static int[] rotateOffset(int offsetX, int offsetZ, int width, int length, int turns) {
        return switch (turns) {
            case 1 -> new int[] { length - 1 - offsetZ, offsetX };
            case 2 -> new int[] { width - 1 - offsetX, length - 1 - offsetZ };
            case 3 -> new int[] { offsetZ, width - 1 - offsetX };
            default -> new int[] { offsetX, offsetZ };
        };
    }

    private static BlockEntry decodePaletteEntry(String stateRaw) {
        if (stateRaw == null || stateRaw.isBlank()) {
            return null;
        }
        String idRaw = stateRaw.split("\\[", 2)[0].trim();
        Identifier id = Identifier.tryParse(idRaw);
        if (id == null) {
            return null;
        }
        Block block = Registries.BLOCK.get(id);
        if (block == null) {
            return null;
        }
        int rotation = rotationFromState(stateRaw);
        return new BlockEntry(block, rotation);
    }

    private static int rotationFromState(String stateRaw) {
        int bracket = stateRaw.indexOf('[');
        int end = stateRaw.lastIndexOf(']');
        if (bracket == -1 || end <= bracket) {
            return 0;
        }
        String props = stateRaw.substring(bracket + 1, end);
        String[] parts = props.split(",");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if (!"facing".equals(kv[0].trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            String value = kv[1].trim().toLowerCase(Locale.ROOT);
            return switch (value) {
                case "east" -> 1;
                case "south" -> 2;
                case "west" -> 3;
                default -> 0;
            };
        }
        return 0;
    }

    private record BlockEntry(Block block, int rotation) {
    }

    private record SchematicData(
        int width,
        int height,
        int length,
        int offsetX,
        int offsetY,
        int offsetZ,
        int[] ids,
        String[] palette,
        BlockEntry[] paletteCache,
        byte[] legacyData
    ) {
        BlockEntry blockAt(int index) {
            if (index < 0 || index >= ids.length) {
                return null;
            }
            if (palette != null) {
                int paletteId = ids[index];
                if (paletteId < 0 || paletteId >= palette.length) {
                    return null;
                }
                if (paletteCache != null && paletteId < paletteCache.length && paletteCache[paletteId] != null) {
                    return paletteCache[paletteId];
                }
                String stateRaw = palette[paletteId];
                BlockEntry entry = decodePaletteEntry(stateRaw);
                if (paletteCache != null && paletteId < paletteCache.length) {
                    paletteCache[paletteId] = entry;
                }
                return entry;
            }
            if (legacyData != null) {
                int blockId = ids[index];
                int data = legacyData[index] & 0xF;
                if (blockId > 255) {
                    return null;
                }
                int legacyStateId = (blockId << 4) | data;
                BlockState state = decodeLegacyState(legacyStateId);
                if (state == null || state.isAir()) {
                    return null;
                }
                int rotation = rotationFromState(state);
                return new BlockEntry(state.getBlock(), rotation);
            }
            Block block = Registries.BLOCK.get(ids[index]);
            if (block == null) {
                return null;
            }
            return new BlockEntry(block, 0);
        }
    }

    private static BlockState decodeLegacyState(int legacyStateId) {
        var dynamic = BlockStateFlattening.lookupState(legacyStateId);
        if (dynamic == null) {
            return net.minecraft.block.Blocks.AIR.getDefaultState();
        }
        return BlockState.CODEC.parse(dynamic)
            .result()
            .orElse(net.minecraft.block.Blocks.AIR.getDefaultState());
    }

    private static int rotationFromState(BlockState state) {
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            return switch (facing) {
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> 0;
            };
        }
        if (state.contains(Properties.FACING)) {
            Direction facing = state.get(Properties.FACING);
            if (facing.getAxis().isHorizontal()) {
                return switch (facing) {
                    case EAST -> 1;
                    case SOUTH -> 2;
                    case WEST -> 3;
                    default -> 0;
                };
            }
        }
        if (state.contains(Properties.HORIZONTAL_AXIS)) {
            return state.get(Properties.HORIZONTAL_AXIS) == Direction.Axis.Z ? 1 : 0;
        }
        return 0;
    }

    record PasteResult(int placed, int skipped, boolean failed, String error) {
        static PasteResult error(String message) {
            return new PasteResult(0, 0, true, message);
        }
    }
}
