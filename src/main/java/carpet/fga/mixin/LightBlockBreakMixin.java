//#if MC >= 1.21 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allows survival players to break the otherwise unbreakable light block. */
@Mixin(BlockBehaviour.class)
public abstract class LightBlockBreakMixin {
    @Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
    private void carpetFga$allowLightBlockBreak(BlockState state, Player player,
                                                 BlockGetter level, BlockPos pos,
                                                 CallbackInfoReturnable<Float> cir) {
        String mode = FGASettings.lightBlockBreakable;
        if (!state.is(Blocks.LIGHT) || !("true".equals(mode) || "onlyholding".equals(mode))
                || player.isCreative() || player.isSpectator()
                || !player.getMainHandItem().is(Items.LIGHT)) {
            return;
        }
        cir.setReturnValue(1.0F);
    }
}
//#endif
