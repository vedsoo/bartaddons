package hat.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import hat.fabric.mixin.BlockRenderManagerAccessor;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

public final class FmeGhostBlockRenderer {
    private static final double MAX_DISTANCE_SQ = 72.0 * 72.0;
    private static final int CHUNK_RADIUS = 5;
    private static final int MAX_RENDER_PER_FRAME = 1400;
    private static final int MAX_CANDIDATES = 4000;
    private static final ThreadLocal<BlockPos.Mutable> MUTABLE_POS =
        ThreadLocal.withInitial(BlockPos.Mutable::new);
    private static final ThreadLocal<Random> RENDER_RANDOM =
        ThreadLocal.withInitial(Random::create);
    private static final ThreadLocal<ArrayList<BlockModelPart>> RENDER_PARTS =
        ThreadLocal.withInitial(ArrayList::new);
    private static final ArrayList<Long> VISIBLE_POSITIONS = new ArrayList<>();
    private static final ArrayList<DistanceEntry> DISTANCE_BUFFER = new ArrayList<>();
    private static final Map<Long, ArrayList<Long>> POSITIONS_BY_CHUNK = new HashMap<>();
    private static long indexedVersion = Long.MIN_VALUE;
    private static int lastCamChunkX = Integer.MIN_VALUE;
    private static int lastCamChunkY = Integer.MIN_VALUE;
    private static int lastCamChunkZ = Integer.MIN_VALUE;

    private FmeGhostBlockRenderer() {
    }

    public static void render(WorldRenderContext context) {
        if (!FmeManager.isEnabled()) {
            return;
        }
        MatrixStack matrices = context.matrices();
        if (matrices == null || context.consumers() == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        BlockRenderManager renderManager = client.getBlockRenderManager();
        BlockRenderManagerAccessor accessor = (BlockRenderManagerAccessor) renderManager;
        BlockModels models = accessor.hat$getModels();
        BlockModelRenderer modelRenderer = accessor.hat$getBlockModelRenderer();
        Vec3d camPos = client.gameRenderer.getCamera().getPos();
        Set<Long> ghostPositions = FmeManager.airGhostPositionsView();
        if (ghostPositions.isEmpty()) {
            return;
        }
        updateChunkIndex(ghostPositions);
        rebuildVisibleCacheIfNeeded(camPos);

        if (VISIBLE_POSITIONS.isEmpty()) {
            return;
        }
        BlockPos.Mutable pos = MUTABLE_POS.get();
        int toRender = Math.min(MAX_RENDER_PER_FRAME, VISIBLE_POSITIONS.size());
        for (int i = 0; i < toRender; i++) {
            Long key = VISIBLE_POSITIONS.get(i);
            pos.set(key);
            BlockState worldState = client.world.getBlockState(pos);
            if (!worldState.isAir()) {
                continue;
            }
            BlockState mapped = FmeManager.remapStateAt(Blocks.AIR.getDefaultState(), pos);
            if (mapped.isAir()) {
                continue;
            }

            matrices.push();
            matrices.translate(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);
            BlockStateModel model = models.getModel(mapped);
            ArrayList<BlockModelPart> parts = RENDER_PARTS.get();
            parts.clear();
            Random random = RENDER_RANDOM.get();
            random.setSeed(mapped.getRenderingSeed(pos));
            model.addParts(random, parts);
            RenderLayer layer = RenderLayers.getMovingBlockLayer(mapped);
            VertexConsumer vc = context.consumers().getBuffer(layer);
            modelRenderer.render(client.world, parts, mapped, pos, matrices, vc, true, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
    }

    private static void updateChunkIndex(Set<Long> ghostPositions) {
        long version = FmeManager.renderDataVersion();
        if (indexedVersion == version) {
            return;
        }

        POSITIONS_BY_CHUNK.clear();
        BlockPos.Mutable pos = MUTABLE_POS.get();
        for (Long key : ghostPositions) {
            pos.set(key);
            long chunkKey = chunkKey(
                chunkCoord(pos.getX()),
                chunkCoord(pos.getY()),
                chunkCoord(pos.getZ())
            );
            POSITIONS_BY_CHUNK.computeIfAbsent(chunkKey, ignored -> new ArrayList<>()).add(key);
        }
        indexedVersion = version;
        lastCamChunkX = Integer.MIN_VALUE;
        lastCamChunkY = Integer.MIN_VALUE;
        lastCamChunkZ = Integer.MIN_VALUE;
    }

    private static void rebuildVisibleCacheIfNeeded(Vec3d camPos) {
        int camChunkX = chunkCoord((int) Math.floor(camPos.x));
        int camChunkY = chunkCoord((int) Math.floor(camPos.y));
        int camChunkZ = chunkCoord((int) Math.floor(camPos.z));
        if (camChunkX == lastCamChunkX && camChunkY == lastCamChunkY && camChunkZ == lastCamChunkZ) {
            return;
        }

        VISIBLE_POSITIONS.clear();
        DISTANCE_BUFFER.clear();
        BlockPos.Mutable pos = MUTABLE_POS.get();
        for (int x = camChunkX - CHUNK_RADIUS; x <= camChunkX + CHUNK_RADIUS; x++) {
            for (int y = camChunkY - 1; y <= camChunkY + 1; y++) {
                for (int z = camChunkZ - CHUNK_RADIUS; z <= camChunkZ + CHUNK_RADIUS; z++) {
                    List<Long> positions = POSITIONS_BY_CHUNK.get(chunkKey(x, y, z));
                    if (positions == null) {
                        continue;
                    }
                    for (Long key : positions) {
                        pos.set(key);
                        double dx = pos.getX() + 0.5 - camPos.x;
                        double dy = pos.getY() + 0.5 - camPos.y;
                        double dz = pos.getZ() + 0.5 - camPos.z;
                        double distanceSq = dx * dx + dy * dy + dz * dz;
                        if (distanceSq > MAX_DISTANCE_SQ) {
                            continue;
                        }
                        DISTANCE_BUFFER.add(new DistanceEntry(key, distanceSq));
                    }
                }
            }
        }
        DISTANCE_BUFFER.sort((a, b) -> Double.compare(a.distanceSq, b.distanceSq));
        int limit = Math.min(MAX_CANDIDATES, DISTANCE_BUFFER.size());
        for (int i = 0; i < limit; i++) {
            VISIBLE_POSITIONS.add(DISTANCE_BUFFER.get(i).key);
        }
        lastCamChunkX = camChunkX;
        lastCamChunkY = camChunkY;
        lastCamChunkZ = camChunkZ;
    }

    private static long chunkKey(int chunkX, int chunkY, int chunkZ) {
        return (((long) chunkX) & 0x3FFFFFL) << 42
            | ((((long) chunkY) & 0xFFFFFL) << 22)
            | (((long) chunkZ) & 0x3FFFFFL);
    }

    private static int chunkCoord(int blockCoord) {
        return blockCoord >> 4;
    }

    private record DistanceEntry(long key, double distanceSq) {
    }
}
