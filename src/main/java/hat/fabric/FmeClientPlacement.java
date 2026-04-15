package hat.fabric;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public final class FmeClientPlacement {
    private FmeClientPlacement() {
    }

    public static boolean tryHandleEditModePlacement(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult) {
        if (player == null || hand != Hand.MAIN_HAND || hitResult == null) {
            return false;
        }
        if (!FmeManager.isEnabled() || !FmeManager.isEditMode()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return false;
        }

        BlockPos hitPos = hitResult.getBlockPos();
        boolean hitGhost = FmeManager.isAirGhostPosition(hitPos);
        BlockState hitState = client.world.getBlockState(hitPos);
        if (hitState.isAir() && !hitGhost) {
            return false;
        }

        // Edit the block that was actually targeted. Offsetting here turns normal edits
        // into adjacent ghost placement and makes existing mapped blocks impossible to update.
        BlockPos targetPos = hitPos.toImmutable();
        BlockState targetState = client.world.getBlockState(targetPos);
        if (!FmeManager.applyReplacement(targetPos, targetState.getBlock())) {
            return false;
        }

        if (targetState.isAir() || FmeManager.isAirGhostPosition(targetPos)) {
            FmeManager.sendClientMessage("Placed ghost block using " + selectedSourceLabel());
        } else {
            FmeManager.sendClientMessage(
                targetState.getBlock().getName().getString() + " now uses " + selectedSourceLabel()
            );
        }

        player.swingHand(hand);
        return true;
    }

    private static String selectedSourceLabel() {
        if (FmeManager.getSelectedSourceType() == FmeManager.SelectedSourceType.CUSTOM_TEXTURE) {
            String name = FmeManager.getSelectedCustomTextureName();
            if (name != null && !name.isBlank()) {
                return "custom texture " + name;
            }
            return "custom texture";
        }
        return FmeManager.getSelectedSource().getName().getString() + " texture";
    }
}
