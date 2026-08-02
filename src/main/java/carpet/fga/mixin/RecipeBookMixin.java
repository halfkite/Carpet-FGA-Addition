//#if MC >= 1.16.5
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.RecipeBook;
//#if MC >= 1.20.5
import net.minecraft.world.item.crafting.RecipeHolder;
//#endif
//#if MC < 1.20.5
//$$ import net.minecraft.world.item.crafting.Recipe;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBook.class)
public abstract class RecipeBookMixin {
    //#if MC >= 1.20.5
    @Inject(
            method = "contains(Lnet/minecraft/world/item/crafting/RecipeHolder;)Z",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void carpetFga$allowEveryRecipe(RecipeHolder<?> recipe, CallbackInfoReturnable<Boolean> cir) {
        if (FGASettings.recipeBookAlwaysUnlocked && recipe != null) {
            cir.setReturnValue(true);
        }
    }
    //#else
    //$$ @Inject(method = "contains(Lnet/minecraft/world/item/crafting/Recipe;)Z", at = @At("RETURN"), cancellable = true, require = 0)
    //$$ private void carpetFga$allowEveryRecipe(Recipe<?> recipe, CallbackInfoReturnable<Boolean> cir) {
    //$$     if (FGASettings.recipeBookAlwaysUnlocked && recipe != null) cir.setReturnValue(true);
    //$$ }
    //#endif

    @Inject(
            method = "contains(Lnet/minecraft/resources/ResourceLocation;)Z",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void carpetFga$allowEveryRecipeId(ResourceLocation recipeId, CallbackInfoReturnable<Boolean> cir) {
        if (FGASettings.recipeBookAlwaysUnlocked && recipeId != null) {
            cir.setReturnValue(true);
        }
    }
}
//#endif
