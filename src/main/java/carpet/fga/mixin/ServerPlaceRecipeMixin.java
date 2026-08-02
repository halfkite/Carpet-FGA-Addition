//#if MC >= 1.16.5
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.stats.ServerRecipeBook;
//#if MC >= 1.20.5
import net.minecraft.world.item.crafting.RecipeHolder;
//#endif
//#if MC < 1.20.5
//$$ import net.minecraft.world.item.crafting.Recipe;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlaceRecipe.class)
public abstract class ServerPlaceRecipeMixin {
    //#if MC >= 1.20.5
    @WrapOperation(
            method = "recipeClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/stats/ServerRecipeBook;contains(Lnet/minecraft/world/item/crafting/RecipeHolder;)Z"
            ),
            require = 0
    )
    private boolean carpetFga$allowRecipePlacement(ServerRecipeBook recipeBook, RecipeHolder<?> recipe,
                                                    Operation<Boolean> original) {
        return FGASettings.recipeBookAlwaysUnlocked || original.call(recipeBook, recipe);
    }
    //#else
    //$$ @WrapOperation(method = "recipeClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/stats/ServerRecipeBook;contains(Lnet/minecraft/world/item/crafting/Recipe;)Z"), require = 0)
    //$$ private boolean carpetFga$allowRecipePlacement(ServerRecipeBook recipeBook, Recipe<?> recipe, Operation<Boolean> original) {
    //$$     return FGASettings.recipeBookAlwaysUnlocked || original.call(recipeBook, recipe);
    //$$ }
    //#endif
}
//#endif
