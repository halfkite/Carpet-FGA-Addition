//#if MC == 1.20.1 || MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.ItemFrameBlockificationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelItemFrameSupportMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"))
    private void carpetFga$validateItemFrameSupport(BlockPos position, BlockState state, int flags,
                                                     int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (FGASettings.itemFrameBlockification
                && cir.getReturnValue()
                && (Object) this instanceof ServerLevel level) {
            ItemFrameBlockificationManager.blockChanged(level, position);
        }
    }
}
//#endif
