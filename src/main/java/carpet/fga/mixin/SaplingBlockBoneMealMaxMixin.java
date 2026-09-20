package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.3
import carpet.fga.BoneMealMaxEfficiency;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
//#if MC >= 26.3
//$$ import net.minecraft.world.level.block.BonemealSource;
//#endif
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
            //#if MC < 26.3
            method = "isBonemealSuccess(Lnet/minecraft/world/level/Level;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            //#else
            //$$ method = "isBonemealSuccess(Lnet/minecraft/world/level/Level;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/BonemealSource;)Z",
            //#endif
            at = @At("HEAD"), cancellable = true
    )
    private void carpetFga$alwaysGrow(net.minecraft.world.level.Level level, RandomSource random,
                                      BlockPos pos, BlockState state,
                                      //#if MC < 26.3
                                      CallbackInfoReturnable<Boolean> cir) {
                                      //#else
                                      //$$ BonemealSource source, CallbackInfoReturnable<Boolean> cir) {
                                      //#endif
        if (BoneMealMaxEfficiency.enabled()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            //#if MC < 26.3
            method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            //#else
            //$$ method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/BonemealSource;)V",
            //#endif
            at = @At("HEAD"), cancellable = true
    )
    private void carpetFga$growImmediately(ServerLevel level, RandomSource random, BlockPos pos,
                                           BlockState state,
                                           //#if MC < 26.3
                                           CallbackInfo ci) {
                                           //#else
                                           //$$ BonemealSource source, CallbackInfo ci) {
                                           //#endif
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
