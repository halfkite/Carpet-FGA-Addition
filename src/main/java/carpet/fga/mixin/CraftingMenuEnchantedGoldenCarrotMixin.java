//#if MC >= 1.21 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.EnchantedGoldenCarrotManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
//#if MC >= 1.21.6
//$$ import net.minecraft.server.level.ServerLevel;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuEnchantedGoldenCarrotMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("RETURN"))
    private static void carpetFga$filterEnchantedGoldenCarrotResult(
            AbstractContainerMenu menu,
            //#if MC < 1.21.6
            Level level,
            //#else
            //$$ ServerLevel level,
            //#endif
            Player player,
            CraftingContainer crafting,
            ResultContainer result,
            RecipeHolder<CraftingRecipe> recipe,
            CallbackInfo ci) {
        EnchantedGoldenCarrotManager.filterCraftingResult(crafting, result, recipe);
    }
}
//#endif
