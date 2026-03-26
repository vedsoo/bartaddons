package hat.fabric;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class DungeonDoorGlassManager {
    private static final int START_X = -185;
    private static final int START_Z = -185;
    private static final int STEP = 16;
    private static final int DOOR_Y = 69;

    private static final BlockState GLASS = Blocks.GLASS.getDefaultState();

    private static final Map<Long, BlockState> ORIGINALS = new HashMap<>();
    private static final BlockPos.Mutable MUTABLE = new BlockPos.Mutable();
    private static boolean active;

    private DungeonDoorGlassManager() {
    }

    public static void onClientTick(MinecraftClient client) {
        if (!active) {
            return;
        }
        if (client == null || client.player == null || client.world == null || !isDungeonMapPresent(client)) {
            clearAll(client != null ? client.world : null);
            active = false;
        }
    }

    public static void toggle(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        if (active) {
            clearAll(client.world);
            active = false;
            client.player.sendMessage(Text.literal("[DoorGlass] Removed."), false);
            return;
        }

        if (!isDungeonMapPresent(client)) {
            client.player.sendMessage(Text.literal("[DoorGlass] Hold the dungeon Magical Map in slot 9 first."), false);
            return;
        }

        placeAll(client.world);
        active = true;
        client.player.sendMessage(Text.literal("[DoorGlass] Placed."), false);
    }

    private static boolean isDungeonMapPresent(MinecraftClient client) {
        ItemStack mapStack = client.player.getInventory().getStack(8);
        if (mapStack == null || mapStack.isEmpty()) {
            return false;
        }
        String name = mapStack.getName().getString();
        return name.contains("Magical Map") && !name.contains("Score Summary");
    }

    private static void placeAll(World world) {
        clearAll(world);
        Set<Long> wanted = computeWantedPositions(world);
        for (Long posLong : wanted) {
            placeAt(world, posLong);
        }
    }

    private static Set<Long> computeWantedPositions(World world) {
        Set<Long> wanted = new HashSet<>();

        for (int column = 0; column <= 10; column++) {
            for (int row = 0; row <= 10; row++) {
                boolean rowEven = (row & 1) == 0;
                boolean columnEven = (column & 1) == 0;
                if (rowEven == columnEven) {
                    continue;
                }

                int x = START_X + (column * STEP);
                int z = START_Z + (row * STEP);

                if (!isDoorAt(world, x, z)) {
                    continue;
                }

                int dx = rowEven ? 0 : 1;
                int dz = rowEven ? 1 : 0;

                // Vertical columns on both sides of each door.
                for (int y = 69; y <= 71; y++) {
                    wanted.add(BlockPos.asLong(x + dx, y, z + dz));
                    wanted.add(BlockPos.asLong(x - dx, y, z - dz));
                }
            }
        }

        return wanted;
    }

    private static boolean isDoorAt(World world, int x, int z) {
        if (!world.isChunkLoaded(new BlockPos(x, DOOR_Y, z))) {
            return false;
        }

        int roofHeight = findRoofHeight(world, x, z);
        if (roofHeight != 73 && roofHeight != 74 && roofHeight != 81 && roofHeight != 82) {
            return false;
        }

        BlockState doorState = world.getBlockState(new BlockPos(x, DOOR_Y, z));
        if (doorState.isOf(Blocks.INFESTED_CHISELED_STONE_BRICKS)) {
            return false; // Skip entrance door
        }
        return true; // Normal, wither, blood
    }

    private static int findRoofHeight(World world, int x, int z) {
        for (int y = 140; y >= 60; y--) {
            MUTABLE.set(x, y, z);
            if (!world.getBlockState(MUTABLE).isAir()) {
                return y;
            }
        }
        return -1;
    }

    private static void placeAt(World world, long posLong) {
        BlockPos pos = BlockPos.fromLong(posLong);
        BlockState current = world.getBlockState(pos);
        if (!current.isAir() && !current.isOf(Blocks.GLASS)) {
            return;
        }
        ORIGINALS.put(posLong, current);
        world.setBlockState(pos, GLASS, 19);
    }

    private static void clearAll(World world) {
        if (ORIGINALS.isEmpty()) {
            return;
        }
        if (world == null) {
            ORIGINALS.clear();
            return;
        }

        for (Map.Entry<Long, BlockState> entry : ORIGINALS.entrySet()) {
            world.setBlockState(BlockPos.fromLong(entry.getKey()), entry.getValue(), 19);
        }
        ORIGINALS.clear();
    }
}
