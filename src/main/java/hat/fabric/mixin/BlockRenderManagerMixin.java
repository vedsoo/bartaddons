package hat.fabric.mixin;

import hat.fabric.FmeManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(BlockRenderManager.class)
public abstract class BlockRenderManagerMixin {
    @Shadow
    @Final
    private BlockModels models;

    @Shadow
    @Final
    private BlockModelRenderer blockModelRenderer;

    private static final ThreadLocal<Random> HAT_FME_RANDOM =
        ThreadLocal.withInitial(Random::create);
    private static final ThreadLocal<ArrayList<BlockModelPart>> HAT_FME_PARTS =
        ThreadLocal.withInitial(ArrayList::new);

    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void hat$renderMapped(
        BlockState state,
        BlockPos pos,
        BlockRenderView world,
        MatrixStack matrices,
        VertexConsumer vertexConsumer,
        boolean cull,
        List<BlockModelPart> parts,
        CallbackInfo ci
    ) {
        BlockState mapped = FmeManager.remapStateAt(state, pos);
        if (mapped == state) {
            return;
        }

        BlockStateModel mappedModel = this.models.getModel(mapped);
        ArrayList<BlockModelPart> mappedParts = HAT_FME_PARTS.get();
        mappedParts.clear();

        Random random = HAT_FME_RANDOM.get();
        random.setSeed(mapped.getRenderingSeed(pos));
        mappedModel.addParts(random, mappedParts);
        this.blockModelRenderer.render(world, mappedParts, mapped, pos, matrices, vertexConsumer, cull, OverlayTexture.DEFAULT_UV);
        ci.cancel();
    }
}
