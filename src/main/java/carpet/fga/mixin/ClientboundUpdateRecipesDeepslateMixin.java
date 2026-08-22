//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.DeepslateStonecuttingRecipes;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
//#if MC >= 1.21.3
//$$ import net.minecraft.world.item.crafting.SelectableRecipe;
//$$ import net.minecraft.world.item.crafting.StonecutterRecipe;
//#endif
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientboundUpdateRecipesPacket.class)
public abstract class ClientboundUpdateRecipesDeepslateMixin {
    @Shadow
    @Final
    @Mutable
    //#if MC >= 1.21.3
    //$$ private SelectableRecipe.SingleInputSet<StonecutterRecipe> stonecutterRecipes;
    //#else
    private List<?> recipes;
    //#endif

    @Inject(method = "<init>", at = @At("RETURN"))
    private void carpetFga$filterDisabledRecipes(CallbackInfo callback) {
        //#if MC >= 1.21.3
        //$$ stonecutterRecipes = DeepslateStonecuttingRecipes.filter(stonecutterRecipes);
        //#else
        recipes = recipes.stream().filter(recipe -> !DeepslateStonecuttingRecipes.isDisabledFgaRecipe(recipe)).toList();
        //#endif
    }
}
//#endif
