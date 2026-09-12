//#if MC >= 1.21 && MC <= 1.21.11
package carpet.fga;

import carpet.fga.mixin.MerchantOfferAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
//#if MC >= 1.21.5
//$$ import net.minecraft.core.Holder;
//#endif
import net.minecraft.world.entity.npc.VillagerTrades;
//#if MC >= 1.21.11
//$$ import net.minecraft.server.level.ServerLevel;
//#endif
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.util.RandomSource;

import java.util.IdentityHashMap;
import java.util.Map;

/** Applies the lowest vanilla random input cost without changing trade state. */
public final class VillagerMinimumTradePriceManager {
    private static final Map<Villager, Boolean> NEW_PROFESSION = new IdentityHashMap<>();
    private static final Map<MerchantOffer, FutureContext> FUTURES = new IdentityHashMap<>();

    private record FutureContext(Villager villager, VillagerTrades.ItemListing listing) {
    }

    private VillagerMinimumTradePriceManager() {
    }

    public static boolean enabled() {
        return !"false".equals(FGASettings.villagerMinimumTradePrice);
    }

    public static boolean applies(Villager villager) {
        // "onlynew" is retained here only as a read-time compatibility value
        // for worlds that were saved before the option was removed.
        return ("true".equals(FGASettings.villagerMinimumTradePrice)
                || "onlynew".equals(FGASettings.villagerMinimumTradePrice))
                && NEW_PROFESSION.getOrDefault(villager, false);
    }

    public static boolean isNewProfession(Villager villager) {
        return NEW_PROFESSION.getOrDefault(villager, false);
    }

    //#if MC >= 1.21.5
    //$$ public static void professionChanged(Villager villager, Holder<VillagerProfession> oldProfession,
    //$$         Holder<VillagerProfession> newProfession) {
    //$$     if (oldProfession != null && oldProfession.is(VillagerProfession.NONE)
    //$$             && !newProfession.is(VillagerProfession.NONE)) {
    //$$         NEW_PROFESSION.put(villager, true);
    //$$     } else if (newProfession.is(VillagerProfession.NONE)) {
    //$$         NEW_PROFESSION.remove(villager);
    //$$     }
    //$$ }
    //#else
    public static void professionChanged(Villager villager, VillagerProfession oldProfession,
            VillagerProfession newProfession) {
        if (oldProfession == VillagerProfession.NONE && newProfession != VillagerProfession.NONE) {
            NEW_PROFESSION.put(villager, true);
        } else if (newProfession == VillagerProfession.NONE) {
            // A villager that loses its job can be bound again later.
            NEW_PROFESSION.remove(villager);
        }
    }
    //#endif

    public static void clear(Villager villager) {
        NEW_PROFESSION.remove(villager);
    }

    public static MerchantOffer chooseMinimum(Villager villager, VillagerTrades.ItemListing listing,
            Entity entity, RandomSource random, MerchantOffer original) {
        if (!enabled() || !applies(villager) || original == null) {
            return original;
        }

        if (original.getClass().getName().contains("FutureMerchantOffer")) {
            // VisibleTraders completes this offer on its worker thread. Keep the
            // villager association so the completed offer can be adjusted there.
            FUTURES.put(original, new FutureContext(villager, listing));
            return original;
        }

        if (applyEnchantedBookMinimum(original)) {
            return original;
        }

        MerchantOffer best = original;
        int bestCount = original.getItemCostA().count();
        // Trade listings use the same random source for the random price and, for a few
        // listings, the result. Keep only candidates with the same visible trade result.
        // Repeating the listing gives the actual lower bound for the normal vanilla trades
        // while leaving uses, demand, reputation and the result untouched.
        for (int i = 0; i < 64; i++) {
            //#if MC >= 1.21.11
            //$$ MerchantOffer candidate = listing.getOffer((ServerLevel) villager.level(), entity, random);
            //#else
            MerchantOffer candidate = listing.getOffer(entity, random);
            //#endif
            candidate = VillagerOnlyMaxEnchantmentTradesManager.promoteGeneratedOffer(villager, candidate);
            if (candidate == null || !sameStack(candidate.getResult(), original.getResult())
                    || !sameSecondCost(candidate, original)) {
                continue;
            }
            int count = candidate.getItemCostA().count();
            if (count < bestCount) {
                best = candidate;
                bestCount = count;
            }
            if (bestCount <= minimumBound(original)) {
                break;
            }
        }

        if (best != original) {
            ((MerchantOfferAccessor) (Object) original).carpetFga$setBaseCostA(
                    new ItemCost(original.getItemCostA().item(), bestCount,
                            original.getItemCostA().components()));
        }
        return original;
    }

    public static void futureOfferFulfilled(MerchantOffer futureOffer, MerchantOffer completedOffer) {
        FutureContext context = FUTURES.remove(futureOffer);
        if (context != null && completedOffer != null) {
            chooseMinimum(context.villager(), context.listing(), context.villager(),
                    context.villager().getRandom(), completedOffer);
        }
    }

    private static boolean applyEnchantedBookMinimum(MerchantOffer offer) {
        if (offer.getResult().getItem() != Items.ENCHANTED_BOOK) {
            return false;
        }
        ItemEnchantments enchantments = offer.getResult().get(DataComponents.STORED_ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) {
            return false;
        }
        int level = 1;
        boolean treasure = false;
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            level = Math.max(level, enchantments.getLevel(holder));
            treasure |= holder.is(EnchantmentTags.TREASURE);
        }
        int minimum = Math.min(64, 2 + 3 * level);
        if (treasure) {
            minimum = Math.min(64, minimum * 2);
        }
        if (offer.getItemCostA().count() > minimum) {
            ((MerchantOfferAccessor) (Object) offer).carpetFga$setBaseCostA(
                    new ItemCost(offer.getItemCostA().item(), minimum,
                            offer.getItemCostA().components()));
        }
        return true;
    }

    private static int minimumBound(MerchantOffer offer) {
        // Vanilla never accepts a non-positive input cost.
        return 1;
    }

    private static boolean sameSecondCost(MerchantOffer first, MerchantOffer second) {
        if (first.getItemCostB().isEmpty() != second.getItemCostB().isEmpty()) {
            return false;
        }
        return first.getItemCostB().isEmpty()
                || (sameItem(first.getItemCostB().get(), second.getItemCostB().get()));
    }

    private static boolean sameItem(ItemCost first, ItemCost second) {
        return first.item() == second.item() && first.components().equals(second.components());
    }

    private static boolean sameStack(net.minecraft.world.item.ItemStack first,
            net.minecraft.world.item.ItemStack second) {
        return first.is(second.getItem()) && first.getComponents().equals(second.getComponents());
    }
}
//#endif
