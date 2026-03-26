package hat.fabric.mixin;

import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.block.BlockRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockRenderManager.class)
public interface BlockRenderManagerAccessor {
    @Accessor("models")
    BlockModels hat$getModels();

    @Accessor("blockModelRenderer")
    BlockModelRenderer hat$getBlockModelRenderer();
}
