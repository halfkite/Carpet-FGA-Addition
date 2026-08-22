//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga;

import net.minecraft.resources.ResourceLocation;
//#if MC >= 1.20.2
import net.minecraft.world.item.crafting.RecipeHolder;
//#else
//$$ import net.minecraft.world.item.crafting.Recipe;
//#endif
//#if MC >= 1.21.3
//$$ import net.minecraft.world.item.crafting.SelectableRecipe;
//$$ import net.minecraft.world.item.crafting.StonecutterRecipe;
//#endif

public final class DeepslateStonecuttingRecipes {
    private DeepslateStonecuttingRecipes() {}

    public static boolean isFgaRecipe(Object recipe) {
        ResourceLocation id = recipeId(recipe);
        if (id == null) return false;
        return "carpet-fga-addition".equals(id.getNamespace())
                && (id.getPath().endsWith("_from_deepslate_stonecutting")
                    //#if MC >= 1.20.1 && MC <= 1.21.1
                    || WoodStonecuttingRecipes.isWoodRecipe(id)
                    //#elseif MC >= 1.21.4 && MC <= 26.2
                    || WoodStonecuttingRecipes.isWoodRecipe(id)
                    //#endif
                );
    }

    private static ResourceLocation recipeId(Object recipe) {
        if (recipe == null) return null;
                //#if MC >= 1.21.3
                //$$ return ((RecipeHolder<?>) recipe).id().location();
                //#elseif MC >= 1.20.2
                return ((RecipeHolder<?>) recipe).id();
                //#else
                //$$ return ((Recipe<?>) recipe).getId();
                //#endif
    }

    public static boolean isDisabledFgaRecipe(Object recipe) {
        if (!isFgaRecipe(recipe)) return false;
        ResourceLocation id = recipeId(recipe);
        if (id.getPath().endsWith("_from_deepslate_stonecutting")) {
            return !FGASettings.deepslateStonecuttingRecipes;
        }
        //#if MC >= 1.20.1 && MC <= 1.21.1
        if (WoodStonecuttingRecipes.isWoodRecipe(id)) {
            return !FGASettings.woodStonecuttingRecipes;
        }
        //#elseif MC >= 1.21.4 && MC <= 26.2
        if (WoodStonecuttingRecipes.isWoodRecipe(id)) {
            return !FGASettings.woodStonecuttingRecipes;
        }
        //#endif
        return false;
    }

    //#if MC >= 1.21.3
    //$$ public static SelectableRecipe.SingleInputSet<StonecutterRecipe> filter(
    //$$         SelectableRecipe.SingleInputSet<StonecutterRecipe> recipes) {
    //$$     return new SelectableRecipe.SingleInputSet<>(recipes.entries().stream()
            //$$             .filter(entry -> entry.recipe().recipe()
            //$$                     .map(recipe -> !isDisabledFgaRecipe(recipe))
    //$$                     .orElse(true))
    //$$             .toList());
    //$$ }
    //#endif
}
//#endif
