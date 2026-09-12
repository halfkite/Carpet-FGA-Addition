//#if MC >= 26.1
//$$ package carpet.fga.mixin;

//$$ import carpet.fga.ModernVillagerTradeManager;
//$$ import net.minecraft.resources.ResourceKey;
//$$ import net.minecraft.server.level.ServerLevel;
//$$ import net.minecraft.world.entity.npc.villager.AbstractVillager;
//$$ import net.minecraft.world.entity.npc.villager.Villager;
//$$ import net.minecraft.world.item.trading.MerchantOffers;
//$$ import net.minecraft.world.item.trading.TradeSet;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Unique;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//$$ @Mixin(AbstractVillager.class)
//$$ public abstract class ModernVillagerTradeMixin {
//$$     @Unique
//$$     private int carpetFga$offerStart;

//$$     @Inject(method = "addOffersFromTradeSet", at = @At("HEAD"))
//$$     private void carpetFga$captureOffers(ServerLevel level, MerchantOffers offers,
//$$                                           ResourceKey<TradeSet> key, CallbackInfo ci) {
//$$         this.carpetFga$offerStart = offers.size();
//$$     }

//$$     @Inject(method = "addOffersFromTradeSet", at = @At("TAIL"))
//$$     private void carpetFga$processOffers(ServerLevel level, MerchantOffers offers,
//$$                                           ResourceKey<TradeSet> key, CallbackInfo ci) {
//$$         Villager villager = (Villager) (Object) this;
//$$         for (int index = Math.min(this.carpetFga$offerStart, offers.size()); index < offers.size(); index++) {
//$$             ModernVillagerTradeManager.process(villager, offers.get(index));
//$$         }
//$$     }
//$$ }
//#endif
