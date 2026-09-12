//#if MC >= 1.21 && MC <= 1.21.11
package carpet.fga;

import carpet.fga.mixin.MerchantOfferAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
//#if MC >= 1.21.2
//$$ import net.minecraft.world.item.enchantment.Enchantable;
//#endif
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.util.RandomSource;

import java.util.IdentityHashMap;
import java.util.Map;

/** Promotes newly generated villager enchantments to their effective maximum level. */
public final class VillagerOnlyMaxEnchantmentTradesManager {
    private static final Map<MerchantOffer, FutureContext> FUTURES = new IdentityHashMap<>();

    private record FutureContext(Villager villager) {
    }

    private VillagerOnlyMaxEnchantmentTradesManager() {
    }

    public static MerchantOffer choose(Villager villager, VillagerTrades.ItemListing listing,
            Entity entity, RandomSource random, MerchantOffer original) {
        if (!hasEnabledRule()
                || !VillagerMinimumTradePriceManager.isNewProfession(villager)
                || original == null) {
            return original;
        }

        if (original.getClass().getName().contains("FutureMerchantOffer")) {
            FUTURES.put(original, new FutureContext(villager));
            return original;
        }

        return promoteEnchantments(original);
    }

    /** Applies the same result normalization to a repeated listing used by another rule. */
    public static MerchantOffer promoteGeneratedOffer(Villager villager, MerchantOffer offer) {
        if (offer == null || !hasEnabledRule()
                || !VillagerMinimumTradePriceManager.isNewProfession(villager)) {
            return offer;
        }
        return promoteEnchantments(offer);
    }

    public static void futureOfferFulfilled(MerchantOffer futureOffer, MerchantOffer completedOffer) {
        FutureContext context = FUTURES.remove(futureOffer);
        if (context == null || completedOffer == null
                || !hasEnabledRule()
                || !VillagerMinimumTradePriceManager.isNewProfession(context.villager())) {
            return;
        }

        promoteEnchantments(completedOffer);
    }

    private static MerchantOffer promoteEnchantments(MerchantOffer offer) {
        ItemStack result = offer.getResult();
        boolean enchantedBook = result.is(Items.ENCHANTED_BOOK);
        String mode = enchantedBook
                ? FGASettings.villagerOnlyMaxEnchantmentBooks
                : FGASettings.villagerOnlyMaxEnchantmentEquipment;
        if ("false".equals(mode)) {
            return offer;
        }
        ItemEnchantments enchantments = result.get(enchantedBook
                ? DataComponents.STORED_ENCHANTMENTS
                : DataComponents.ENCHANTMENTS);

        // Ordinary books, unenchanted equipment and all other trades are unchanged.
        if (enchantments == null || enchantments.isEmpty()) {
            return offer;
        }

        ItemEnchantments.Mutable upgraded = new ItemEnchantments.Mutable(enchantments);
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            upgraded.set(holder, effectiveMaximum(result, holder, mode));
        }

        ItemStack upgradedResult = result.copy();
        upgradedResult.set(enchantedBook
                        ? DataComponents.STORED_ENCHANTMENTS
                        : DataComponents.ENCHANTMENTS,
                upgraded.toImmutable());
        ((MerchantOfferAccessor) (Object) offer).carpetFga$setResult(upgradedResult);
        return offer;
    }

    private static int effectiveMaximum(ItemStack result, Holder<Enchantment> holder, String mode) {
        int vanillaMaximum = holder.value().definition().maxLevel();
        if ("onlyvanilla".equals(mode) && !result.is(Items.ENCHANTED_BOOK)) {
            return vanillaEquipmentMaximum(result, holder.value(), vanillaMaximum);
        }
        return vanillaMaximum > 1 && "more".equals(mode)
                ? EnchantmentLevelRules.increaseLimit(vanillaMaximum,
                FGASettings.enchantmentLevelLimitIncrease())
                : vanillaMaximum;
    }

    /**
     * Vanilla villager equipment is generated with an enchanting power of 5-19
     * before the normal item enchantability bonus is applied.  Keep the
     * equipment rule aligned with that trade-specific cap rather than using
     * the standalone enchantment maximum (which would incorrectly produce
     * Sharpness V or Efficiency V on normal villager equipment).
     */
    private static int vanillaEquipmentMaximum(ItemStack result, Enchantment enchantment, int vanillaMaximum) {
        //#if MC >= 1.21.2
        //$$ Enchantable component = result.get(DataComponents.ENCHANTABLE);
        //$$ int enchantability = component == null ? 0 : component.value();
        //#else
        int enchantability = Math.max(0, result.getItem().getEnchantmentValue());
        //#endif
        int maximumPower = Math.round(
                (19 + 2 * (enchantability / 4)) * 1.15F);
        for (int level = vanillaMaximum; level >= enchantment.getMinLevel(); level--) {
            if (enchantment.getMinCost(level) <= maximumPower
                    && enchantment.getMaxCost(level) >= 5) {
                return level;
            }
        }
        return enchantment.getMinLevel();
    }

    private static boolean hasEnabledRule() {
        return !"false".equals(FGASettings.villagerOnlyMaxEnchantmentBooks)
                || !"false".equals(FGASettings.villagerOnlyMaxEnchantmentEquipment);
    }
}
//#endif
