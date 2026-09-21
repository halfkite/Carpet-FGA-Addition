//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
//#if MC >= 1.21.5
import net.minecraft.core.component.DataComponents;
//#else
import net.minecraft.world.item.DiggerItem;
//#endif
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

/**
 * Handles Fire Aspect on mining tools and level-based furnace processing of block drops.
 *
 * <p>Upgrade smoke procedure: enable {@code fireAspectOnTools} on an isolated server;
 * summon fake players {@code FGAFireOne}, {@code FGAFireTwo}, and
 * {@code FGAFireFortune}; give them respectively a pickaxe with Fire Aspect I, a
 * pickaxe with Fire Aspect II, and a pickaxe with Fire Aspect plus Fortune; make each
 * fake player mine the prepared test block. The first tool must produce the first
 * smelting result, the second must apply the chained recipe twice, and the third must
 * produce smelted ore with the Fortune-enlarged drop count. Record the item stacks and
 * server log, then repeat the same procedure for every build node listed in
 * {@code scripts/powershell/fire-aspect-tool-smoke-all.ps1}.</p>
 */
public final class FireAspectToolManager {
    private FireAspectToolManager() {
    }

    public static boolean isFireAspect(Enchantment enchantment) {
        return enchantment.description().getContents() instanceof TranslatableContents contents
                && "enchantment.minecraft.fire_aspect".equals(contents.getKey());
    }

    public static boolean isTool(ItemStack stack) {
        //#if MC >= 1.21.5
        //$$ return stack.get(DataComponents.TOOL) != null;
        //#else
        return stack.getItem() instanceof DiggerItem;
        //#endif
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

        Holder<Enchantment> fireAspect =
                //#if MC >= 1.21.3
                //$$ level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                //$$         .getOrThrow(Enchantments.FIRE_ASPECT);
                //#else
                level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                        .getHolderOrThrow(Enchantments.FIRE_ASPECT);
                //#endif
        int fireAspectLevel = EnchantmentHelper.getItemEnchantmentLevel(fireAspect, tool);
        if (fireAspectLevel <= 0) {
            return drops;
        }

        Holder<Enchantment> silkTouch =
                //#if MC >= 1.21.3
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.SILK_TOUCH);
                //#else
                //$$ level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                //$$         .getHolderOrThrow(Enchantments.SILK_TOUCH);
                //#endif
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
                new SingleRecipeInput(drop.copyWithCount(1))
                //#if MC >= 26.1.2
                //$$ );
                //#else
                , level.registryAccess());
                //#endif
        if (output.isEmpty()) return drop;
        return output.copyWithCount(output.getCount() * drop.getCount());
    }
}
//#endif
