//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.TrialSpawnerMultiplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TrialSpawnerData.class)
public abstract class TrialSpawnerDataMultiplierMixin {
    @Inject(method = "countAdditionalPlayers", at = @At("RETURN"), cancellable = true)
    private void carpetFga$multiplyDetectedPlayers(BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(TrialSpawnerMultiplier.additionalPlayers(
                (TrialSpawnerData) (Object) this, callback.getReturnValueI()));
    }
}
//#endif
