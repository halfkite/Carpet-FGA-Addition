//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.ComparatorThroughBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
//#if MC >= 1.21.2
//$$ import net.minecraft.world.level.redstone.Orientation;
//#endif
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/** Extends comparator neighbour notifications to configured passthrough blocks. */
@Mixin(Level.class)
public abstract class LevelComparatorThroughBlocksMixin {
    @Inject(method = "updateNeighbourForOutputSignal", at = @At("HEAD"), cancellable = true)
    private void carpetFga$notifyThroughConfiguredBlock(BlockPos sourcePos, Block sourceBlock, CallbackInfo ci) {
        Level level = (Level) (Object) this;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = sourcePos.relative(direction);
            if (!level.hasChunkAt(neighborPos)) continue;

            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.is(Blocks.COMPARATOR)) {
                //#if MC >= 1.21.2
                //$$ level.neighborChanged(neighborState, neighborPos, sourceBlock,
                //$$         Orientation.of(Direction.UP, direction, Orientation.SideBias.LEFT), false);
                //#else
                level.neighborChanged(neighborState, neighborPos, sourceBlock, sourcePos, false);
                //#endif
                continue;
            }
            if (!neighborState.isRedstoneConductor(level, neighborPos)
                    && !ComparatorThroughBlocks.matches(neighborState)) continue;

            BlockPos beyondPos = neighborPos.relative(direction);
            if (!level.hasChunkAt(beyondPos)) continue;
            BlockState beyondState = level.getBlockState(beyondPos);
            if (beyondState.is(Blocks.COMPARATOR)) {
                //#if MC >= 1.21.2
                //$$ level.neighborChanged(beyondState, beyondPos, sourceBlock,
                //$$         Orientation.of(Direction.UP, direction, Orientation.SideBias.LEFT), false);
                //#else
                level.neighborChanged(beyondState, beyondPos, sourceBlock, sourcePos, false);
                //#endif
            }
        }
        ci.cancel();
    }
}
//#endif
