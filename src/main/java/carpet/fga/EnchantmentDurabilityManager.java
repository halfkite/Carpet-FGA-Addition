//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.effects.DamageItem;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.item.enchantment.Enchantment;

/** Selectively removes vanilla durability effects from Soul Speed and Thorns. */
public final class EnchantmentDurabilityManager {
    private EnchantmentDurabilityManager() {
    }

    public static boolean suppressDurabilityEffect(Enchantment enchantment) {
        return FGASettings.thornsNoDurability && is(enchantment, "enchantment.minecraft.thorns");
    }

    public static boolean suppressSoulSpeedLocationDurability(
            Enchantment enchantment, EnchantmentLocationBasedEffect effect) {
        return FGASettings.soulSpeedNoDurability
                && effect instanceof DamageItem
                && is(enchantment, "enchantment.minecraft.soul_speed");
    }

    public static boolean suppressSoulSpeedDamage(ServerLevel level, ItemStack stack) {
        if (!FGASettings.soulSpeedNoDurability || stack.isEmpty()) return false;
        Holder<Enchantment> soulSpeed =
                //#if MC >= 1.21.3
                //$$ level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                //$$         .getOrThrow(Enchantments.SOUL_SPEED);
                //#else
                level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                        .getHolderOrThrow(Enchantments.SOUL_SPEED);
                //#endif
        return EnchantmentHelper.getItemEnchantmentLevel(soulSpeed, stack) > 0;
    }

    private static boolean is(Enchantment enchantment, String key) {
        return enchantment.description().getContents() instanceof TranslatableContents contents
                && key.equals(contents.getKey());
    }
}
//#endif
