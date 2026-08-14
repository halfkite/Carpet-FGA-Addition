//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
//#if MC >= 1.21.11
//$$ import net.minecraft.world.entity.npc.villager.Villager;
//#else
import net.minecraft.world.entity.npc.Villager;
//#endif
//#if MC >= 1.21.3
//$$ import net.minecraft.server.level.ServerLevel;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerUpgradeWhileTradingMixin {
    @Shadow
    private int updateMerchantTimer;

    @Shadow
    private boolean increaseProfessionLevelOnUpdate;

    @Invoker("increaseMerchantCareer")
    //#if MC >= 1.21.11
    //$$ protected abstract void carpetFga$increaseMerchantCareer(ServerLevel level);
    //#else
    protected abstract void carpetFga$increaseMerchantCareer();
    //#endif

    @Invoker("resendOffersToTradingPlayer")
    protected abstract void carpetFga$resendOffersToTradingPlayer();

    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void carpetFga$advanceUpgradeWhileTrading(
            //#if MC >= 1.21.3
            //$$ ServerLevel level,
            //#endif
            CallbackInfo callback) {
        Villager villager = (Villager) (Object) this;
        if (!FGASettings.villagerUpgradeWhileTrading
                || !villager.isTrading()
                || this.updateMerchantTimer <= 0) {
            return;
        }

        if (--this.updateMerchantTimer > 0) {
            return;
        }

        if (this.increaseProfessionLevelOnUpdate) {
            //#if MC >= 1.21.11
            //$$ this.carpetFga$increaseMerchantCareer(level);
            //#else
            this.carpetFga$increaseMerchantCareer();
            //#endif
            this.increaseProfessionLevelOnUpdate = false;
            this.carpetFga$resendOffersToTradingPlayer();
        }
        villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
    }
}
//#endif
