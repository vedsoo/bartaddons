package hat.fabric.mixin;

import hat.fabric.FmeManager;
import hat.fabric.ItemRenderStateExtension;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderState.class)
public abstract class ItemRenderStateMixin implements ItemRenderStateExtension {
    @Unique
    private Identifier hat$customTextureId;
    @Unique
    private float hat$customTextureScale = 1.0f;
    @Unique
    private ItemDisplayContext hat$displayContext = ItemDisplayContext.NONE;

    @Override
    public void hat$setCustomTextureId(Identifier textureId) {
        hat$customTextureId = textureId;
    }

    @Override
    public Identifier hat$getCustomTextureId() {
        return hat$customTextureId;
    }

    @Override
    public void hat$setCustomTextureScale(float scale) {
        hat$customTextureScale = scale <= 0.0f ? 1.0f : scale;
    }

    @Override
    public float hat$getCustomTextureScale() {
        return hat$customTextureScale;
    }

    @Override
    public void hat$setDisplayContext(ItemDisplayContext displayContext) {
        hat$displayContext = displayContext == null ? ItemDisplayContext.NONE : displayContext;
    }

    @Override
    public ItemDisplayContext hat$getDisplayContext() {
        return hat$displayContext;
    }

    @Override
    public void hat$clearCustomTextureId() {
        hat$customTextureId = null;
        hat$customTextureScale = 1.0f;
        hat$displayContext = ItemDisplayContext.NONE;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hat$renderCustomTexture(
        MatrixStack matrices,
        OrderedRenderCommandQueue queue,
        int light,
        int overlay,
        int color,
        CallbackInfo ci
    ) {
        if (hat$customTextureId == null || queue == null || matrices == null) {
            return;
        }
        Identifier textureId = hat$customTextureId;
        matrices.push();
        hat$applyDisplayTransform(matrices, hat$displayContext);
        if (hat$customTextureScale != 1.0f) {
            matrices.scale(hat$customTextureScale, hat$customTextureScale, hat$customTextureScale);
        }
        queue.submitCustom(matrices, RenderLayer.getEntityCutoutNoCull(textureId), (entry, vertexConsumer) ->
            hat$drawFlatItem(entry, vertexConsumer, light, overlay, color));
        matrices.pop();
        ci.cancel();
    }

    @Unique
    private static void hat$applyDisplayTransform(MatrixStack matrices, ItemDisplayContext displayContext) {
        if (displayContext == null) {
            return;
        }
        hat$applyVanillaBaseDisplayTransform(matrices, displayContext);
        switch (displayContext) {
            case FIRST_PERSON_RIGHT_HAND -> {
                matrices.translate(FmeManager.getItemFirstPersonX(), FmeManager.getItemFirstPersonY(), FmeManager.getItemFirstPersonZ());
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(FmeManager.getItemFirstPersonRotX()));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(FmeManager.getItemFirstPersonRotY()));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(FmeManager.getItemFirstPersonRotZ()));
                matrices.scale(FmeManager.getItemFirstPersonScale(), FmeManager.getItemFirstPersonScale(), FmeManager.getItemFirstPersonScale());
            }
            case FIRST_PERSON_LEFT_HAND -> {
                matrices.translate(-FmeManager.getItemFirstPersonX(), FmeManager.getItemFirstPersonY(), FmeManager.getItemFirstPersonZ());
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(FmeManager.getItemFirstPersonRotX()));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-FmeManager.getItemFirstPersonRotY()));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-FmeManager.getItemFirstPersonRotZ()));
                matrices.scale(FmeManager.getItemFirstPersonScale(), FmeManager.getItemFirstPersonScale(), FmeManager.getItemFirstPersonScale());
            }
            case THIRD_PERSON_RIGHT_HAND -> {
                matrices.translate(FmeManager.getItemThirdPersonX(), FmeManager.getItemThirdPersonY(), FmeManager.getItemThirdPersonZ());
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(FmeManager.getItemThirdPersonRotX()));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(FmeManager.getItemThirdPersonRotY()));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(FmeManager.getItemThirdPersonRotZ()));
                matrices.scale(FmeManager.getItemThirdPersonScale(), FmeManager.getItemThirdPersonScale(), FmeManager.getItemThirdPersonScale());
            }
            case THIRD_PERSON_LEFT_HAND -> {
                matrices.translate(-FmeManager.getItemThirdPersonX(), FmeManager.getItemThirdPersonY(), FmeManager.getItemThirdPersonZ());
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(FmeManager.getItemThirdPersonRotX()));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-FmeManager.getItemThirdPersonRotY()));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-FmeManager.getItemThirdPersonRotZ()));
                matrices.scale(FmeManager.getItemThirdPersonScale(), FmeManager.getItemThirdPersonScale(), FmeManager.getItemThirdPersonScale());
            }
            case GROUND -> matrices.scale(0.5f, 0.5f, 0.5f);
            case FIXED, GUI, HEAD, NONE -> {
            }
        }
    }

    @Unique
    private static void hat$applyVanillaBaseDisplayTransform(MatrixStack matrices, ItemDisplayContext displayContext) {
        switch (displayContext) {
            case FIRST_PERSON_RIGHT_HAND -> {
                matrices.translate(1.13f / 16.0f, 3.2f / 16.0f, 1.13f / 16.0f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(25.0f));
                matrices.scale(0.68f, 0.68f, 0.68f);
            }
            case FIRST_PERSON_LEFT_HAND -> {
                matrices.translate(-(1.13f / 16.0f), 3.2f / 16.0f, 1.13f / 16.0f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-25.0f));
                matrices.scale(0.68f, 0.68f, 0.68f);
            }
            case THIRD_PERSON_RIGHT_HAND -> {
                matrices.translate(0.0f, 4.0f / 16.0f, 0.5f / 16.0f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-12.0f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-35.0f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(20.0f));
                matrices.scale(0.85f, 0.85f, 0.85f);
            }
            case THIRD_PERSON_LEFT_HAND -> {
                matrices.translate(0.0f, 4.0f / 16.0f, 0.5f / 16.0f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-12.0f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(35.0f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-20.0f));
                matrices.scale(0.85f, 0.85f, 0.85f);
            }
            default -> {
            }
        }
    }

    @Unique
    private static void hat$drawFlatItem(MatrixStack.Entry entry, VertexConsumer vc, int light, int overlay, int color) {
        Matrix4f mat = entry.getPositionMatrix();
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        if (alpha == 0 && red == 0 && green == 0 && blue == 0) {
            alpha = 255;
            red = 255;
            green = 255;
            blue = 255;
        } else if (alpha == 0) {
            alpha = 255;
        }
        float minX = -0.5f;
        float maxX = 0.5f;
        float minY = -0.5f;
        float maxY = 0.5f;
        float panelZ = 0.0f;
        hat$vertex(vc, entry, mat, light, overlay, red, green, blue, alpha, minX, minY, panelZ, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f);
        hat$vertex(vc, entry, mat, light, overlay, red, green, blue, alpha, maxX, minY, panelZ, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f);
        hat$vertex(vc, entry, mat, light, overlay, red, green, blue, alpha, maxX, maxY, panelZ, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        hat$vertex(vc, entry, mat, light, overlay, red, green, blue, alpha, minX, maxY, panelZ, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
    }

    @Unique
    private static void hat$vertex(
        VertexConsumer vc,
        MatrixStack.Entry entry,
        Matrix4f mat,
        int light,
        int overlay,
        int red,
        int green,
        int blue,
        int alpha,
        float x,
        float y,
        float z,
        float u,
        float v,
        float nx,
        float ny,
        float nz
    ) {
        vc.vertex(mat, x, y, z)
            .color(red, green, blue, alpha)
            .texture(u, v)
            .overlay(overlay)
            .light(light)
            .normal(entry, nx, ny, nz);
    }
}
