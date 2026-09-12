//#if MC >= 1.21 && MC <= 1.21.11
package carpet.fga.mixin;

import carpet.fga.VillagerMinimumTradePriceManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerMinimumTradePriceMixin {
    @Inject(method = "setVillagerData", at = @At("HEAD"))
    private void carpetFga$captureProfession(VillagerData data, CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        //#if MC >= 1.21.5
        //$$ VillagerMinimumTradePriceManager.professionChanged(villager,
        //$$         villager.getVillagerData().profession(), data.profession());
        //#else
        VillagerMinimumTradePriceManager.professionChanged(villager,
                villager.getVillagerData().getProfession(), data.getProfession());
        //#endif
    }
}
//#endif
