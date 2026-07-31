//#if MC >= 1.20.5
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.RecipeBook;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBook.class)
public abstract class RecipeBookMixin {
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
