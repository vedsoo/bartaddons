package hat.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;

public final class ChinaHatRenderer {
    private static final double RADIUS = 0.70D;
    private static final double HEIGHT = 0.30D;
    private static final double STANDING_Y_OFFSET = 0.03D;
    private static final double MOVING_Y_OFFSET = 0.10D;
    private static final int ANGLES = 64;
    private static final float ALPHA_RING = 0.60F;
    private static final float ALPHA_HAT = 0.34F;
    private static final int RING_ALPHA_INT = (int) (ALPHA_RING * 255.0F);
    private static final int HAT_ALPHA_INT = (int) (ALPHA_HAT * 255.0F);
    private static final int TEXTURE_ALPHA_INT = 220;
    private static final float[] RING_X = new float[ANGLES + 1];
    private static final float[] RING_Z = new float[ANGLES + 1];
    private static final int FULL_LIGHT = 0xF000F0;
    private static final Color[] MLM_COLORS = {
        new Color(7, 141, 112),
        new Color(38, 206, 170),
        new Color(152, 232, 193),
        new Color(255, 255, 255),
        new Color(123, 173, 226),
        new Color(80, 74, 204),
        new Color(61, 26, 120)
    };
    private static final Color[] TRANS_COLORS = {
        new Color(91, 206, 250),
        new Color(245, 169, 184),
        new Color(255, 255, 255),
        new Color(245, 169, 184),
        new Color(91, 206, 250)
    };

    static {
        for (int i = 0; i <= ANGLES; i++) {
            double angle = i * Math.PI * 2.0D / ANGLES;
            RING_X[i] = (float) (Math.cos(angle) * RADIUS);
            RING_Z[i] = (float) (Math.sin(angle) * RADIUS);
        }
    }

    private ChinaHatRenderer() {
    }

    public static void render(WorldRenderContext context) {
        if (!HatConfig.chinaHatEnabled) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        if (client.options.getPerspective().isFirstPerson()) {
            return;
        }

        Vec3d camPos = client.gameRenderer.getCamera().getPos();
        float tickDelta = client.getRenderTickCounter().getTickProgress(true);
        double yOffset = isStandingStill(player) ? STANDING_Y_OFFSET : MOVING_Y_OFFSET;

        double px = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX()) - camPos.x;
        double py = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()) - camPos.y
            + player.getHeight() + (player.isSneaking() ? (yOffset - 0.23D) : yOffset);
        double pz = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ()) - camPos.z;

        MatrixStack matrices = context.matrices();
        if (matrices == null) {
            return;
        }

        matrices.push();
        matrices.translate(px, py, pz);

        float spinYaw = (player.age + tickDelta) * 5.0F - 90.0F;
        float headYaw = MathHelper.lerp(tickDelta, player.lastHeadYaw, player.headYaw);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(spinYaw));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-headYaw));

        try {
            drawHat(context, matrices, tickDelta, System.currentTimeMillis());
        } catch (Throwable ignored) {
            HatConfig.chinaHatEnabled = false;
        }
        matrices.pop();
    }

    private static void drawHat(WorldRenderContext context, MatrixStack matrices, float tickDelta, long nowMillis) {
        if (HatConfig.colorMode == HatConfig.ColorMode.TEXTURE) {
            if (!HatTextureManager.hasTexture()) {
                HatTextureManager.reload();
            }
            if (HatTextureManager.hasTexture()) {
                drawTexturedHat(context, matrices);
                return;
            }
        }

        Matrix4f posMatrix = matrices.peek().getPositionMatrix();
        VertexConsumer ring = context.consumers().getBuffer(RenderLayer.getDebugLineStrip(2.0D));
        for (int i = 0; i <= ANGLES; i++) {
            int rgb = getColorRgb(i, ANGLES, tickDelta, nowMillis);
            ring.vertex(posMatrix, RING_X[i], 0.0F, RING_Z[i])
                .color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, RING_ALPHA_INT);
        }

        VertexConsumer cone = context.consumers().getBuffer(RenderLayer.getDebugTriangleFan());
        int apexRgb = getColorRgb(0.0D, ANGLES, tickDelta, nowMillis);
        cone.vertex(posMatrix, 0.0F, (float) HEIGHT, 0.0F)
            .color((apexRgb >> 16) & 255, (apexRgb >> 8) & 255, apexRgb & 255, 220);

        for (int i = 0; i <= ANGLES; i++) {
            int rgb = getColorRgb(i, ANGLES, tickDelta, nowMillis);
            cone.vertex(posMatrix, RING_X[i], 0.0F, RING_Z[i])
                .color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, HAT_ALPHA_INT);
        }
    }

    private static void drawTexturedHat(WorldRenderContext context, MatrixStack matrices) {
        Matrix4f posMatrix = matrices.peek().getPositionMatrix();
        VertexConsumer ring = context.consumers().getBuffer(RenderLayer.getDebugLineStrip(2.0D));
        for (int i = 0; i <= ANGLES; i++) {
            ring.vertex(posMatrix, RING_X[i], 0.0F, RING_Z[i])
                .color(255, 255, 255, RING_ALPHA_INT);
        }

        VertexConsumer cone = context.consumers().getBuffer(RenderLayer.getEntityTranslucent(HatTextureManager.getTextureId()));
        cone.vertex(posMatrix, 0.0F, (float) HEIGHT, 0.0F)
            .color(255, 255, 255, TEXTURE_ALPHA_INT)
            .texture(0.5F, 0.0F)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(FULL_LIGHT);

        for (int i = 0; i <= ANGLES; i++) {
            float u = (float) i / (float) ANGLES;
            cone.vertex(posMatrix, RING_X[i], 0.0F, RING_Z[i])
                .color(255, 255, 255, TEXTURE_ALPHA_INT)
                .texture(u, 1.0F)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_LIGHT);
        }
    }

    private static boolean isStandingStill(ClientPlayerEntity player) {
        Vec3d vel = player.getVelocity();
        double horizontalSpeedSq = vel.x * vel.x + vel.z * vel.z;
        return player.isOnGround()
            && !player.isSneaking()
            && horizontalSpeedSq < 0.0009D
            && Math.abs(vel.y) < 0.003D;
    }

    private static int getColorRgb(double index, double max, float tickDelta, long nowMillis) {
        return switch (HatConfig.colorMode) {
            case RAINBOW -> rainbowRgb(index, max, tickDelta, nowMillis);
            case MLM -> paletteRgb(index, max, MLM_COLORS);
            case TRANS -> paletteRgb(index, max, TRANS_COLORS);
            case STATIC -> ((HatConfig.staticRed & 255) << 16)
                | ((HatConfig.staticGreen & 255) << 8)
                | (HatConfig.staticBlue & 255);
            case TEXTURE -> 0xFFFFFF;
        };
    }

    private static int paletteRgb(double index, double max, Color[] colors) {
        int stripe = (int) Math.floor((index / max) * colors.length) % colors.length;
        if (stripe < 0) {
            stripe += colors.length;
        }
        return colors[stripe].getRGB() & 0x00FFFFFF;
    }

    private static int rainbowRgb(double index, double max, float tickDelta, long nowMillis) {
        double c = index / max * 10.0D;
        if (index > max / 2.0D) {
            c = 10.0D - c;
        }
        float hue = (float) (((c / 10.0D) + (((nowMillis / 25.0D) + tickDelta) % 360.0D) / 360.0D) % 1.0D);
        return Color.HSBtoRGB(hue, 0.85F, 1.0F) & 0x00FFFFFF;
    }
}
