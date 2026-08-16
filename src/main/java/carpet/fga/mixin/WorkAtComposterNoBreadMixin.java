//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.world.entity.ai.behavior.WorkAtComposter;
//#if MC >= 1.21.11
//$$ import net.minecraft.world.entity.npc.villager.Villager;
//#else
import net.minecraft.world.entity.npc.Villager;
//#endif
//#if MC >= 1.21.3
//$$ import net.minecraft.server.level.ServerLevel;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorkAtComposter.class)
public abstract class WorkAtComposterNoBreadMixin {
    @Inject(method = "makeBread", at = @At("HEAD"), cancellable = true)
    private void carpetFga$skipBreadCrafting(
            //#if MC >= 1.21.3
            //$$ ServerLevel level,
            //#endif
            Villager villager, CallbackInfo callback) {
        if (FGASettings.villagerDoNotCraftBread) {
            callback.cancel();
        }
    }
}
//#endif
