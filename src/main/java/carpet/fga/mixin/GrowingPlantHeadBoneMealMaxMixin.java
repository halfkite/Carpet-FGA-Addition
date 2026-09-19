package carpet.fga.mixin;

//#if MC == 1.21.1
import carpet.fga.BoneMealMaxEfficiency;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.TwistingVinesBlock;
import net.minecraft.world.level.block.WeepingVinesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GrowingPlantHeadBlock.class)
abstract class GrowingPlantHeadBoneMealMaxMixin {
    @org.spongepowered.asm.mixin.Shadow
    protected abstract boolean canGrowInto(BlockState state);

    @Inject(
            method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void carpetFga$maxVineGrowth(ServerLevel level, RandomSource random, BlockPos pos,
                                          BlockState state, CallbackInfo ci) {
        if (!BoneMealMaxEfficiency.enabled()) {
            return;
        }
        Object block = this;
        if (block instanceof WeepingVinesBlock || block instanceof TwistingVinesBlock) {
            Direction direction = block instanceof WeepingVinesBlock ? Direction.DOWN : Direction.UP;
            BoneMealMaxEfficiency.growVinesToLimit(level, pos, state, direction,
                    this::canGrowInto);
            ci.cancel();
        }
    }
}
//#endif
