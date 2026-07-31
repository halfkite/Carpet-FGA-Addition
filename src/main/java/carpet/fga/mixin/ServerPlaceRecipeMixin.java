//#if MC >= 1.20.5
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlaceRecipe.class)
public abstract class ServerPlaceRecipeMixin {
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
}
//#endif
