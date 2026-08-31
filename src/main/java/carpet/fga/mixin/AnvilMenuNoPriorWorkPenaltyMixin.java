//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.world.inventory.AnvilMenu;
//#if MC <= 1.21.1
import net.minecraft.world.entity.player.Abilities;
//#else
//$$ import net.minecraft.world.entity.player.Player;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuNoPriorWorkPenaltyMixin {
    @Inject(method = "calculateIncreasedRepairCost", at = @At("HEAD"), cancellable = true)
    private static void carpetFga$removePriorWorkPenalty(int cost, CallbackInfoReturnable<Integer> callback) {
        if (FGASettings.anvilNoPriorWorkPenalty) {
            callback.setReturnValue(cost);
        }
    }

    //#if MC <= 1.21.1
    @Redirect(method = "createResult", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Abilities;instabuild:Z", ordinal = 1))
    private boolean carpetFga$bypassTooExpensiveLimit(Abilities abilities) {
        return FGASettings.anvilNoPriorWorkPenalty || abilities.instabuild;
    }
    //#else
//$$     @Redirect(method = "mayPickup", at = @At(value = "INVOKE",
//$$             target = "Lnet/minecraft/world/entity/player/Player;hasInfiniteMaterials()Z"))
//$$     private boolean carpetFga$bypassTooExpensiveLimit(Player player) {
//$$         return FGASettings.anvilNoPriorWorkPenalty || player.hasInfiniteMaterials();
//$$     }
    //#endif
}
//#endif
