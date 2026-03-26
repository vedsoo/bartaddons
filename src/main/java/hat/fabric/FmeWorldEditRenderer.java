package hat.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class FmeWorldEditRenderer {
    private FmeWorldEditRenderer() {
    }

    public static void render(WorldRenderContext context) {
        HatClientMod.Selection selection = HatClientMod.getWorldEditSelectionForRender();
        if (selection == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        int color = FmeManager.getSelectionBoxColor();
        float a = ((color >> 24) & 0xFF) / 255.0f;
        if (a <= 0.0f) {
            return;
        }
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        MatrixStack matrices = context.matrices();
        if (matrices == null) {
            return;
        }

        Vec3d cam = client.gameRenderer.getCamera().getPos();
        double minX = selection.min().getX();
        double minY = selection.min().getY();
        double minZ = selection.min().getZ();
        double maxX = selection.max().getX() + 1.0;
        double maxY = selection.max().getY() + 1.0;
        double maxZ = selection.max().getZ() + 1.0;
        Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        DebugRenderer.drawBox(matrices, context.consumers(), box, r, g, b, a);
        Vec3d camLocal = cam.subtract(box.getCenter());
        matrices.translate(camLocal.x, camLocal.y, camLocal.z);
        double nudge = 0.008;
        Box inner = box.expand(-nudge);
        Box outer = box.expand(nudge);
        DebugRenderer.drawBox(matrices, context.consumers(), inner, r, g, b, a);
        DebugRenderer.drawBox(matrices, context.consumers(), outer, r, g, b, a);
        matrices.pop();
    }
}
