//#if MC >= 1.16.5
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
//#if MC >= 1.21.3
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
//#endif
//#if MC >= 1.20.5
import net.minecraft.world.item.crafting.RecipeHolder;
//#endif
//#if MC < 1.20.5
//$$ import net.minecraft.world.item.crafting.Recipe;
//#endif
//#if MC < 1.21.3
//$$ import net.minecraft.world.item.crafting.RecipeManager;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
//#if MC >= 1.21.3
import java.util.function.Predicate;
//#endif

@Mixin(ServerRecipeBook.class)
public abstract class ServerRecipeBookMixin {
    //#if MC >= 1.20.5
    @Inject(method = "addRecipes", at = @At("HEAD"), cancellable = true, require = 0)
    private void carpetFga$skipRecipeUnlocks(Collection<RecipeHolder<?>> recipes, ServerPlayer player,
                                              CallbackInfoReturnable<Integer> cir) {
        if (FGASettings.recipeBookAlwaysUnlocked) {
            cir.setReturnValue(0);
        }
    }
    //#else
    //$$ @Inject(method = "addRecipes", at = @At("HEAD"), cancellable = true, require = 0)
    //$$ private void carpetFga$skipRecipeUnlocks(Collection<Recipe<?>> recipes, ServerPlayer player, CallbackInfoReturnable<Integer> cir) {
    //$$     if (FGASettings.recipeBookAlwaysUnlocked) cir.setReturnValue(0);
    //$$ }
    //#endif

    @Inject(method = "toNbt", at = @At("HEAD"), cancellable = true, require = 0)
    private void carpetFga$skipRecipeBookSave(CallbackInfoReturnable<CompoundTag> cir) {
        if (FGASettings.recipeBookAlwaysUnlocked) {
            cir.setReturnValue(new CompoundTag());
        }
    }

    @Inject(method = "fromNbt", at = @At("HEAD"), cancellable = true, require = 0)
    //#if MC >= 1.21.3
    private void carpetFga$skipRecipeBookLoad(CompoundTag tag, Predicate<ResourceKey<Recipe<?>>> recipePredicate,
                                               CallbackInfo ci) {
    //#else
    //$$ private void carpetFga$skipRecipeBookLoad(CompoundTag tag, RecipeManager recipeManager, CallbackInfo ci) {
    //#endif
        if (FGASettings.recipeBookAlwaysUnlocked) {
            ci.cancel();
        }
    }
}
//#endif
