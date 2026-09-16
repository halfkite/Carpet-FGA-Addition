//#if MC >= 1.21 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.EnchantedGoldenCarrotManager;
import carpet.fga.FGASettings;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerEnchantedGoldenCarrotMixin {
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void carpetFga$hideDisabledEnchantedGoldenCarrotRecipe(
            RecipeType<T> type,
            I input,
            Level level,
            RecipeHolder<T> previous,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        if (FGASettings.enchantedGoldenCarrot) return;
        Optional<RecipeHolder<T>> result = cir.getReturnValue();
        if (result.isPresent() && EnchantedGoldenCarrotManager.isRecipe(result.get())) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
//#endif
