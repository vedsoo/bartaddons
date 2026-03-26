package hat.fabric.mixin;

import hat.fabric.FmeManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumBlockRendererMixin {
    @ModifyVariable(method = "renderModel", at = @At("HEAD"), argsOnly = true, index = 1, require = 0)
    private BlockStateModel hat$swapSodiumModel(
        BlockStateModel value,
        BlockStateModel model,
        BlockState state,
        BlockPos pos,
        BlockPos origin
    ) {
        BlockState mapped = FmeManager.remapStateAt(state, pos);
        if (mapped == state) {
            return value;
        }
        return MinecraftClient.getInstance().getBlockRenderManager().getModel(mapped);
    }

    @ModifyVariable(method = "renderModel", at = @At("HEAD"), argsOnly = true, index = 2, require = 0)
    private BlockState hat$swapSodiumState(
        BlockState value,
        BlockStateModel model,
        BlockState state,
        BlockPos pos,
        BlockPos origin
    ) {
        return FmeManager.remapStateAt(value, pos);
    }
}
