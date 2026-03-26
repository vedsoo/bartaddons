package hat.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FmeManager {
    private static final Path SAVE_PATH = FabricLoader.getInstance().getConfigDir().resolve("hat-fme.json");
    private static final Path STATE_PATH = FabricLoader.getInstance().getConfigDir().resolve("hat-fme-state.json");
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir()
        .resolve("fme")
        .resolve("config");
    private static Path currentSavePath = SAVE_PATH;
    private static final Map<Long, Block> POSITION_REPLACEMENTS = new HashMap<>();
    private static final Map<Long, String> POSITION_CUSTOM_TEXTURES = new HashMap<>();
    private static final Map<Long, Block> POSITION_CUSTOM_TEXTURE_SOURCES = new HashMap<>();
    private static final Map<Long, Integer> POSITION_ROTATIONS = new HashMap<>();
    private static final Map<Long, Block> LAST_REPLACE_UNDO = new HashMap<>();
    private static boolean lastReplaceWasCustom = false;
    private static final Map<RemapKey, BlockState> REMAP_STATE_CACHE = new HashMap<>();
    private static final Set<Long> AIR_GHOST_POSITIONS = new HashSet<>();
    private static final Set<Identifier> FAVORITES = new HashSet<>();
    private static final Set<String> CUSTOM_TEXTURE_FAVORITES = new HashSet<>();
    private static boolean batching = false;
    private static boolean batchChanged = false;
    private static boolean enabled = false;
    private static boolean editMode = false;
    private static Block selectedSource = net.minecraft.block.Blocks.STONE;
    private static SelectedSourceType selectedSourceType = SelectedSourceType.BLOCK;
    private static String selectedCustomTexture;
    private static int selectedRotation = 0;
    private static int openScreenInTicks = -1;
    private static ScreenMode openScreenMode = ScreenMode.DEFAULT;
    private static int guiPanelColor = 0xE0161216;
    private static int guiBorderColor = 0x003A2A2F;
    private static int guiTextColor = 0xFFF5E9E1;
    private static int guiAccentTextColor = 0xFFF28C3A;
    private static int selectionBoxColor = 0x552E1C20;
    private static float guiScale = 1.5f;
    private static float guiBlockBrightness = 2.0f;
    private static boolean autoSaveEnabled = true;
    private static boolean skipRemapCacheClear = false;
    private static int offsetX = 0;
    private static int offsetY = 0;
    private static int offsetZ = 0;

    private FmeManager() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isEditMode() {
        return editMode;
    }

    public static boolean toggleEnabled() {
        enabled = !enabled;
        save();
        refreshWorld(null);
        return enabled;
    }

    public static boolean toggleEditMode() {
        editMode = !editMode;
        save();
        refreshWorld(null);
        return editMode;
    }

    public static void setOffset(BlockPos pos) {
        if (pos == null) {
            return;
        }
        offsetX = pos.getX();
        offsetY = pos.getY();
        offsetZ = pos.getZ();
        save();
        refreshWorld(null);
    }

    public static void setOffset(int x, int y, int z) {
        offsetX = x;
        offsetY = y;
        offsetZ = z;
        save();
        refreshWorld(null);
    }

    public static void clearOffset() {
        offsetX = 0;
        offsetY = 0;
        offsetZ = 0;
        save();
        refreshWorld(null);
    }

    public static BlockPos getOffset() {
        return new BlockPos(offsetX, offsetY, offsetZ);
    }

    public static void setSelectedSource(Block source) {
        if (source != null) {
            selectedSource = source;
            selectedSourceType = SelectedSourceType.BLOCK;
            selectedRotation = 0;
            save();
        }
    }

    public static Block getSelectedSource() {
        return selectedSource;
    }

    public static void selectCustomTexture(Path texturePath) {
        if (texturePath == null) {
            return;
        }
        selectedCustomTexture = texturePath.getFileName().toString();
        selectedSourceType = SelectedSourceType.CUSTOM_TEXTURE;
        selectedRotation = 0;
        save();
    }

    public static String getSelectedCustomTextureName() {
        return selectedCustomTexture;
    }

    public static SelectedSourceType getSelectedSourceType() {
        return selectedSourceType;
    }

    public static int rotateSelectedSource() {
        selectedRotation = (selectedRotation + 1) % 4;
        save();
        return selectedRotation;
    }

    public static int getSelectedRotation() {
        return selectedRotation;
    }

    public static void setSelectedRotation(int rotation) {
        selectedRotation = Math.floorMod(rotation, 4);
        save();
    }

    public static void selectCustomTextureName(String fileName, int rotation) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        selectedCustomTexture = fileName;
        selectedSourceType = SelectedSourceType.CUSTOM_TEXTURE;
        selectedRotation = Math.floorMod(rotation, 4);
        save();
    }

    public static boolean applyReplacement(BlockPos pos, Block target) {
        if (pos == null || target == null || selectedSource == null) {
            return false;
        }
        long key = toConfigKey(pos);
        if (selectedSourceType == SelectedSourceType.CUSTOM_TEXTURE && selectedCustomTexture != null) {
            POSITION_CUSTOM_TEXTURES.put(key, selectedCustomTexture);
            POSITION_CUSTOM_TEXTURE_SOURCES.put(key, target);
            POSITION_REPLACEMENTS.remove(key);
            POSITION_ROTATIONS.put(key, selectedRotation);
            if (target.getDefaultState().isAir()) {
                AIR_GHOST_POSITIONS.add(key);
            } else {
                AIR_GHOST_POSITIONS.remove(key);
            }
            markChanged(pos);
            return true;
        }
        if (target == selectedSource) {
            POSITION_REPLACEMENTS.remove(key);
            POSITION_ROTATIONS.remove(key);
            AIR_GHOST_POSITIONS.remove(key);
            POSITION_CUSTOM_TEXTURES.remove(key);
            POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
            markChanged(pos);
            return true;
        }
        POSITION_REPLACEMENTS.put(key, selectedSource);
        POSITION_CUSTOM_TEXTURES.remove(key);
        POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
        POSITION_ROTATIONS.put(key, selectedRotation);
        if (target.getDefaultState().isAir()) {
            AIR_GHOST_POSITIONS.add(key);
        } else {
            AIR_GHOST_POSITIONS.remove(key);
        }
        markChanged(pos);
        return true;
    }

    public static boolean applyReplacementDirect(BlockPos pos, Block target, Block mapped, int rotation) {
        if (pos == null || target == null || mapped == null) {
            return false;
        }
        long key = toConfigKey(pos);
        if (target == mapped) {
            POSITION_REPLACEMENTS.remove(key);
            POSITION_ROTATIONS.remove(key);
            AIR_GHOST_POSITIONS.remove(key);
            POSITION_CUSTOM_TEXTURES.remove(key);
            POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
            markChanged(pos);
            return true;
        }
        POSITION_REPLACEMENTS.put(key, mapped);
        POSITION_CUSTOM_TEXTURES.remove(key);
        POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
        POSITION_ROTATIONS.put(key, Math.floorMod(rotation, 4));
        if (target.getDefaultState().isAir()) {
            AIR_GHOST_POSITIONS.add(key);
        } else {
            AIR_GHOST_POSITIONS.remove(key);
        }
        markChanged(pos);
        return true;
    }

    public static boolean clearReplacement(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        long key = toConfigKey(pos);
        boolean removed = POSITION_REPLACEMENTS.remove(key) != null;
        boolean removedCustom = POSITION_CUSTOM_TEXTURES.remove(key) != null;
        POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
        AIR_GHOST_POSITIONS.remove(key);
        if (removed || removedCustom) {
            POSITION_ROTATIONS.remove(key);
            markChanged(pos);
            return true;
        }
        return false;
    }

    public static boolean clearReplacementKey(long key) {
        boolean removed = POSITION_REPLACEMENTS.remove(key) != null;
        boolean removedCustom = POSITION_CUSTOM_TEXTURES.remove(key) != null;
        POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
        AIR_GHOST_POSITIONS.remove(key);
        if (removed || removedCustom) {
            POSITION_ROTATIONS.remove(key);
            markChanged(null);
            return true;
        }
        return false;
    }

    public static long configKeyFor(BlockPos pos) {
        return toConfigKey(pos);
    }

    public static boolean rotateReplacement(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        long key = toConfigKey(pos);
        if (!POSITION_REPLACEMENTS.containsKey(key) && !POSITION_CUSTOM_TEXTURES.containsKey(key)) {
            return false;
        }
        int next = (POSITION_ROTATIONS.getOrDefault(key, 0) + 1) % 4;
        POSITION_ROTATIONS.put(key, next);
        markChanged(pos);
        return true;
    }

    public static int replaceMappedSource(Block from, Block to) {
        if (from == null || to == null || from == to || POSITION_REPLACEMENTS.isEmpty()) {
            return 0;
        }

        LAST_REPLACE_UNDO.clear();
        lastReplaceWasCustom = false;
        int changed = 0;
        for (Map.Entry<Long, Block> entry : POSITION_REPLACEMENTS.entrySet()) {
            if (entry.getValue() == from) {
                LAST_REPLACE_UNDO.put(entry.getKey(), from);
                entry.setValue(to);
                changed++;
            }
        }

        if (changed > 0) {
            maybeClearRemapCache();
            save();
            refreshWorld(null);
        }
        return changed;
    }

    public static int replaceMappedSourceWithCustomTexture(Block from, String textureFile) {
        if (from == null || textureFile == null || textureFile.isBlank() || POSITION_REPLACEMENTS.isEmpty()) {
            return 0;
        }

        LAST_REPLACE_UNDO.clear();
        lastReplaceWasCustom = true;
        int changed = 0;
        java.util.ArrayList<Long> toRemove = new java.util.ArrayList<>();
        for (Map.Entry<Long, Block> entry : POSITION_REPLACEMENTS.entrySet()) {
            if (entry.getValue() == from) {
                long key = entry.getKey();
                LAST_REPLACE_UNDO.put(key, from);
                POSITION_CUSTOM_TEXTURES.put(key, textureFile);
                POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
                toRemove.add(key);
                changed++;
            }
        }

        if (changed > 0) {
            for (Long key : toRemove) {
                POSITION_REPLACEMENTS.remove(key);
            }
            maybeClearRemapCache();
            save();
            refreshWorld(null);
        } else {
            lastReplaceWasCustom = false;
        }
        return changed;
    }

    public static int undoLastReplace() {
        if (LAST_REPLACE_UNDO.isEmpty()) {
            return 0;
        }

        int restored = 0;
        for (Map.Entry<Long, Block> entry : LAST_REPLACE_UNDO.entrySet()) {
            long key = entry.getKey();
            if (POSITION_REPLACEMENTS.containsKey(key)) {
                POSITION_REPLACEMENTS.put(key, entry.getValue());
                restored++;
                continue;
            }
            if (lastReplaceWasCustom && POSITION_CUSTOM_TEXTURES.containsKey(key)) {
                POSITION_CUSTOM_TEXTURES.remove(key);
                POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
                POSITION_REPLACEMENTS.put(key, entry.getValue());
                restored++;
            }
        }
        LAST_REPLACE_UNDO.clear();
        lastReplaceWasCustom = false;

        if (restored > 0) {
            maybeClearRemapCache();
            save();
            refreshWorld(null);
        }
        return restored;
    }

    public static BlockState remapStateAt(BlockState original, BlockPos pos) {
        if (!enabled) {
            return original;
        }
        long key = toConfigKey(pos);
        if (POSITION_CUSTOM_TEXTURES.containsKey(key)) {
            Block source = POSITION_CUSTOM_TEXTURE_SOURCES.get(key);
            if (source != null && original.getBlock() != source) {
                return original;
            }
            if (original.isAir() && !AIR_GHOST_POSITIONS.contains(key)) {
                return original;
            }
            return net.minecraft.block.Blocks.AIR.getDefaultState();
        }
        Block mapped = POSITION_REPLACEMENTS.get(key);
        if (mapped == null) {
            return original;
        }
        if (original.isAir() && !AIR_GHOST_POSITIONS.contains(key)) {
            return original;
        }
        BlockState out = mapped.getDefaultState();
        if (!original.isAir()) {
            for (var property : original.getProperties()) {
                if (out.contains(property)) {
                    out = copyProperty(out, original, property);
                }
            }
        }

        int turns = POSITION_ROTATIONS.getOrDefault(key, 0);
        RemapKey remapKey = new RemapKey(original, mapped, turns);
        BlockState cached = REMAP_STATE_CACHE.get(remapKey);
        if (cached != null) {
            return cached;
        }

        if (turns != 0 && out.contains(Properties.HORIZONTAL_FACING)) {
            Direction facing = out.get(Properties.HORIZONTAL_FACING);
            for (int i = 0; i < turns; i++) {
                facing = facing.rotateYClockwise();
            }
            out = out.with(Properties.HORIZONTAL_FACING, facing);
        } else if (turns != 0 && out.contains(Properties.FACING)) {
            Direction facing = out.get(Properties.FACING);
            if (facing.getAxis().isHorizontal()) {
                for (int i = 0; i < turns; i++) {
                    facing = facing.rotateYClockwise();
                }
                out = out.with(Properties.FACING, facing);
            }
        } else if (turns != 0 && out.contains(Properties.HORIZONTAL_AXIS)) {
            net.minecraft.util.math.Direction.Axis axis = out.get(Properties.HORIZONTAL_AXIS);
            if (turns % 2 != 0) {
                axis = axis == net.minecraft.util.math.Direction.Axis.X
                    ? net.minecraft.util.math.Direction.Axis.Z
                    : net.minecraft.util.math.Direction.Axis.X;
                out = out.with(Properties.HORIZONTAL_AXIS, axis);
            }
        } else if (turns != 0 && out.contains(Properties.AXIS)) {
            net.minecraft.util.math.Direction.Axis axis = out.get(Properties.AXIS);
            if (turns % 2 != 0 && axis != net.minecraft.util.math.Direction.Axis.Y) {
                axis = axis == net.minecraft.util.math.Direction.Axis.X
                    ? net.minecraft.util.math.Direction.Axis.Z
                    : net.minecraft.util.math.Direction.Axis.X;
                out = out.with(Properties.AXIS, axis);
            }
        }
        REMAP_STATE_CACHE.put(remapKey, out);
        return out;
    }

    public static void toggleFavorite(Block block) {
        Identifier id = Registries.BLOCK.getId(block);
        if (!FAVORITES.add(id)) {
            FAVORITES.remove(id);
        }
        save();
    }

    public static boolean isFavorite(Block block) {
        return FAVORITES.contains(Registries.BLOCK.getId(block));
    }

    public static Set<Identifier> favoritesView() {
        return Collections.unmodifiableSet(FAVORITES);
    }

    public static void toggleCustomTextureFavorite(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        String key = fileName.trim();
        if (!CUSTOM_TEXTURE_FAVORITES.add(key)) {
            CUSTOM_TEXTURE_FAVORITES.remove(key);
        }
        save();
    }

    public static boolean isCustomTextureFavorite(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        return CUSTOM_TEXTURE_FAVORITES.contains(fileName.trim());
    }

    public static Set<String> customTextureFavoritesView() {
        return Collections.unmodifiableSet(CUSTOM_TEXTURE_FAVORITES);
    }

    public static Map<Block, Block> replacementsView() {
        return Collections.emptyMap();
    }

    public static Map<Long, Block> positionReplacementsView() {
        return Collections.unmodifiableMap(POSITION_REPLACEMENTS);
    }

    public static Map<Long, String> customTextureReplacementsView() {
        return Collections.unmodifiableMap(POSITION_CUSTOM_TEXTURES);
    }

    public static Map<Long, Block> customTextureSourcesView() {
        return Collections.unmodifiableMap(POSITION_CUSTOM_TEXTURE_SOURCES);
    }

    public static Set<Long> airGhostPositionsView() {
        return Collections.unmodifiableSet(AIR_GHOST_POSITIONS);
    }

    public static Block customTextureSourceAt(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        return POSITION_CUSTOM_TEXTURE_SOURCES.get(toConfigKey(pos));
    }

    public static boolean isAirGhostPosition(BlockPos pos) {
        return pos != null && AIR_GHOST_POSITIONS.contains(toConfigKey(pos));
    }

    public static int rotationAt(BlockPos pos) {
        if (pos == null) {
            return 0;
        }
        return POSITION_ROTATIONS.getOrDefault(toConfigKey(pos), 0);
    }

    public static Block mappedBlockAt(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        return POSITION_REPLACEMENTS.get(toConfigKey(pos));
    }

    public static void openScreen() {
        openScreenMode = ScreenMode.DEFAULT;
        openScreenInTicks = 2;
    }

    public static void openGuiSettings() {
        openScreenMode = ScreenMode.GUI_SETTINGS;
        openScreenInTicks = 2;
    }

    public static void clientTick(MinecraftClient client) {
        if (openScreenInTicks < 0) {
            return;
        }
        if (openScreenInTicks > 0) {
            openScreenInTicks--;
            return;
        }
        openScreenInTicks = -1;
        client.setScreen(new FmeScreen(openScreenMode == ScreenMode.GUI_SETTINGS));
    }

    public static void sendClientMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            MutableText prefix = Text.literal("[FME] ").formatted(Formatting.GREEN);
            client.player.sendMessage(prefix.append(Text.literal(message)), false);
        }
    }

    public static void load() {
        ensureConfigDir();
        Path lastConfig = readLastConfigPath();
        if (lastConfig != null && Files.exists(lastConfig)) {
            boolean loaded = loadFromPath(lastConfig, false, false);
            if (loaded) {
                currentSavePath = lastConfig;
                return;
            }
        }
        loadFromPath(SAVE_PATH, false, false);
        currentSavePath = SAVE_PATH;
    }

    public static boolean loadDefaultConfig() {
        ensureConfigDir();
        boolean loaded = loadFromPath(SAVE_PATH, true, true);
        if (loaded) {
            currentSavePath = SAVE_PATH;
            recordLastConfigPath(SAVE_PATH);
        }
        return loaded;
    }

    public static boolean loadConfig(String nameOrPath) {
        ensureConfigDir();
        Path path = resolveConfigPath(nameOrPath, false);
        if (path == null) {
            return false;
        }
        boolean loaded = loadFromPath(path, true, true);
        if (loaded) {
            currentSavePath = path;
            recordLastConfigPath(path);
        }
        return loaded;
    }

    public static boolean saveConfig(String name) {
        ensureConfigDir();
        Path path = resolveConfigPath(name, true);
        if (path == null) {
            return false;
        }
        boolean saved = saveToPath(path);
        if (saved) {
            currentSavePath = path;
            recordLastConfigPath(path);
        }
        return saved;
    }

    public static boolean saveCurrentConfig() {
        ensureConfigDir();
        return saveToPath(currentSavePath);
    }

    public static boolean addConfig(String name) {
        ensureConfigDir();
        Path path = resolveConfigPath(name, true);
        if (path == null) {
            return false;
        }
        boolean saved = saveToPath(path);
        if (saved) {
            currentSavePath = path;
            recordLastConfigPath(path);
        }
        return saved;
    }

    public static int stripAirReplacements() {
        int removed = 0;
        if (POSITION_REPLACEMENTS.isEmpty()) {
            return removed;
        }
        for (var it = POSITION_REPLACEMENTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Block> entry = it.next();
            if (entry.getValue() == net.minecraft.block.Blocks.AIR) {
                it.remove();
                POSITION_ROTATIONS.remove(entry.getKey());
                POSITION_CUSTOM_TEXTURES.remove(entry.getKey());
                POSITION_CUSTOM_TEXTURE_SOURCES.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            maybeClearRemapCache();
            save();
            refreshWorld(null);
        }
        return removed;
    }

    public static boolean convertFloorsConfig(String pathRaw, String nameRaw) {
        ensureConfigDir();
        Path source = resolveConfigPath(pathRaw, false);
        if (source == null || !Files.exists(source)) {
            return false;
        }
        String targetName = nameRaw;
        if (targetName == null || targetName.isBlank()) {
            String fileName = source.getFileName() != null ? source.getFileName().toString() : "floors";
            targetName = stripExtension(fileName);
        }
        Path target = resolveConfigPath(targetName, true);
        if (target == null) {
            return false;
        }

        clearState(true);
        try {
            String raw = Files.readString(source, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            boolean loaded = loadFloorsConfig(root);
            if (!loaded) {
                return false;
            }
            enabled = true;
            editMode = false;
            boolean saved = saveToPath(target);
            if (saved) {
                currentSavePath = target;
                recordLastConfigPath(target);
            }
            return saved;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean convertFloorsConfigSkippingAir(String pathRaw, String nameRaw) {
        ensureConfigDir();
        Path source = resolveConfigPath(pathRaw, false);
        if (source == null || !Files.exists(source)) {
            return false;
        }
        String targetName = nameRaw;
        if (targetName == null || targetName.isBlank()) {
            String fileName = source.getFileName() != null ? source.getFileName().toString() : "floors";
            targetName = stripExtension(fileName);
        }
        Path target = resolveConfigPath(targetName, true);
        if (target == null) {
            return false;
        }

        clearState(true);
        try {
            String raw = Files.readString(source, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            boolean loaded = loadFloorsConfig(root, true);
            if (!loaded) {
                return false;
            }
            enabled = true;
            editMode = false;
            boolean saved = saveToPath(target);
            if (saved) {
                currentSavePath = target;
                recordLastConfigPath(target);
            }
            return saved;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean loadFromPath(Path path, boolean resetState, boolean refreshWorld) {
        clearState(resetState);
        if (path == null || !Files.exists(path)) {
            return false;
        }

        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            boolean loaded = applyConfig(root);
            if (refreshWorld && loaded) {
                refreshWorld(null);
            }
            return loaded;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean applyConfig(JsonObject root) {
        boolean loadedLegacy = false;
        boolean loadedFloors = false;
        if (root.has("enabled")) {
            enabled = root.get("enabled").getAsBoolean();
        }
        if (root.has("editMode")) {
            editMode = root.get("editMode").getAsBoolean();
        }
        if (root.has("selectedSource")) {
            Identifier id = Identifier.tryParse(root.get("selectedSource").getAsString());
            if (id != null) {
                Block block = Registries.BLOCK.get(id);
                if (block != null) {
                    selectedSource = block;
                }
            }
        }
        if (root.has("selectedSourceType")) {
            String sourceTypeRaw = root.get("selectedSourceType").getAsString();
            if ("CUSTOM_TEXTURE".equalsIgnoreCase(sourceTypeRaw)) {
                selectedSourceType = SelectedSourceType.CUSTOM_TEXTURE;
            } else {
                selectedSourceType = SelectedSourceType.BLOCK;
            }
        }
        if (root.has("selectedCustomTexture")) {
            String customTextureRaw = root.get("selectedCustomTexture").getAsString();
            if (!customTextureRaw.isBlank()) {
                selectedCustomTexture = customTextureRaw;
            }
        }
        if (root.has("selectedRotation")) {
            selectedRotation = Math.floorMod(root.get("selectedRotation").getAsInt(), 4);
        }
        if (root.has("guiPanelColor")) {
            guiPanelColor = root.get("guiPanelColor").getAsInt();
        }
        if (root.has("guiBorderColor")) {
            guiBorderColor = root.get("guiBorderColor").getAsInt();
        }
        if (root.has("guiTextColor")) {
            guiTextColor = root.get("guiTextColor").getAsInt();
        }
        if (root.has("guiAccentTextColor")) {
            guiAccentTextColor = root.get("guiAccentTextColor").getAsInt();
        }
        if (root.has("selectionBoxColor")) {
            selectionBoxColor = root.get("selectionBoxColor").getAsInt();
        }
        guiScale = 1.5f;
        if (root.has("blockBrightness")) {
            try {
                float value = (float) root.get("blockBrightness").getAsDouble();
                guiBlockBrightness = MathHelper.clamp(value, 0.25f, 4.0f);
            } catch (Exception ignored) {
                guiBlockBrightness = 2.0f;
            }
        } else {
            guiBlockBrightness = 2.0f;
        }
        if (root.has("offsetX")) {
            offsetX = root.get("offsetX").getAsInt();
        }
        if (root.has("offsetY")) {
            offsetY = root.get("offsetY").getAsInt();
        }
        if (root.has("offsetZ")) {
            offsetZ = root.get("offsetZ").getAsInt();
        }
        if (root.has("favorites") && root.get("favorites").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("favorites");
            for (JsonElement e : arr) {
                Identifier id = Identifier.tryParse(e.getAsString());
                if (id != null) {
                    FAVORITES.add(id);
                }
            }
        }
        if (root.has("favoriteCustomTextures") && root.get("favoriteCustomTextures").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("favoriteCustomTextures");
            for (JsonElement e : arr) {
                String name = e.getAsString();
                if (name != null && !name.isBlank()) {
                    CUSTOM_TEXTURE_FAVORITES.add(name);
                }
            }
        }
        if (root.has("replacements")) {
            JsonElement replacementsEl = root.get("replacements");
            if (replacementsEl.isJsonArray()) {
                JsonArray arr = replacementsEl.getAsJsonArray();
                for (JsonElement e : arr) {
                    if (!e.isJsonObject()) {
                        continue;
                    }
                    JsonObject obj = e.getAsJsonObject();
                    Long pos = parsePosFromObject(obj);
                    if (pos == null) {
                        continue;
                    }
                    Block source = parseBlockFromObject(obj);
                    if (source == null) {
                        continue;
                    }
                    POSITION_REPLACEMENTS.put(pos, source);
                    POSITION_ROTATIONS.put(pos, parseRotationFromObject(obj));
                }
            } else if (replacementsEl.isJsonObject()) {
                JsonObject obj = replacementsEl.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    Long pos = parsePosFromString(entry.getKey());
                    if (pos == null) {
                        continue;
                    }
                    JsonElement value = entry.getValue();
                    Block source = null;
                    int rotation = 0;
                    if (value != null && value.isJsonPrimitive()) {
                        String raw = value.getAsString();
                        source = parseBlockFromString(raw);
                    } else if (value != null && value.isJsonObject()) {
                        JsonObject valueObj = value.getAsJsonObject();
                        source = parseBlockFromObject(valueObj);
                        rotation = parseRotationFromObject(valueObj);
                    }
                    if (source == null) {
                        continue;
                    }
                    POSITION_REPLACEMENTS.put(pos, source);
                    POSITION_ROTATIONS.put(pos, rotation);
                }
            }
        }
        if (root.has("customTextures")) {
            JsonElement customEl = root.get("customTextures");
            if (customEl.isJsonArray()) {
                JsonArray arr = customEl.getAsJsonArray();
                for (JsonElement e : arr) {
                    if (!e.isJsonObject()) {
                        continue;
                    }
                    JsonObject obj = e.getAsJsonObject();
                    Long pos = parsePosFromObject(obj);
                    if (pos == null) {
                        continue;
                    }
                    String file = parseCustomTextureFile(obj);
                    if (file == null) {
                        continue;
                    }
                    POSITION_CUSTOM_TEXTURES.put(pos, file);
                    Block source = parseBlockFromObject(obj);
                    if (source != null) {
                        POSITION_CUSTOM_TEXTURE_SOURCES.put(pos, source);
                    }
                    POSITION_ROTATIONS.put(pos, parseRotationFromObject(obj));
                }
            } else if (customEl.isJsonObject()) {
                JsonObject obj = customEl.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    Long pos = parsePosFromString(entry.getKey());
                    if (pos == null) {
                        continue;
                    }
                    JsonElement value = entry.getValue();
                    String file = null;
                    Block source = null;
                    int rotation = 0;
                    if (value != null && value.isJsonPrimitive()) {
                        file = value.getAsString();
                    } else if (value != null && value.isJsonObject()) {
                        JsonObject valueObj = value.getAsJsonObject();
                        file = parseCustomTextureFile(valueObj);
                        source = parseBlockFromObject(valueObj);
                        rotation = parseRotationFromObject(valueObj);
                    }
                    if (file == null || file.isBlank()) {
                        continue;
                    }
                    POSITION_CUSTOM_TEXTURES.put(pos, file);
                    if (source != null) {
                        POSITION_CUSTOM_TEXTURE_SOURCES.put(pos, source);
                    }
                    POSITION_ROTATIONS.put(pos, rotation);
                }
            }
        }
        if (root.has("airGhosts")) {
            JsonElement airEl = root.get("airGhosts");
            if (airEl.isJsonArray()) {
                JsonArray arr = airEl.getAsJsonArray();
                for (JsonElement e : arr) {
                    Long pos = parsePosElement(e);
                    if (pos != null) {
                        AIR_GHOST_POSITIONS.add(pos);
                    }
                }
            } else if (airEl.isJsonObject()) {
                JsonObject obj = airEl.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    Long pos = parsePosFromString(entry.getKey());
                    if (pos != null) {
                        AIR_GHOST_POSITIONS.add(pos);
                    }
                }
            }
        }
        if (root.has("airGhosts") && root.get("airGhosts").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("airGhosts");
            for (JsonElement e : arr) {
                AIR_GHOST_POSITIONS.add(e.getAsLong());
            }
        }

        if (POSITION_REPLACEMENTS.isEmpty()
            && POSITION_CUSTOM_TEXTURES.isEmpty()
            && root.has("minecraft:overworld")
            && root.get("minecraft:overworld").isJsonObject()) {
            JsonObject overworld = root.getAsJsonObject("minecraft:overworld");
            for (Map.Entry<String, JsonElement> entry : overworld.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive()) {
                    continue;
                }
                String raw = value.getAsString();
                if (raw == null || raw.isBlank() || raw.startsWith("!")) {
                    continue;
                }
                String idRaw = raw.split("\\[", 2)[0];
                Identifier id = Identifier.tryParse(idRaw);
                if (id == null) {
                    continue;
                }
                Block source = Registries.BLOCK.get(id);
                if (source == null) {
                    continue;
                }
                BlockPos pos = parseLegacyPos(key);
                if (pos == null) {
                    continue;
                }
                POSITION_REPLACEMENTS.put(pos.asLong(), source);
                POSITION_ROTATIONS.put(pos.asLong(), 0);
                loadedLegacy = true;
            }
            if (loadedLegacy) {
                enabled = true;
                editMode = false;
            }
        }
        if (POSITION_REPLACEMENTS.isEmpty() && POSITION_CUSTOM_TEXTURES.isEmpty()) {
            loadedFloors = loadFloorsConfig(root);
            if (loadedFloors) {
                enabled = true;
                editMode = false;
            }
        }
        int totalEntries = POSITION_REPLACEMENTS.size() + POSITION_CUSTOM_TEXTURES.size();
        skipRemapCacheClear = loadedLegacy || loadedFloors || totalEntries >= 5000;
        autoSaveEnabled = !skipRemapCacheClear;
        if (loadedLegacy) {
            sendClientMessage("Loaded legacy config. Auto-save disabled to avoid freezes. Use /fme config save <name>.");
        } else if (loadedFloors) {
            sendClientMessage("Loaded floors config. Auto-save disabled to avoid freezes. Use /fme config save <name>.");
        } else if (!autoSaveEnabled) {
            sendClientMessage("Large config loaded (" + totalEntries + " entries). Auto-save disabled to avoid freezes.");
        }
        return true;
    }

    private static void save() {
        if (!autoSaveEnabled) {
            return;
        }
        saveToPath(currentSavePath);
    }

    public static void beginBatch() {
        batching = true;
        batchChanged = false;
    }

    public static void endBatch() {
        if (!batching) {
            return;
        }
        batching = false;
        if (batchChanged) {
            maybeClearRemapCache();
            save();
            refreshWorld(null);
        }
    }

    private static void markChanged(BlockPos pos) {
        if (batching) {
            batchChanged = true;
            return;
        }
        maybeClearRemapCache();
        save();
        refreshWorld(pos);
    }

    private static boolean saveToPath(Path path) {
        if (path == null) {
            return false;
        }
        try {
            JsonObject root = buildConfigJson();
            Files.createDirectories(path.getParent());
            Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static JsonObject buildConfigJson() {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", enabled);
        root.addProperty("editMode", editMode);
        root.addProperty("selectedSource", Registries.BLOCK.getId(selectedSource).toString());
        root.addProperty("selectedSourceType", selectedSourceType.name());
        if (selectedCustomTexture != null) {
            root.addProperty("selectedCustomTexture", selectedCustomTexture);
        }
        root.addProperty("selectedRotation", selectedRotation);
        root.addProperty("guiPanelColor", guiPanelColor);
        root.addProperty("guiBorderColor", guiBorderColor);
        root.addProperty("guiTextColor", guiTextColor);
        root.addProperty("guiAccentTextColor", guiAccentTextColor);
        root.addProperty("selectionBoxColor", selectionBoxColor);
        root.addProperty("blockBrightness", guiBlockBrightness);
        root.addProperty("offsetX", offsetX);
        root.addProperty("offsetY", offsetY);
        root.addProperty("offsetZ", offsetZ);

        JsonArray favorites = new JsonArray();
        for (Identifier id : FAVORITES) {
            favorites.add(id.toString());
        }
        root.add("favorites", favorites);

        JsonArray customFavorites = new JsonArray();
        for (String name : CUSTOM_TEXTURE_FAVORITES) {
            customFavorites.add(name);
        }
        root.add("favoriteCustomTextures", customFavorites);

        JsonArray replacements = new JsonArray();
        for (Map.Entry<Long, Block> entry : POSITION_REPLACEMENTS.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("pos", entry.getKey());
            o.addProperty("source", Registries.BLOCK.getId(entry.getValue()).toString());
            o.addProperty("rotation", POSITION_ROTATIONS.getOrDefault(entry.getKey(), 0));
            replacements.add(o);
        }
        root.add("replacements", replacements);

        JsonArray customTextures = new JsonArray();
        for (Map.Entry<Long, String> entry : POSITION_CUSTOM_TEXTURES.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("pos", entry.getKey());
            o.addProperty("file", entry.getValue());
            Block source = POSITION_CUSTOM_TEXTURE_SOURCES.get(entry.getKey());
            if (source != null) {
                o.addProperty("source", Registries.BLOCK.getId(source).toString());
            }
            o.addProperty("rotation", POSITION_ROTATIONS.getOrDefault(entry.getKey(), 0));
            customTextures.add(o);
        }
        root.add("customTextures", customTextures);

        JsonArray airGhosts = new JsonArray();
        for (Long pos : AIR_GHOST_POSITIONS) {
            airGhosts.add(pos);
        }
        root.add("airGhosts", airGhosts);
        return root;
    }

    private static void clearState(boolean resetDefaults) {
        POSITION_REPLACEMENTS.clear();
        POSITION_CUSTOM_TEXTURES.clear();
        POSITION_CUSTOM_TEXTURE_SOURCES.clear();
        POSITION_ROTATIONS.clear();
        AIR_GHOST_POSITIONS.clear();
        FAVORITES.clear();
        CUSTOM_TEXTURE_FAVORITES.clear();
        clearRemapCache();
        if (resetDefaults) {
            enabled = false;
            editMode = false;
            selectedSource = net.minecraft.block.Blocks.STONE;
            selectedSourceType = SelectedSourceType.BLOCK;
            selectedCustomTexture = null;
            selectedRotation = 0;
            guiPanelColor = 0xE0161216;
            guiBorderColor = 0x003A2A2F;
            guiTextColor = 0xFFF5E9E1;
            guiAccentTextColor = 0xFFF28C3A;
            selectionBoxColor = 0x552E1C20;
            guiScale = 1.5f;
            guiBlockBrightness = 2.0f;
            autoSaveEnabled = true;
            skipRemapCacheClear = false;
            currentSavePath = SAVE_PATH;
            offsetX = 0;
            offsetY = 0;
            offsetZ = 0;
        }
    }

    private static long toConfigKey(BlockPos pos) {
        if (pos == null) {
            return 0L;
        }
        if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
            return pos.asLong();
        }
        return BlockPos.asLong(pos.getX() - offsetX, pos.getY() - offsetY, pos.getZ() - offsetZ);
    }

    private static void maybeClearRemapCache() {
        if (skipRemapCacheClear) {
            return;
        }
        clearRemapCache();
    }

    private static Path resolveConfigPath(String nameOrPath, boolean forSave) {
        if (nameOrPath == null || nameOrPath.isBlank()) {
            return null;
        }
        String trimmed = nameOrPath.trim();
        Path path;
        boolean hasSeparator = trimmed.contains("/") || trimmed.contains("\\");
        boolean looksAbsolute = trimmed.contains(":") || trimmed.startsWith("/") || trimmed.startsWith("\\");
        if (looksAbsolute || hasSeparator) {
            path = Path.of(trimmed);
            if (!path.isAbsolute()) {
                path = CONFIG_DIR.resolve(path);
            }
        } else {
            String name = sanitizeConfigName(trimmed);
            if (name.isBlank()) {
                return null;
            }
            int dot = name.lastIndexOf('.');
            if (dot <= 0 || dot == name.length() - 1) {
                name = name + ".json";
            }
            path = CONFIG_DIR.resolve(name);
        }
        if (forSave) {
            ensureConfigDir();
        }
        return path.normalize();
    }

    private static void recordLastConfigPath(Path path) {
        if (path == null) {
            return;
        }
        try {
            Path normalized = path.toAbsolutePath().normalize();
            JsonObject root = new JsonObject();
            root.addProperty("lastConfig", normalized.toString());
            Files.createDirectories(STATE_PATH.getParent());
            Files.writeString(STATE_PATH, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static Path readLastConfigPath() {
        if (!Files.exists(STATE_PATH)) {
            return null;
        }
        try {
            String raw = Files.readString(STATE_PATH, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (!root.has("lastConfig")) {
                return null;
            }
            String value = root.get("lastConfig").getAsString();
            if (value == null || value.isBlank()) {
                return null;
            }
            return Path.of(value).normalize();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void ensureConfigDir() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException ignored) {
        }
    }

    private static String sanitizeConfigName(String raw) {
        return raw.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return name;
        }
        return name.substring(0, dot);
    }

    private static BlockPos parseLegacyPos(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            return new BlockPos(x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long parsePosFromString(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
        }
        BlockPos pos = parseLegacyPos(trimmed);
        return pos != null ? pos.asLong() : null;
    }

    private static Long parsePosElement(JsonElement element) {
        if (element == null) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            try {
                return element.getAsLong();
            } catch (Exception ignored) {
            }
            return parsePosFromString(element.getAsString());
        }
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            if (arr.size() == 3) {
                Integer x = parseIntElement(arr.get(0));
                Integer y = parseIntElement(arr.get(1));
                Integer z = parseIntElement(arr.get(2));
                if (x != null && y != null && z != null) {
                    return new BlockPos(x, y, z).asLong();
                }
            }
            return null;
        }
        if (element.isJsonObject()) {
            return parsePosFromObject(element.getAsJsonObject());
        }
        return null;
    }

    private static Long parsePosFromObject(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        if (obj.has("pos")) {
            return parsePosElement(obj.get("pos"));
        }
        if (obj.has("x") && obj.has("y") && obj.has("z")) {
            Integer x = parseIntElement(obj.get("x"));
            Integer y = parseIntElement(obj.get("y"));
            Integer z = parseIntElement(obj.get("z"));
            if (x != null && y != null && z != null) {
                return new BlockPos(x, y, z).asLong();
            }
        }
        return null;
    }

    private static Integer parseIntElement(JsonElement element) {
        if (element == null) {
            return null;
        }
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int parseRotationFromObject(JsonObject obj) {
        if (obj == null) {
            return 0;
        }
        if (obj.has("rotation")) {
            return parseRotationElement(obj.get("rotation"));
        }
        if (obj.has("rot")) {
            return parseRotationElement(obj.get("rot"));
        }
        if (obj.has("turns")) {
            return parseRotationElement(obj.get("turns"));
        }
        return 0;
    }

    private static int parseRotationElement(JsonElement element) {
        if (element == null) {
            return 0;
        }
        try {
            return Math.floorMod(element.getAsInt(), 4);
        } catch (Exception ignored) {
            try {
                return Math.floorMod(Integer.parseInt(element.getAsString()), 4);
            } catch (Exception ignored2) {
                return 0;
            }
        }
    }

    private static Block parseBlockFromObject(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        if (obj.has("source")) {
            return parseBlockFromString(obj.get("source").getAsString());
        }
        if (obj.has("block")) {
            return parseBlockFromString(obj.get("block").getAsString());
        }
        if (obj.has("id")) {
            return parseBlockFromString(obj.get("id").getAsString());
        }
        if (obj.has("name")) {
            return parseBlockFromString(obj.get("name").getAsString());
        }
        return null;
    }

    private static Block parseBlockFromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        String idRaw = trimmed.split("\\[", 2)[0];
        idRaw = remapLegacyBlockId(idRaw, trimmed);
        Identifier id = Identifier.tryParse(idRaw);
        if (id == null) {
            return null;
        }
        Block block = Registries.BLOCK.get(id);
        return block == null ? null : block;
    }

    private static String parseCustomTextureFile(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        if (obj.has("file")) {
            return obj.get("file").getAsString();
        }
        if (obj.has("texture")) {
            return obj.get("texture").getAsString();
        }
        if (obj.has("name")) {
            return obj.get("name").getAsString();
        }
        return null;
    }

    private static boolean loadFloorsConfig(JsonObject root) {
        return loadFloorsConfig(root, false);
    }

    private static boolean loadFloorsConfig(JsonObject root, boolean skipAir) {
        boolean loadedAny = false;
        for (Map.Entry<String, JsonElement> floorEntry : root.entrySet()) {
            if (!floorEntry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject floor = floorEntry.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> blockEntry : floor.entrySet()) {
                String stateRaw = blockEntry.getKey();
                if (!blockEntry.getValue().isJsonArray()) {
                    continue;
                }
                String idRaw = stateRaw.split("\\[", 2)[0];
                idRaw = remapLegacyBlockId(idRaw, stateRaw);
                if (skipAir && "minecraft:air".equals(idRaw)) {
                    continue;
                }
                Identifier id = Identifier.tryParse(idRaw);
                if (id == null) {
                    continue;
                }
                Block source = Registries.BLOCK.get(id);
                if (source == null) {
                    continue;
                }
                int rotation = rotationFromState(stateRaw);
                JsonArray positions = blockEntry.getValue().getAsJsonArray();
                for (JsonElement posEl : positions) {
                    if (!posEl.isJsonPrimitive()) {
                        continue;
                    }
                    BlockPos pos = parseLegacyPos(posEl.getAsString());
                    if (pos == null) {
                        continue;
                    }
        long key = toConfigKey(pos);
                    POSITION_REPLACEMENTS.put(key, source);
                    POSITION_ROTATIONS.put(key, rotation);
                    if (source.getDefaultState().isAir()) {
                        AIR_GHOST_POSITIONS.add(key);
                    }
                    loadedAny = true;
                }
            }
        }
        return loadedAny;
    }

    private static String remapLegacyBlockId(String idRaw, String stateRaw) {
        if (idRaw == null) {
            return null;
        }
        return switch (idRaw) {
            case "minecraft:stone" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "granite" -> "minecraft:granite";
                        case "granite_smooth" -> "minecraft:polished_granite";
                        case "diorite" -> "minecraft:diorite";
                        case "diorite_smooth" -> "minecraft:polished_diorite";
                        case "andesite" -> "minecraft:andesite";
                        case "andesite_smooth" -> "minecraft:polished_andesite";
                        default -> "minecraft:stone";
                    };
                }
                yield "minecraft:stone";
            }
            case "minecraft:grass" -> "minecraft:grass_block";
            case "minecraft:dirt" -> {
                if (stateRaw != null && stateRaw.contains("variant=podzol")) {
                    yield "minecraft:podzol";
                }
                if (stateRaw != null && stateRaw.contains("variant=coarse_dirt")) {
                    yield "minecraft:coarse_dirt";
                }
                yield "minecraft:dirt";
            }
            case "minecraft:log" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield "minecraft:" + variant + "_log";
                }
                yield "minecraft:oak_log";
            }
            case "minecraft:log2" -> "minecraft:dark_oak_log";
            case "minecraft:leaves" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield "minecraft:" + variant + "_leaves";
                }
                yield "minecraft:oak_leaves";
            }
            case "minecraft:leaves2" -> "minecraft:dark_oak_leaves";
            case "minecraft:tallgrass" -> {
                String type = parseStateProperty(stateRaw, "type");
                if ("fern".equals(type)) {
                    yield "minecraft:fern";
                }
                if ("dead_bush".equals(type)) {
                    yield "minecraft:dead_bush";
                }
                yield "minecraft:grass";
            }
            case "minecraft:red_flower" -> {
                String type = parseStateProperty(stateRaw, "type");
                if (type != null) {
                    yield switch (type) {
                        case "poppy", "rose" -> "minecraft:poppy";
                        case "blue_orchid" -> "minecraft:blue_orchid";
                        case "allium" -> "minecraft:allium";
                        case "houstonia", "azure_bluet" -> "minecraft:azure_bluet";
                        case "red_tulip" -> "minecraft:red_tulip";
                        case "orange_tulip" -> "minecraft:orange_tulip";
                        case "white_tulip" -> "minecraft:white_tulip";
                        case "pink_tulip" -> "minecraft:pink_tulip";
                        case "oxeye_daisy" -> "minecraft:oxeye_daisy";
                        default -> "minecraft:poppy";
                    };
                }
                yield "minecraft:poppy";
            }
            case "minecraft:yellow_flower" -> "minecraft:dandelion";
            case "minecraft:stonebrick" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "mossy" -> "minecraft:mossy_stone_bricks";
                        case "cracked" -> "minecraft:cracked_stone_bricks";
                        case "chiseled" -> "minecraft:chiseled_stone_bricks";
                        case "stonebrick" -> "minecraft:stone_bricks";
                        default -> "minecraft:stone_bricks";
                    };
                }
                yield "minecraft:stone_bricks";
            }
            case "minecraft:sandstone" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "chiseled" -> "minecraft:chiseled_sandstone";
                        case "smooth" -> "minecraft:smooth_sandstone";
                        default -> "minecraft:sandstone";
                    };
                }
                yield "minecraft:sandstone";
            }
            case "minecraft:red_sandstone" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "chiseled" -> "minecraft:chiseled_red_sandstone";
                        case "smooth" -> "minecraft:smooth_red_sandstone";
                        default -> "minecraft:red_sandstone";
                    };
                }
                yield "minecraft:red_sandstone";
            }
            case "minecraft:quartz_block" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "chiseled" -> "minecraft:chiseled_quartz_block";
                        case "lines" -> "minecraft:quartz_pillar";
                        default -> "minecraft:quartz_block";
                    };
                }
                yield "minecraft:quartz_block";
            }
            case "minecraft:stone_slab" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "sandstone" -> "minecraft:sandstone_slab";
                        case "wood_old" -> "minecraft:oak_slab";
                        case "cobblestone" -> "minecraft:cobblestone_slab";
                        case "brick" -> "minecraft:brick_slab";
                        case "stone_brick" -> "minecraft:stone_brick_slab";
                        case "nether_brick" -> "minecraft:nether_brick_slab";
                        case "quartz" -> "minecraft:quartz_slab";
                        default -> "minecraft:stone_slab";
                    };
                }
                yield "minecraft:stone_slab";
            }
            case "minecraft:double_stone_slab" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "sandstone" -> "minecraft:sandstone";
                        case "wood_old" -> "minecraft:oak_planks";
                        case "cobblestone" -> "minecraft:cobblestone";
                        case "brick" -> "minecraft:bricks";
                        case "stone_brick" -> "minecraft:stone_bricks";
                        case "nether_brick" -> "minecraft:nether_bricks";
                        case "quartz" -> "minecraft:quartz_block";
                        default -> "minecraft:smooth_stone";
                    };
                }
                yield "minecraft:smooth_stone";
            }
            case "minecraft:double_wooden_slab" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "spruce" -> "minecraft:spruce_planks";
                        case "birch" -> "minecraft:birch_planks";
                        case "jungle" -> "minecraft:jungle_planks";
                        case "acacia" -> "minecraft:acacia_planks";
                        case "dark_oak" -> "minecraft:dark_oak_planks";
                        default -> "minecraft:oak_planks";
                    };
                }
                yield "minecraft:oak_planks";
            }
            case "minecraft:wooden_slab" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "spruce" -> "minecraft:spruce_slab";
                        case "birch" -> "minecraft:birch_slab";
                        case "jungle" -> "minecraft:jungle_slab";
                        case "acacia" -> "minecraft:acacia_slab";
                        case "dark_oak" -> "minecraft:dark_oak_slab";
                        default -> "minecraft:oak_slab";
                    };
                }
                yield "minecraft:oak_slab";
            }
            case "minecraft:planks" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield "minecraft:" + variant + "_planks";
                }
                yield "minecraft:oak_planks";
            }
            case "minecraft:prismarine" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "bricks" -> "minecraft:prismarine_bricks";
                        case "dark" -> "minecraft:dark_prismarine";
                        default -> "minecraft:prismarine";
                    };
                }
                yield "minecraft:prismarine";
            }
            case "minecraft:monster_egg" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "cobblestone" -> "minecraft:infested_cobblestone";
                        case "stonebrick" -> "minecraft:infested_stone_bricks";
                        case "mossy_stonebrick" -> "minecraft:infested_mossy_stone_bricks";
                        case "cracked_stonebrick" -> "minecraft:infested_cracked_stone_bricks";
                        case "chiseled_stonebrick" -> "minecraft:infested_chiseled_stone_bricks";
                        default -> "minecraft:infested_stone";
                    };
                }
                yield "minecraft:infested_stone";
            }
            case "minecraft:cobblestone_wall" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if ("mossy".equals(variant)) {
                    yield "minecraft:mossy_cobblestone_wall";
                }
                yield "minecraft:cobblestone_wall";
            }
            case "minecraft:stained_glass" -> mapLegacyColoredBlock(stateRaw, "stained_glass", "minecraft:", "_stained_glass");
            case "minecraft:stained_glass_pane" -> mapLegacyColoredBlock(stateRaw, "stained_glass_pane", "minecraft:", "_stained_glass_pane");
            case "minecraft:stained_hardened_clay" -> mapLegacyColoredBlock(stateRaw, "stained_hardened_clay", "minecraft:", "_terracotta");
            case "minecraft:wool" -> mapLegacyColoredBlock(stateRaw, "wool", "minecraft:", "_wool");
            case "minecraft:carpet" -> mapLegacyColoredBlock(stateRaw, "carpet", "minecraft:", "_carpet");
            default -> idRaw;
        };
    }

    private static String mapLegacyColoredBlock(String stateRaw, String fallbackName, String prefix, String suffix) {
        String color = parseStateProperty(stateRaw, "color");
        if (color == null) {
            color = parseStateProperty(stateRaw, "variant");
        }
        String mapped = mapLegacyColor(color);
        if (mapped == null) {
            if ("stained_hardened_clay".equals(fallbackName)) {
                return "minecraft:terracotta";
            }
            return prefix + "white" + suffix;
        }
        return prefix + mapped + suffix;
    }

    private static String mapLegacyColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw) {
            case "silver" -> "light_gray";
            case "light_blue" -> "light_blue";
            case "light_gray" -> "light_gray";
            default -> raw;
        };
    }

    private static String parseStateProperty(String stateRaw, String key) {
        if (stateRaw == null || key == null) {
            return null;
        }
        int bracket = stateRaw.indexOf('[');
        int end = stateRaw.lastIndexOf(']');
        if (bracket == -1 || end <= bracket) {
            return null;
        }
        String props = stateRaw.substring(bracket + 1, end);
        String[] parts = props.split(",");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String k = kv[0].trim().toLowerCase(Locale.ROOT);
            if (!k.equals(key.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String v = kv[1].trim().toLowerCase(Locale.ROOT);
            return v.isBlank() ? null : v;
        }
        return null;
    }

    private static int rotationFromState(String stateRaw) {
        if (stateRaw == null) {
            return 0;
        }
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
            String key = kv[0].trim().toLowerCase(Locale.ROOT);
            String value = kv[1].trim().toLowerCase(Locale.ROOT);
            if (!"facing".equals(key)) {
                continue;
            }
            return switch (value) {
                case "east" -> 1;
                case "south" -> 2;
                case "west" -> 3;
                default -> 0;
            };
        }
        return 0;
    }

    private static void refreshWorld(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.worldRenderer == null) {
            return;
        }

        if (pos != null && client.world != null) {
            BlockState state = client.world.getBlockState(pos);
            client.worldRenderer.scheduleBlockRerenderIfNeeded(pos, state, state);
            client.worldRenderer.scheduleBlockRenders(
                pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1
            );
            return;
        }
        client.worldRenderer.scheduleTerrainUpdate();
    }


    private static void clearRemapCache() {
        REMAP_STATE_CACHE.clear();
    }

    private static <T extends Comparable<T>> BlockState copyProperty(
        BlockState into,
        BlockState from,
        net.minecraft.state.property.Property<T> property
    ) {
        return into.with(property, from.get(property));
    }

    private record RemapKey(BlockState original, Block mapped, int turns) {
    }

    public enum SelectedSourceType {
        BLOCK,
        CUSTOM_TEXTURE
    }

    public enum ScreenMode {
        DEFAULT,
        GUI_SETTINGS
    }

    public static int getGuiPanelColor() {
        return guiPanelColor;
    }

    public static int getGuiBorderColor() {
        return guiBorderColor;
    }

    public static int getGuiTextColor() {
        return guiTextColor;
    }

    public static int getGuiAccentTextColor() {
        return guiAccentTextColor;
    }

    public static int getSelectionBoxColor() {
        return selectionBoxColor;
    }

    public static void setGuiPanelColor(int argb) {
        guiPanelColor = argb;
        save();
    }

    public static void setGuiBorderColor(int argb) {
        guiBorderColor = argb;
        save();
    }

    public static void setGuiTextColor(int argb) {
        guiTextColor = argb;
        save();
    }

    public static void setGuiAccentTextColor(int argb) {
        guiAccentTextColor = argb;
        save();
    }

    public static void setSelectionBoxColor(int argb) {
        selectionBoxColor = argb;
        save();
    }

    public static void resetGuiColors() {
        guiPanelColor = 0xE0161216;
        guiBorderColor = 0x003A2A2F;
        guiTextColor = 0xFFF5E9E1;
        guiAccentTextColor = 0xFFF28C3A;
        selectionBoxColor = 0x552E1C20;
        save();
    }

    public static void saveGuiColors() {
        save();
    }

    public static float getGuiScale() {
        return 1.5f;
    }

    public static void setGuiScale(float value) {
        guiScale = 1.5f;
    }

    public static float getBlockBrightness() {
        return guiBlockBrightness;
    }

    public static void setBlockBrightness(float value) {
        guiBlockBrightness = MathHelper.clamp(value, 0.25f, 4.0f);
        save();
    }
}
