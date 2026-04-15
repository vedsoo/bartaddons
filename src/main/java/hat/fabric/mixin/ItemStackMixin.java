package hat.fabric.mixin;

import hat.fabric.FmeManager;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void hat$overrideName(CallbackInfoReturnable<Text> cir) {
        Text override = FmeManager.itemNameOverrideText((ItemStack) (Object) this);
        if (override != null && !override.getString().isBlank()) {
            cir.setReturnValue(override);
        }
    }

    @Inject(method = "getCustomName", at = @At("HEAD"), cancellable = true)
    private void hat$overrideCustomName(CallbackInfoReturnable<Text> cir) {
        FmeManager.ItemNameOverrideMapping override = FmeManager.itemNameOverrideAt((ItemStack) (Object) this);
        if (override != null) {
            cir.setReturnValue(override.toText());
        }
    }

    @Inject(method = "getFormattedName", at = @At("HEAD"), cancellable = true)
    private void hat$overrideFormattedName(CallbackInfoReturnable<Text> cir) {
        FmeManager.ItemNameOverrideMapping override = FmeManager.itemNameOverrideAt((ItemStack) (Object) this);
        if (override != null) {
            cir.setReturnValue(override.toText());
        }
    }
}
