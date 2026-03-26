package hat.fabric.mixin;

import hat.fabric.FmeManager;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class SodiumLevelSliceMixin {
    private static final ThreadLocal<BlockPos.Mutable> HAT_MUTABLE_POS =
        ThreadLocal.withInitial(BlockPos.Mutable::new);

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true, require = 0)
    private void hat$remapStateForSodiumCulling(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        BlockState original = cir.getReturnValue();
        BlockPos.Mutable pos = HAT_MUTABLE_POS.get().set(x, y, z);
        BlockState mapped = FmeManager.remapStateAt(original, pos);
        if (mapped != original) {
            cir.setReturnValue(mapped);
        }
    }
}
