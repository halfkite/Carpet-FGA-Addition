package carpet.fga.mixin;

//#if MC == 1.21.1
import carpet.fga.BoneMealMaxEfficiency;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SaplingBlock.class)
abstract class SaplingBlockBoneMealMaxMixin {
    @Shadow @Final private TreeGrower treeGrower;

    @Inject(
            method = "isBonemealSuccess(Lnet/minecraft/world/level/Level;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At("HEAD"), cancellable = true
    )
    private void carpetFga$alwaysGrow(net.minecraft.world.level.Level level, RandomSource random,
                                      BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (BoneMealMaxEfficiency.enabled()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"), cancellable = true
    )
    private void carpetFga$growImmediately(ServerLevel level, RandomSource random, BlockPos pos,
                                           BlockState state, CallbackInfo ci) {
        if (!BoneMealMaxEfficiency.enabled()) {
            return;
        }
        SaplingBlock self = (SaplingBlock) (Object) this;
        BlockState readyState = state.setValue(SaplingBlock.STAGE, 1);
        boolean grown = BoneMealMaxEfficiency.growTreeToLimit(treeGrower, level, pos, readyState, random);
        if (!grown && level.getBlockState(pos).is(self)) {
            level.setBlock(pos, readyState, 4);
        }
        ci.cancel();
    }
}
//#endif
