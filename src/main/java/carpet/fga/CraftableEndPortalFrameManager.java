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

public final class CraftableEndPortalFrameManager {
    private static final String RECIPE_NAMESPACE = "carpet-fga-addition";
    private static final String RECIPE_PATH = "craftable_end_portal_frame";
    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath(
            RECIPE_NAMESPACE, RECIPE_PATH);

    private CraftableEndPortalFrameManager() {}

    public static boolean isRecipe(Object recipe) {
        return RECIPE_ID.equals(recipeId(recipe));
    }

    public static boolean isDisabledRecipe(Object recipe) {
        return isRecipe(recipe) && !FGASettings.craftableEndPortalFrame;
    }

    public static void filterCraftingResult(ResultContainer result, Object recipe) {
        if (isDisabledRecipe(recipe)) {
            result.setItem(0, ItemStack.EMPTY);
            result.setRecipeUsed(null);
        }
    }

    public static boolean blockResultTake(ItemStack stack, Slot slot) {
        if (FGASettings.craftableEndPortalFrame || stack == null || stack.isEmpty()
                || stack.getItem() != Items.END_PORTAL_FRAME
                || !(slot.container instanceof ResultContainer result)) {
            return false;
        }
        return isRecipe(result.getRecipeUsed());
    }

    public static boolean isRecipeId(ResourceLocation id) {
        return RECIPE_ID.equals(id);
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
