package hat.fabric.mixin;

import hat.fabric.FmeManager;
import hat.fabric.HatTextureManager;
import hat.fabric.ItemRenderStateExtension;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerMixin {
    @Unique
    private static final ThreadLocal<Boolean> HAT_BYPASS_REMAP = ThreadLocal.withInitial(() -> false);

    @Shadow
    public abstract void updateForLivingEntity(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, LivingEntity entity);

    @Shadow
    public abstract void updateForNonLivingEntity(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, Entity entity);

    @Shadow
    public abstract void clearAndUpdate(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, World world, HeldItemContext heldItemContext, int seed);

    @Shadow
    public abstract void update(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, World world, HeldItemContext heldItemContext, int seed);

    @Inject(method = "updateForLivingEntity", at = @At("HEAD"), cancellable = true)
    private void hat$remapLiving(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, LivingEntity entity, CallbackInfo ci) {
        if (hat$handleRemap(renderState, stack, displayContext, null, ci)) {
            return;
        }
        if (HAT_BYPASS_REMAP.get()) {
            return;
        }
        ItemStack replacement = FmeManager.createItemAppearanceRenderStack(stack);
        if (!replacement.isEmpty()) {
            HAT_BYPASS_REMAP.set(true);
            try {
                updateForLivingEntity(renderState, replacement, displayContext, entity);
            } finally {
                HAT_BYPASS_REMAP.set(false);
            }
            ci.cancel();
        }
    }

    @Inject(method = "updateForNonLivingEntity", at = @At("HEAD"), cancellable = true)
    private void hat$remapNonLiving(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, Entity entity, CallbackInfo ci) {
        if (hat$handleRemap(renderState, stack, displayContext, entity, ci)) {
            return;
        }
        if (HAT_BYPASS_REMAP.get()) {
            return;
        }
        ItemStack replacement = FmeManager.createItemAppearanceRenderStack(stack);
        if (!replacement.isEmpty()) {
            HAT_BYPASS_REMAP.set(true);
            try {
                updateForNonLivingEntity(renderState, replacement, displayContext, entity);
            } finally {
                HAT_BYPASS_REMAP.set(false);
            }
            ci.cancel();
        }
    }

    @Inject(method = "clearAndUpdate", at = @At("HEAD"), cancellable = true)
    private void hat$remapClearAndUpdate(
        ItemRenderState renderState,
        ItemStack stack,
        ItemDisplayContext displayContext,
        World world,
        HeldItemContext heldItemContext,
        int seed,
        CallbackInfo ci
    ) {
        if (hat$handleRemap(renderState, stack, displayContext, null, ci)) {
            return;
        }
        if (HAT_BYPASS_REMAP.get()) {
            return;
        }
        ItemStack replacement = FmeManager.createItemAppearanceRenderStack(stack);
        if (!replacement.isEmpty()) {
            HAT_BYPASS_REMAP.set(true);
            try {
                clearAndUpdate(renderState, replacement, displayContext, world, heldItemContext, seed);
            } finally {
                HAT_BYPASS_REMAP.set(false);
            }
            ci.cancel();
        }
    }

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void hat$remapUpdate(
        ItemRenderState renderState,
        ItemStack stack,
        ItemDisplayContext displayContext,
        World world,
        HeldItemContext heldItemContext,
        int seed,
        CallbackInfo ci
    ) {
        if (hat$handleRemap(renderState, stack, displayContext, null, ci)) {
            return;
        }
        if (HAT_BYPASS_REMAP.get()) {
            return;
        }
        ItemStack replacement = FmeManager.createItemAppearanceRenderStack(stack);
        if (!replacement.isEmpty()) {
            HAT_BYPASS_REMAP.set(true);
            try {
                update(renderState, replacement, displayContext, world, heldItemContext, seed);
            } finally {
                HAT_BYPASS_REMAP.set(false);
            }
            ci.cancel();
        }
    }

    @Unique
    private boolean hat$handleRemap(
        ItemRenderState renderState,
        ItemStack stack,
        ItemDisplayContext displayContext,
        Entity entity,
        CallbackInfo ci
    ) {
        ItemRenderStateExtension extension = (ItemRenderStateExtension) renderState;
        extension.hat$clearCustomTextureId();
        extension.hat$setDisplayContext(displayContext);
        if (HAT_BYPASS_REMAP.get()) {
            return false;
        }
        FmeManager.ItemAppearanceMapping mapping = FmeManager.itemAppearanceAt(stack);
        if (mapping == null || mapping.targetType != FmeManager.ItemAppearanceTargetType.CUSTOM_TEXTURE) {
            return false;
        }
        var texturePath = HatTextureManager.resolveTexturePath(mapping.targetValue);
        if (texturePath == null) {
            return false;
        }
        var textureId = HatTextureManager.getOrLoadTexture(texturePath);
        if (textureId == null) {
            return false;
        }
        renderState.clear();
        extension.hat$setCustomTextureId(textureId);
        extension.hat$setCustomTextureScale(hat$customTextureScaleFor(displayContext, entity));
        ci.cancel();
        return true;
    }

    @Unique
    private static float hat$customTextureScaleFor(ItemDisplayContext displayContext, Entity entity) {
        if (entity instanceof ProjectileEntity) {
            return 0.65f;
        }
        return 1.0f;
    }
}
