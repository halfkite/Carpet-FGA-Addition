//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Constant;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuNoPriorWorkPenaltyMixin {
    @Inject(method = "calculateIncreasedRepairCost", at = @At("HEAD"), cancellable = true)
    private static void carpetFga$removePriorWorkPenalty(int cost, CallbackInfoReturnable<Integer> callback) {
        if (FGASettings.anvilNoPriorWorkPenalty) {
            callback.setReturnValue(cost);
        }
    }

    @ModifyConstant(
            method = "createResult",
            constant = @Constant(intValue = 40, ordinal = 2)
    )
    private int carpetFga$removeTooExpensiveLimit(int original) {
        return FGASettings.anvilNoPriorWorkPenalty ? Integer.MAX_VALUE : original;
    }
}
//#endif
