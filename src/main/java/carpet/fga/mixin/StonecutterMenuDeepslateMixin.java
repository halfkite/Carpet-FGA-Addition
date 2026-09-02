//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.DeepslateStonecuttingRecipes;
import net.minecraft.world.inventory.StonecutterMenu;
//#if MC >= 1.21.3
//$$ import net.minecraft.world.item.crafting.SelectableRecipe;
//#endif
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(StonecutterMenu.class)
public abstract class StonecutterMenuDeepslateMixin {
    @Shadow
    //#if MC >= 1.21.3
    //$$ private SelectableRecipe.SingleInputSet<StonecutterRecipe> recipesForInput;
    //#else
    private List<?> recipes;
    //#endif

    @Inject(method = "setupRecipeList", at = @At("RETURN"))
    private void carpetFga$filterDeepslateRecipes(CallbackInfo callback) {
        //#if MC >= 1.21.3
        //$$ recipesForInput = DeepslateStonecuttingRecipes.filter(recipesForInput);
        //#else
        recipes.removeIf(DeepslateStonecuttingRecipes::isDisabledFgaRecipe);
        //#endif
    }
}
//#endif
