//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//#if MC >= 1.20.2
import net.minecraft.world.item.crafting.RecipeHolder;
//#endif

public final class CraftableBarrierManager {
    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath(
            "carpet-fga-addition", "craftable_barrier");

    private CraftableBarrierManager() {}

    public static boolean isRecipe(Object recipe) {
        return RECIPE_ID.equals(recipeId(recipe));
    }

    public static boolean isDisabledRecipe(Object recipe) {
        return isRecipe(recipe) && !FGASettings.craftableBarrier;
    }

    public static void filterCraftingResult(ResultContainer result, Object recipe) {
        if (isDisabledRecipe(recipe)) {
            result.setItem(0, ItemStack.EMPTY);
            result.setRecipeUsed(null);
        }
    }

    public static boolean blockResultTake(ItemStack stack, Slot slot) {
        if (FGASettings.craftableBarrier || stack == null || stack.isEmpty()
                || stack.getItem() != Items.BARRIER
                || !(slot.container instanceof ResultContainer result)) {
            return false;
        }
        return isRecipe(result.getRecipeUsed());
    }

    public static ResourceLocation recipeId(Object recipe) {
        if (recipe == null) return null;
        //#if MC >= 1.20.2
        //#if MC < 1.21.3
        return recipe instanceof RecipeHolder<?> holder ? holder.id() : null;
        //#else
        //#if MC < 26.3
        //$$ return recipe instanceof RecipeHolder<?> holder ? holder.id().location() : null;
        //#else
        //$$ return recipe instanceof RecipeHolder<?> holder ? holder.id().identifier() : null;
        //#endif
        //#endif
        //#else
        //$$ return recipe instanceof Recipe<?> value ? value.getId() : null;
        //#endif
    }
}
//#endif
