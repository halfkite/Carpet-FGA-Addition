//#if MC == 1.21.1
package carpet.fga;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Handles Fire Aspect on mining tools and level-based furnace processing of block drops. */
public final class FireAspectToolManager {
    private FireAspectToolManager() {
    }

    public static boolean isFireAspect(Enchantment enchantment) {
        return enchantment.description().getContents() instanceof TranslatableContents contents
                && "enchantment.minecraft.fire_aspect".equals(contents.getKey());
    }

    public static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof DiggerItem;
    }

    public static boolean allowsFireAspect(Enchantment enchantment, ItemStack stack) {
        return FGASettings.fireAspectOnTools && isFireAspect(enchantment) && isTool(stack);
    }

    public static boolean blocksEnchantmentTableFireAspect(Enchantment enchantment, ItemStack stack) {
        return allowsFireAspect(enchantment, stack);
    }

    public static boolean conflictsWithSilkTouch(Holder<Enchantment> first, Holder<Enchantment> second) {
        if (!FGASettings.fireAspectOnTools) return false;
        return (first.is(Enchantments.FIRE_ASPECT) && second.is(Enchantments.SILK_TOUCH))
                || (first.is(Enchantments.SILK_TOUCH) && second.is(Enchantments.FIRE_ASPECT));
    }

    public static List<ItemStack> smeltDrops(ServerLevel level, Entity breaker, ItemStack tool,
                                              List<ItemStack> drops) {
        if (!FGASettings.fireAspectOnTools || breaker == null || !isTool(tool)) {
            return drops;
        }

        Holder<Enchantment> fireAspect = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.FIRE_ASPECT);
        int fireAspectLevel = EnchantmentHelper.getItemEnchantmentLevel(fireAspect, tool);
        if (fireAspectLevel <= 0) {
            return drops;
        }

        Holder<Enchantment> silkTouch = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.SILK_TOUCH);
        if (EnchantmentHelper.getItemEnchantmentLevel(silkTouch, tool) > 0) {
            return drops;
        }

        List<ItemStack> result = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            ItemStack processed = drop;
            for (int pass = 0; pass < fireAspectLevel; pass++) {
                ItemStack next = smeltOnce(level, processed);
                if (next == processed) {
                    break;
                }
                processed = next;
            }
            result.add(processed);
        }
        return result;
    }

    private static ItemStack smeltOnce(ServerLevel level, ItemStack drop) {
        if (drop.isEmpty()) return drop;

        Optional<RecipeHolder<SmeltingRecipe>> recipe = level.getRecipeManager().getRecipeFor(
                RecipeType.SMELTING,
                new SingleRecipeInput(drop.copyWithCount(1)),
                level);
        if (recipe.isEmpty()) return drop;

        ItemStack output = recipe.get().value().assemble(
                new SingleRecipeInput(drop.copyWithCount(1)),
                level.registryAccess());
        if (output.isEmpty()) return drop;
        return output.copyWithCount(output.getCount() * drop.getCount());
    }
}
//#endif
