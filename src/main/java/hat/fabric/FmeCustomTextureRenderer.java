package hat.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FmeCustomTextureRenderer {
    private static final int MAX_LIGHT_LEVEL = 0xF0;
    private static final double MAX_DISTANCE_SQ = 72.0 * 72.0;
    private static final int CHUNK_RADIUS = 5;
    private static final int MAX_RENDER_PER_FRAME = 900;
    private static final int MAX_CANDIDATES = 2500;
    private static final long VALIDATE_TICKS = 200;
    private static final long MISSING_RETRY_TICKS = 40;
    private static final float UNIFORM_NORMAL_X = 0f;
    private static final float UNIFORM_NORMAL_Y = 1f;
    private static final float UNIFORM_NORMAL_Z = 0f;
    private static final Map<String, CachedTexture> TEXTURE_CACHE = new HashMap<>();
    private static final Map<net.minecraft.util.Identifier, VertexConsumer> BASE_BUFFER_CACHE = new HashMap<>();
    private static final ThreadLocal<BlockPos.Mutable> MUTABLE_POS =
        ThreadLocal.withInitial(BlockPos.Mutable::new);
    private static final Map<Long, ArrayList<TextureEntry>> ENTRIES_BY_CHUNK = new HashMap<>();
    private static final ArrayList<TextureEntry> VISIBLE_ENTRIES = new ArrayList<>();
    private static final ArrayList<TextureDistanceEntry> DISTANCE_BUFFER = new ArrayList<>();
    private static long indexedVersion = Long.MIN_VALUE;
    private static int lastCamChunkX = Integer.MIN_VALUE;
    private static int lastCamChunkY = Integer.MIN_VALUE;
    private static int lastCamChunkZ = Integer.MIN_VALUE;

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
        updateChunkIndex(custom);
        rebuildVisibleEntriesIfNeeded(camPos);
        if (VISIBLE_ENTRIES.isEmpty()) {
            return;
        }

        BASE_BUFFER_CACHE.clear();
        long now = client.world.getTime();
        BlockPos.Mutable pos = MUTABLE_POS.get();
        int limit = Math.min(MAX_RENDER_PER_FRAME, VISIBLE_ENTRIES.size());
        for (int i = 0; i < limit; i++) {
            TextureEntry entry = VISIBLE_ENTRIES.get(i);
            pos.set(entry.key);
            var state = client.world.getBlockState(pos);
            if (state.isAir() && !FmeManager.isAirGhostPosition(pos)) {
                continue;
            }
            var source = FmeManager.customTextureSourceAt(pos);
            if (source != null && state.getBlock() != source) {
                continue;
            }

            var textureId = resolveTextureId(entry.fileName, now);
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

    private static void updateChunkIndex(Map<Long, String> custom) {
        long version = FmeManager.renderDataVersion();
        if (indexedVersion == version) {
            return;
        }

        ENTRIES_BY_CHUNK.clear();
        BlockPos.Mutable pos = MUTABLE_POS.get();
        for (Map.Entry<Long, String> entry : custom.entrySet()) {
            long key = entry.getKey();
            pos.set(key);
            long chunkKey = chunkKey(
                chunkCoord(pos.getX()),
                chunkCoord(pos.getY()),
                chunkCoord(pos.getZ())
            );
            ENTRIES_BY_CHUNK.computeIfAbsent(chunkKey, ignored -> new ArrayList<>())
                .add(new TextureEntry(key, entry.getValue()));
        }
        indexedVersion = version;
        lastCamChunkX = Integer.MIN_VALUE;
        lastCamChunkY = Integer.MIN_VALUE;
        lastCamChunkZ = Integer.MIN_VALUE;
    }

    private static void rebuildVisibleEntriesIfNeeded(Vec3d camPos) {
        int camChunkX = chunkCoord((int) Math.floor(camPos.x));
        int camChunkY = chunkCoord((int) Math.floor(camPos.y));
        int camChunkZ = chunkCoord((int) Math.floor(camPos.z));
        if (camChunkX == lastCamChunkX && camChunkY == lastCamChunkY && camChunkZ == lastCamChunkZ) {
            return;
        }

        VISIBLE_ENTRIES.clear();
        DISTANCE_BUFFER.clear();
        BlockPos.Mutable pos = MUTABLE_POS.get();
        for (int x = camChunkX - CHUNK_RADIUS; x <= camChunkX + CHUNK_RADIUS; x++) {
            for (int y = camChunkY - 1; y <= camChunkY + 1; y++) {
                for (int z = camChunkZ - CHUNK_RADIUS; z <= camChunkZ + CHUNK_RADIUS; z++) {
                    List<TextureEntry> entries = ENTRIES_BY_CHUNK.get(chunkKey(x, y, z));
                    if (entries == null) {
                        continue;
                    }
                    for (TextureEntry entry : entries) {
                        pos.set(entry.key);
                        double dx = pos.getX() + 0.5 - camPos.x;
                        double dy = pos.getY() + 0.5 - camPos.y;
                        double dz = pos.getZ() + 0.5 - camPos.z;
                        double distanceSq = dx * dx + dy * dy + dz * dz;
                        if (distanceSq > MAX_DISTANCE_SQ) {
                            continue;
                        }
                        DISTANCE_BUFFER.add(new TextureDistanceEntry(entry, distanceSq));
                    }
                }
            }
        }
        DISTANCE_BUFFER.sort((a, b) -> Double.compare(a.distanceSq, b.distanceSq));
        int limit = Math.min(MAX_CANDIDATES, DISTANCE_BUFFER.size());
        for (int i = 0; i < limit; i++) {
            VISIBLE_ENTRIES.add(DISTANCE_BUFFER.get(i).entry);
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

    private record TextureEntry(long key, String fileName) {
    }

    private record TextureDistanceEntry(TextureEntry entry, double distanceSq) {
    }

    private static void drawCube(VertexConsumer vc, MatrixStack.Entry entry, int light, int color, int alpha) {
        Matrix4f mat = entry.getPositionMatrix();
        vertex(vc, entry, mat, light, color, alpha, 0, 0, 1, 0, 1, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 1, 0, 1, 1, 1, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 1, 1, 1, 1, 0, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 0, 1, 1, 0, 0, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);

        vertex(vc, entry, mat, light, color, alpha, 1, 0, 0, 0, 1, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 0, 0, 0, 1, 1, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 0, 1, 0, 1, 0, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 1, 1, 0, 0, 0, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);

        vertex(vc, entry, mat, light, color, alpha, 0, 0, 0, 0, 1, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 0, 0, 1, 1, 1, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 0, 1, 1, 1, 0, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 0, 1, 0, 0, 0, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);

        vertex(vc, entry, mat, light, color, alpha, 1, 0, 1, 0, 1, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 1, 0, 0, 1, 1, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 1, 1, 0, 1, 0, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 1, 1, 1, 0, 0, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);

        vertex(vc, entry, mat, light, color, alpha, 0, 1, 1, 0, 1, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 1, 1, 1, 1, 1, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 1, 1, 0, 1, 0, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 0, 1, 0, 0, 0, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);

        vertex(vc, entry, mat, light, color, alpha, 0, 0, 0, 0, 1, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 1, 0, 0, 1, 1, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 1, 0, 1, 1, 0, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
        vertex(vc, entry, mat, light, color, alpha, 0, 0, 1, 0, 0, UNIFORM_NORMAL_X, UNIFORM_NORMAL_Y, UNIFORM_NORMAL_Z);
    }

    private static void vertex(VertexConsumer vc, MatrixStack.Entry entry, Matrix4f mat, int light, int color, int alpha,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        vc.vertex(mat, x, y, z)
            .color(color, color, color, alpha)
            .texture(1.0f - u, v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(light)
            .normal(entry, nx, ny, nz);
    }

    private static int packedLight() {
        int level = MAX_LIGHT_LEVEL;
        return (level << 16) | level;
    }

    private static int baseColor() {
        return 255;
    }
}
