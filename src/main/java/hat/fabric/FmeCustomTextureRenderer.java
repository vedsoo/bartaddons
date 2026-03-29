package hat.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class FmeCustomTextureRenderer {
    private static final int MAX_LIGHT_LEVEL = 0xF0;
    private static final double MAX_DISTANCE_SQ = 128.0 * 128.0;
    private static final long VALIDATE_TICKS = 200;
    private static final long MISSING_RETRY_TICKS = 40;
    private static final float UNIFORM_NORMAL_X = 0f;
    private static final float UNIFORM_NORMAL_Y = -1f;
    private static final float UNIFORM_NORMAL_Z = 0f;
    private static final Map<String, CachedTexture> TEXTURE_CACHE = new HashMap<>();
    private static final Map<net.minecraft.util.Identifier, VertexConsumer> BASE_BUFFER_CACHE = new HashMap<>();
    private static final ThreadLocal<BlockPos.Mutable> MUTABLE_POS =
        ThreadLocal.withInitial(BlockPos.Mutable::new);

    private FmeCustomTextureRenderer() {
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
        Vec3d camPos = client.gameRenderer.getCamera().getPos();
        Map<Long, String> custom = FmeManager.customTextureReplacementsView();
        if (custom.isEmpty()) {
            return;
        }
        HatTextureManager.tickAnimations(System.currentTimeMillis());

        BASE_BUFFER_CACHE.clear();
        long now = client.world.getTime();
        for (Map.Entry<Long, String> entry : custom.entrySet()) {
            BlockPos.Mutable pos = MUTABLE_POS.get().set(entry.getKey());
            double dx = pos.getX() + 0.5 - camPos.x;
            double dy = pos.getY() + 0.5 - camPos.y;
            double dz = pos.getZ() + 0.5 - camPos.z;
            if ((dx * dx + dy * dy + dz * dz) > MAX_DISTANCE_SQ) {
                continue;
            }
            var state = client.world.getBlockState(pos);
            if (state.isAir() && !FmeManager.isAirGhostPosition(pos)) {
                continue;
            }
            var source = FmeManager.customTextureSourceAt(pos);
            if (source != null && state.getBlock() != source) {
                continue;
            }

            var textureId = resolveTextureId(entry.getValue(), now);
            if (textureId == null) {
                continue;
            }

            matrices.push();
            matrices.translate(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);
            int turns = FmeManager.rotationAt(pos);
            if (turns != 0) {
                matrices.translate(0.5, 0.5, 0.5);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0f * turns));
                matrices.translate(-0.5, -0.5, -0.5);
            }
            MatrixStack.Entry poseEntry = matrices.peek();
            int baseLight = packedLight();
            int baseColor = baseColor();
            VertexConsumer vc = BASE_BUFFER_CACHE.computeIfAbsent(
                textureId,
                id -> context.consumers().getBuffer(RenderLayer.getEntityCutoutNoCull(id))
            );
            drawCube(vc, poseEntry, baseLight, baseColor, 255);
            matrices.pop();
        }
    }

    private static net.minecraft.util.Identifier resolveTextureId(String fileName, long now) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        CachedTexture cached = TEXTURE_CACHE.get(fileName);
        if (cached != null) {
            long age = now - cached.lastCheckTick;
            if (cached.id != null && age < VALIDATE_TICKS) {
                return cached.id;
            }
            if (cached.id == null && age < MISSING_RETRY_TICKS) {
                return null;
            }
        }
        Path texturePath = HatTextureManager.resolveTexturePath(fileName);
        if (texturePath == null) {
            TEXTURE_CACHE.put(fileName, new CachedTexture(null, now));
            return null;
        }
        var textureId = HatTextureManager.getOrLoadTexture(texturePath);
        TEXTURE_CACHE.put(fileName, new CachedTexture(textureId, now));
        return textureId;
    }

    private record CachedTexture(net.minecraft.util.Identifier id, long lastCheckTick) {
    }

    private static void drawCube(VertexConsumer vc, MatrixStack.Entry entry, int light, int color, int alpha) {
        Matrix4f mat = entry.getPositionMatrix();
        // North (-Z)
        vertex(vc, entry, mat, light, color, alpha, 0, 0, 0, 0, 1, 0, 0, -1);
        vertex(vc, entry, mat, light, color, alpha, 1, 0, 0, 1, 1, 0, 0, -1);
        vertex(vc, entry, mat, light, color, alpha, 1, 1, 0, 1, 0, 0, 0, -1);
        vertex(vc, entry, mat, light, color, alpha, 0, 1, 0, 0, 0, 0, 0, -1);
        // South (+Z)
        vertex(vc, entry, mat, light, color, alpha, 1, 0, 1, 0, 1, 0, 0, 1);
        vertex(vc, entry, mat, light, color, alpha, 0, 0, 1, 1, 1, 0, 0, 1);
        vertex(vc, entry, mat, light, color, alpha, 0, 1, 1, 1, 0, 0, 0, 1);
        vertex(vc, entry, mat, light, color, alpha, 1, 1, 1, 0, 0, 0, 0, 1);
        // West (-X)
        vertex(vc, entry, mat, light, color, alpha, 0, 0, 1, 0, 1, -1, 0, 0);
        vertex(vc, entry, mat, light, color, alpha, 0, 0, 0, 1, 1, -1, 0, 0);
        vertex(vc, entry, mat, light, color, alpha, 0, 1, 0, 1, 0, -1, 0, 0);
        vertex(vc, entry, mat, light, color, alpha, 0, 1, 1, 0, 0, -1, 0, 0);
        // East (+X)
        vertex(vc, entry, mat, light, color, alpha, 1, 0, 0, 0, 1, 1, 0, 0);
        vertex(vc, entry, mat, light, color, alpha, 1, 0, 1, 1, 1, 1, 0, 0);
        vertex(vc, entry, mat, light, color, alpha, 1, 1, 1, 1, 0, 1, 0, 0);
        vertex(vc, entry, mat, light, color, alpha, 1, 1, 0, 0, 0, 1, 0, 0);
        // Down (-Y)
        vertex(vc, entry, mat, light, color, alpha, 0, 0, 1, 0, 1, 0, -1, 0);
        vertex(vc, entry, mat, light, color, alpha, 1, 0, 1, 1, 1, 0, -1, 0);
        vertex(vc, entry, mat, light, color, alpha, 1, 0, 0, 1, 0, 0, -1, 0);
        vertex(vc, entry, mat, light, color, alpha, 0, 0, 0, 0, 0, 0, -1, 0);
        // Up (+Y)
        vertex(vc, entry, mat, light, color, alpha, 0, 1, 0, 0, 1, 0, 1, 0);
        vertex(vc, entry, mat, light, color, alpha, 1, 1, 0, 1, 1, 0, 1, 0);
        vertex(vc, entry, mat, light, color, alpha, 1, 1, 1, 1, 0, 0, 1, 0);
        vertex(vc, entry, mat, light, color, alpha, 0, 1, 1, 0, 0, 0, 1, 0);
    }

    private static void vertex(VertexConsumer vc, MatrixStack.Entry entry, Matrix4f mat, int light, int color, int alpha,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        vc.vertex(mat, x, y, z)
            .color(color, color, color, alpha)
            .texture(1.0f - u, v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(light)
            .normal(entry, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
    }

    private static int packedLight() {
        int level = MAX_LIGHT_LEVEL;
        return (level << 16) | level;
    }

    private static int baseColor() {
        return 255;
    }
}
