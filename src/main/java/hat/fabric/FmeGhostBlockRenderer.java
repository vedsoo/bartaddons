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

import java.util.Set;
import java.util.ArrayList;

public final class FmeGhostBlockRenderer {
    private static final double MAX_DISTANCE_SQ = 128.0 * 128.0;
    private static final long REBUILD_TICKS = 20;
    private static final double REBUILD_MOVE_SQ = 4.0;
    private static final int MAX_RENDER_BLOCKS = 20000;
    private static final ThreadLocal<BlockPos.Mutable> MUTABLE_POS =
        ThreadLocal.withInitial(BlockPos.Mutable::new);
    private static final ThreadLocal<Random> RENDER_RANDOM =
        ThreadLocal.withInitial(Random::create);
    private static final ThreadLocal<ArrayList<BlockModelPart>> RENDER_PARTS =
        ThreadLocal.withInitial(ArrayList::new);
    private static final java.util.ArrayList<Long> CACHED_POSITIONS = new java.util.ArrayList<>();
    private static int lastCamBlockX = Integer.MIN_VALUE;
    private static int lastCamBlockY = Integer.MIN_VALUE;
    private static int lastCamBlockZ = Integer.MIN_VALUE;
    private static long lastRebuildTick = -1;

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
        int camX = (int) Math.floor(camPos.x);
        int camY = (int) Math.floor(camPos.y);
        int camZ = (int) Math.floor(camPos.z);
        long now = client.world.getTime();
        if (shouldRebuildCache(camX, camY, camZ, now)) {
            rebuildCache(FmeManager.airGhostPositionsView(), camPos);
            lastCamBlockX = camX;
            lastCamBlockY = camY;
            lastCamBlockZ = camZ;
            lastRebuildTick = now;
        }
        if (CACHED_POSITIONS.isEmpty()) {
            return;
        }

        BlockPos.Mutable pos = MUTABLE_POS.get();
        for (Long key : CACHED_POSITIONS) {
            pos.set(key);
            double dx = pos.getX() + 0.5 - camPos.x;
            double dy = pos.getY() + 0.5 - camPos.y;
            double dz = pos.getZ() + 0.5 - camPos.z;
            if ((dx * dx + dy * dy + dz * dz) > MAX_DISTANCE_SQ) {
                continue;
            }
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

    private static boolean shouldRebuildCache(int camX, int camY, int camZ, long now) {
        if (lastRebuildTick < 0) {
            return true;
        }
        if (now - lastRebuildTick >= REBUILD_TICKS) {
            return true;
        }
        int dx = camX - lastCamBlockX;
        int dy = camY - lastCamBlockY;
        int dz = camZ - lastCamBlockZ;
        return (dx * dx + dy * dy + dz * dz) >= REBUILD_MOVE_SQ;
    }

    private static void rebuildCache(Set<Long> ghostPositions, Vec3d camPos) {
        CACHED_POSITIONS.clear();
        if (ghostPositions.isEmpty()) {
            return;
        }
        BlockPos.Mutable pos = MUTABLE_POS.get();
        for (Long key : ghostPositions) {
            pos.set(key);
            double dx = pos.getX() + 0.5 - camPos.x;
            double dy = pos.getY() + 0.5 - camPos.y;
            double dz = pos.getZ() + 0.5 - camPos.z;
            if ((dx * dx + dy * dy + dz * dz) > MAX_DISTANCE_SQ) {
                continue;
            }
            CACHED_POSITIONS.add(key);
            if (CACHED_POSITIONS.size() >= MAX_RENDER_BLOCKS) {
                break;
            }
        }
    }
}
