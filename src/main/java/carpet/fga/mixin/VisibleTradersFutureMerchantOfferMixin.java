//#if MC >= 1.21 && MC <= 1.21.11
package carpet.fga.mixin;

import carpet.fga.VillagerMinimumTradePriceManager;
import carpet.fga.VillagerOnlyMaxEnchantmentTradesManager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional compatibility hook for VisibleTraders when that mod is installed. */
@Pseudo
@Mixin(targets = "net.ramixin.visibletraders.threading.FutureMerchantOffer", remap = false)
public abstract class VisibleTradersFutureMerchantOfferMixin {
    @Shadow
    public abstract MerchantOffer getFuture();

    @Inject(method = "fulfillFuture", at = @At("TAIL"), remap = false)
    private void carpetFga$applyMinimumAfterFuture(CallbackInfo ci) {
        VillagerOnlyMaxEnchantmentTradesManager.futureOfferFulfilled(
                (MerchantOffer) (Object) this, this.getFuture());
        VillagerMinimumTradePriceManager.futureOfferFulfilled(
                (MerchantOffer) (Object) this, this.getFuture());
    }
}
//#endif
