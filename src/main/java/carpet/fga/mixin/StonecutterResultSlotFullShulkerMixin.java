//#if MC >= 1.21
package carpet.fga.mixin;

import carpet.fga.FullShulkerBoxCraftingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
//#if MC >= 1.21.3
//$$ import net.minecraft.world.item.crafting.SelectableRecipe;
//#endif
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "net.minecraft.world.inventory.StonecutterMenu$2")
public abstract class StonecutterResultSlotFullShulkerMixin {
    @Shadow @Final private ContainerLevelAccess val$access;

    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
    private void carpetFga$fullBoxTake(Player player, ItemStack stack, CallbackInfo ci) {
        Slot resultSlot = (Slot) (Object) this;
        StonecutterMenu menu = FullShulkerBoxCraftingManager.stonecutterMenuForResult(resultSlot);
        if (menu == null) return;
        int index = menu.getSelectedRecipeIndex();
        RecipeHolder<StonecutterRecipe> recipe;
        //#if MC < 1.21.3
        List<RecipeHolder<StonecutterRecipe>> recipes = ((StonecutterMenuAccessor) menu).carpetFga$getRecipes();
        recipe = index >= 0 && recipes != null && index < recipes.size() ? recipes.get(index) : null;
        //#else
        //$$ List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> entries =
        //$$         menu.getVisibleRecipes().entries();
        //$$ recipe = index >= 0 && index < entries.size()
        //$$         ? entries.get(index).recipe().recipe().orElse(null) : null;
        //#endif
        FullShulkerBoxCraftingManager.StonecutterTakeResult result =
                FullShulkerBoxCraftingManager.takeStonecutterResult(resultSlot, player, recipe);
        if (result == FullShulkerBoxCraftingManager.StonecutterTakeResult.NONE) return;
        if (result == FullShulkerBoxCraftingManager.StonecutterTakeResult.HANDLED) {
            this.val$access.execute((Level level, BlockPos pos) ->
                    level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT,
                            SoundSource.BLOCKS, 1.0F, 1.0F));
        }
        // HANDLED and BLOCKED both cancel vanilla: its remove(1) would otherwise consume a whole box.
        ci.cancel();
    }
}
//#endif
