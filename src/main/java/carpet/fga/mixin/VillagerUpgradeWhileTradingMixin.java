//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.trading.MerchantOffer;
//#if MC >= 1.21.11
//$$ import net.minecraft.world.entity.npc.villager.Villager;
//#else
import net.minecraft.world.entity.npc.Villager;
//#endif
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
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

    @Invoker("shouldIncreaseLevel")
    protected abstract boolean carpetFga$shouldIncreaseLevel();

    @Invoker("resendOffersToTradingPlayer")
    protected abstract void carpetFga$resendOffersToTradingPlayer();

    /**
     * Vanilla defers a pending profession upgrade for 40 ticks so that it can
     * happen after the villager stops trading.  When this rule is enabled the
     * upgrade must be visible in the open merchant screen immediately after
     * the trade that crossed the threshold.
     */
    private void carpetFga$upgradeImmediately() {
        if (!this.increaseProfessionLevelOnUpdate) {
            return;
        }

        do {
            //#if MC >= 1.21.11
            //$$ this.carpetFga$increaseMerchantCareer((ServerLevel) ((Villager) (Object) this).level());
            //#else
            this.carpetFga$increaseMerchantCareer();
            //#endif
        } while (this.carpetFga$shouldIncreaseLevel());

        this.increaseProfessionLevelOnUpdate = false;
        this.updateMerchantTimer = 0;
    }

    private void carpetFga$scheduleOffersSync() {
        Villager villager = (Villager) (Object) this;
        if (!(villager.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // MerchantResultSlot still updates the payment slots after notifyTrade.
        // Sending the merchant packet only after that transaction has finished
        // keeps an exhausted offer's disabled marker intact on the client.
        MinecraftServer server = serverLevel.getServer();
        server.executeIfPossible(() -> {
            if (villager.isAlive() && villager.getTradingPlayer() != null) {
                this.carpetFga$resendOffersToTradingPlayer();
            }
        });
    }

    @Inject(method = "rewardTradeXp", at = @At("TAIL"))
    private void carpetFga$upgradeAndSyncImmediately(MerchantOffer offer, CallbackInfo callback) {
        if (FGASettings.villagerUpgradeWhileTrading) {
            this.carpetFga$upgradeImmediately();
            this.carpetFga$scheduleOffersSync();
        }
    }

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
            this.carpetFga$upgradeImmediately();
            this.carpetFga$scheduleOffersSync();
        }
        villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
    }
}
//#endif
