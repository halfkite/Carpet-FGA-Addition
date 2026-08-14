//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.TrialSpawnerStopManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TrialSpawnerState.class)
public abstract class TrialSpawnerStateStopRefreshMixin {
    @Inject(method = "tickAndGetNext", at = @At("RETURN"), cancellable = true)
    private void carpetFga$skipCooldownAfterStoppedReward(BlockPos pos, TrialSpawner spawner,
                                                          ServerLevel level,
                                                          CallbackInfoReturnable<TrialSpawnerState> callback) {
        callback.setReturnValue(TrialSpawnerStopManager.finishRewardRefresh(
                level, pos, spawner, callback.getReturnValue()));
    }
}
//#endif
