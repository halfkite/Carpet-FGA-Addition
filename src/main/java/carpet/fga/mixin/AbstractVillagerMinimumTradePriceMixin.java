//#if MC >= 1.21 && MC <= 1.21.11
package carpet.fga.mixin;

import carpet.fga.VillagerMinimumTradePriceManager;
import carpet.fga.VillagerOnlyMaxEnchantmentTradesManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
//#if MC >= 1.21.11
//$$ import net.minecraft.server.level.ServerLevel;
//#endif
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerMinimumTradePriceMixin {
    @Redirect(
            method = "addOffersFromItemListings",
            at = @At(value = "INVOKE", target =
                    //#if MC >= 1.21.11
                    //$$ "Lnet/minecraft/world/entity/npc/villager/VillagerTrades$ItemListing;getOffer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/item/trading/MerchantOffer;"
                    //#else
                    "Lnet/minecraft/world/entity/npc/VillagerTrades$ItemListing;getOffer(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/item/trading/MerchantOffer;"
                    //#endif
                    )
    )
    //#if MC >= 1.21.11
    //$$ private MerchantOffer carpetFga$minimumRandomCost(VillagerTrades.ItemListing listing,
    //$$         ServerLevel level, Entity entity, RandomSource random) {
    //$$     MerchantOffer offer = listing.getOffer(level, entity, random);
    //#else
    private MerchantOffer carpetFga$minimumRandomCost(VillagerTrades.ItemListing listing,
            Entity entity, RandomSource random) {
        MerchantOffer offer = listing.getOffer(entity, random);
    //#endif
        if (entity instanceof Villager villager) {
            offer = VillagerOnlyMaxEnchantmentTradesManager.choose(villager, listing, entity, random, offer);
            return VillagerMinimumTradePriceManager.chooseMinimum(villager, listing, entity, random, offer);
        }
        return offer;
    }
}
//#endif
