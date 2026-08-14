//#if MC >= 1.21 && MC <= 1.21.11
package carpet.fga;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
//#if MC >= 1.21.3
//$$ import net.minecraft.world.item.crafting.SelectableRecipe;
//$$ import net.minecraft.world.item.crafting.StonecutterRecipe;
//#endif

public final class DeepslateStonecuttingRecipes {
    private DeepslateStonecuttingRecipes() {}

    public static boolean isFgaRecipe(RecipeHolder<?> recipe) {
        ResourceLocation id =
                //#if MC >= 1.21.3
                //$$ recipe.id().location();
                //#else
                recipe.id();
                //#endif
        return "carpet-fga-addition".equals(id.getNamespace())
                && id.getPath().endsWith("_from_deepslate_stonecutting");
    }

    //#if MC >= 1.21.3
    //$$ public static SelectableRecipe.SingleInputSet<StonecutterRecipe> filter(
    //$$         SelectableRecipe.SingleInputSet<StonecutterRecipe> recipes) {
    //$$     return new SelectableRecipe.SingleInputSet<>(recipes.entries().stream()
    //$$             .filter(entry -> entry.recipe().recipe()
    //$$                     .map(recipe -> !isFgaRecipe(recipe))
    //$$                     .orElse(true))
    //$$             .toList());
    //$$ }
    //#endif
}
//#endif
