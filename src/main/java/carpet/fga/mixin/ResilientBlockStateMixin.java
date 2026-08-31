//#if MC >= 1.21
package carpet.fga.mixin;

import carpet.fga.ResilientBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class ResilientBlockStateMixin {
    // Placement and every canSurvive-based support recheck skip the below-block type check entirely.
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void carpetFga$skipSurvivalCheck(net.minecraft.world.level.LevelReader level, BlockPos pos,
                                             CallbackInfoReturnable<Boolean> callback) {
        if (ResilientBlocks.matches((BlockState) (Object) this)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
    private void carpetFga$skipOnPlace(Level level, BlockPos pos, BlockState oldState, boolean isMoving,
                                       CallbackInfo callback) {
        if (ResilientBlocks.matches((BlockState) (Object) this)) {
            callback.cancel();
        }
    }

    //#if MC == 1.21.1
    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void carpetFga$skipShapeUpdate(net.minecraft.core.Direction direction, BlockState neighborState,
                                           net.minecraft.world.level.LevelAccessor level,
                                           BlockPos pos, BlockPos neighborPos,
                                           CallbackInfoReturnable<BlockState> callback) {
        if (ResilientBlocks.matches((BlockState) (Object) this)) {
            callback.setReturnValue((BlockState) (Object) this);
        }
    }

    // handleNeighborChanged dispatches the block's own neighborChanged, where blocks like cactus recheck
    // canSurvive and falling blocks schedule their fall; cancel it so updates never self-check the block.
    @Inject(method = "handleNeighborChanged", at = @At("HEAD"), cancellable = true)
    private void carpetFga$skipNeighborChanged(Level level, BlockPos pos, Block neighborBlock,
                                               BlockPos fromPos, boolean isMoving, CallbackInfo callback) {
        if (ResilientBlocks.matches((BlockState) (Object) this)) {
            callback.cancel();
        }
    }
    //#else
    //$$ @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    //$$ private void carpetFga$skipShapeUpdate(net.minecraft.world.level.LevelReader level,
    //$$                                         net.minecraft.world.level.ScheduledTickAccess tickAccess,
    //$$                                         BlockPos pos, net.minecraft.core.Direction direction,
    //$$                                         BlockPos neighborPos, BlockState neighborState,
    //$$                                         net.minecraft.util.RandomSource random,
    //$$                                         CallbackInfoReturnable<BlockState> callback) {
    //$$     if (ResilientBlocks.matches((BlockState) (Object) this)) {
    //$$         callback.setReturnValue((BlockState) (Object) this);
    //$$     }
    //$$ }
    //$$
    //$$ @Inject(method = "handleNeighborChanged", at = @At("HEAD"), cancellable = true)
    //$$ private void carpetFga$skipNeighborChanged(Level level, BlockPos pos, Block neighborBlock,
    //$$                                             net.minecraft.world.level.redstone.Orientation orientation,
    //$$                                             boolean isMoving, CallbackInfo callback) {
    //$$     if (ResilientBlocks.matches((BlockState) (Object) this)) {
    //$$         callback.cancel();
    //$$     }
    //$$ }
    //#endif
}
//#endif
