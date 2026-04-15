package hat.fabric.mixin;

import hat.fabric.FmeManager;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class ClientWorldMixin {
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void hat$solidClientFme(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState original = cir.getReturnValue();
        if (!original.isAir()) {
            return;
        }

        BlockState solid = FmeManager.solidClientStateAt(pos);
        if (solid != null && !solid.isAir()) {
            cir.setReturnValue(solid);
        }
    }
}
