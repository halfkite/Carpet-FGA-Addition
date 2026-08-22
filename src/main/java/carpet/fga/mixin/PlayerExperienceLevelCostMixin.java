//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerExperienceLevelCostMixin {
    @Inject(method = "getXpNeededForNextLevel", at = @At("RETURN"), cancellable = true)
    private void carpetFga$flattenExperienceLevelCost(CallbackInfoReturnable<Integer> callback) {
        if (FGASettings.usesExperienceLevelCost0To1()) {
            callback.setReturnValue(7);
        } else if (FGASettings.usesExperienceLevelCost29To30()
                && ((Player) (Object) this).experienceLevel >= 30) {
            callback.setReturnValue(107);
        }
    }
}
//#endif
