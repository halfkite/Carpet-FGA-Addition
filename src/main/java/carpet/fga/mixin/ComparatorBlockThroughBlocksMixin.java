//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.ComparatorThroughBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ComparatorBlock.class)
public abstract class ComparatorBlockThroughBlocksMixin {
    @Inject(method = "getInputSignal", at = @At("HEAD"), cancellable = true)
    private void carpetFga$readConfiguredPassthrough(
            Level level, BlockPos comparatorPos, BlockState comparatorState,
            CallbackInfoReturnable<Integer> cir) {
        Direction facing = comparatorState.getValue(ComparatorBlock.FACING);
        BlockPos passthroughPos = comparatorPos.relative(facing);
        if (!ComparatorThroughBlocks.matches(level.getBlockState(passthroughPos))) return;

        BlockPos outputPos = passthroughPos.relative(facing);
        BlockState outputState = level.getBlockState(outputPos);
        cir.setReturnValue(outputState.hasAnalogOutputSignal()
                ? outputState.getAnalogOutputSignal(level, outputPos)
                : 0);
    }
}
//#endif
