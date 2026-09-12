//#if MC >= 26.1
//$$ package carpet.fga;

//$$ import carpet.fga.mixin.MerchantOfferAccessor;
//$$ import net.minecraft.core.Holder;
//$$ import net.minecraft.core.component.DataComponents;
//$$ import net.minecraft.tags.EnchantmentTags;
//$$ import net.minecraft.world.entity.npc.villager.Villager;
//$$ import net.minecraft.world.entity.npc.villager.VillagerProfession;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.item.Items;
//$$ import net.minecraft.world.item.enchantment.Enchantment;
//$$ import net.minecraft.world.item.enchantment.Enchantable;
//$$ import net.minecraft.world.item.enchantment.ItemEnchantments;
//$$ import net.minecraft.world.item.trading.ItemCost;
//$$ import net.minecraft.world.item.trading.MerchantOffer;

//$$ import java.util.IdentityHashMap;
//$$ import java.util.Map;

//$$ /** Applies the villager trade rules to the data-driven trade system introduced in 26.1. */
//$$ public final class ModernVillagerTradeManager {
//$$     private static final Map<Villager, Boolean> NEW_PROFESSION = new IdentityHashMap<>();

//$$     private ModernVillagerTradeManager() {
//$$     }

//$$     public static void professionChanged(Villager villager, Holder<VillagerProfession> oldProfession,
//$$                                           Holder<VillagerProfession> newProfession) {
//$$         if (oldProfession != null && oldProfession.is(VillagerProfession.NONE)
//$$                 && !newProfession.is(VillagerProfession.NONE)) {
//$$             NEW_PROFESSION.put(villager, true);
//$$         } else if (newProfession.is(VillagerProfession.NONE)) {
//$$             NEW_PROFESSION.remove(villager);
//$$         }
//$$     }

//$$     public static boolean isNewProfession(Villager villager) {
//$$         return NEW_PROFESSION.getOrDefault(villager, false);
//$$     }

//$$     public static void clear(Villager villager) {
//$$         NEW_PROFESSION.remove(villager);
//$$     }

//$$     public static void process(Villager villager, MerchantOffer offer) {
//$$         if (!isNewProfession(villager) || offer == null) return;
//$$         promoteEnchantments(offer);
//$$         applyEnchantedBookMinimum(offer);
//$$     }

//$$     private static void promoteEnchantments(MerchantOffer offer) {
//$$         ItemStack result = offer.getResult();
//$$         boolean book = result.is(Items.ENCHANTED_BOOK);
//$$         String mode = book
//$$                 ? FGASettings.villagerOnlyMaxEnchantmentBooks
//$$                 : FGASettings.villagerOnlyMaxEnchantmentEquipment;
//$$         if ("false".equals(mode)) return;
//$$         ItemEnchantments enchantments = result.get(book
//$$                 ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS);
//$$         if (enchantments == null || enchantments.isEmpty()) return;
//$$         ItemEnchantments.Mutable upgraded = new ItemEnchantments.Mutable(enchantments);
//$$         for (Holder<Enchantment> holder : enchantments.keySet()) {
//$$             int maximum = holder.value().definition().maxLevel();
//$$             if ("onlyvanilla".equals(mode) && !book) maximum = vanillaEquipmentMaximum(result, holder.value(), maximum);
//$$             if ("more".equals(mode) && maximum > 1) {
//$$                 maximum = EnchantmentLevelRules.increaseLimit(maximum, FGASettings.enchantmentLevelLimitIncrease());
//$$             }
//$$             upgraded.set(holder, maximum);
//$$         }
//$$         ItemStack upgradedResult = result.copy();
//$$         upgradedResult.set(book ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS,
//$$                 upgraded.toImmutable());
//$$         ((MerchantOfferAccessor) (Object) offer).carpetFga$setResult(upgradedResult);
//$$     }

//$$     private static int vanillaEquipmentMaximum(ItemStack result, Enchantment enchantment, int vanillaMaximum) {
//$$         Enchantable component = result.get(DataComponents.ENCHANTABLE);
//$$         int enchantability = component == null ? 0 : component.value();
//$$         int maximumPower = Math.round((19 + 2 * (enchantability / 4)) * 1.15F);
//$$         for (int level = vanillaMaximum; level >= enchantment.getMinLevel(); level--) {
//$$             if (enchantment.getMinCost(level) <= maximumPower && enchantment.getMaxCost(level) >= 5) return level;
//$$         }
//$$         return enchantment.getMinLevel();
//$$     }

//$$     private static void applyEnchantedBookMinimum(MerchantOffer offer) {
//$$         if (!"true".equals(FGASettings.villagerMinimumTradePrice)
//$$                 || !offer.getResult().is(Items.ENCHANTED_BOOK)) return;
//$$         ItemEnchantments enchantments = offer.getResult().get(DataComponents.STORED_ENCHANTMENTS);
//$$         if (enchantments == null || enchantments.isEmpty()) return;
//$$         int level = 1;
//$$         boolean treasure = false;
//$$         for (Holder<Enchantment> holder : enchantments.keySet()) {
//$$             level = Math.max(level, enchantments.getLevel(holder));
//$$             treasure |= holder.is(EnchantmentTags.TREASURE);
//$$         }
//$$         int minimum = Math.min(64, 2 + 3 * level);
//$$         if (treasure) minimum = Math.min(64, minimum * 2);
//$$         if (offer.getItemCostA().count() > minimum) {
//$$             ((MerchantOfferAccessor) (Object) offer).carpetFga$setBaseCostA(
//$$                     new ItemCost(offer.getItemCostA().item(), minimum, offer.getItemCostA().components()));
//$$         }
//$$     }
//$$ }
//#endif
