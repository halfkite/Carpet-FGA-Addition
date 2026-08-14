//#if MC >= 1.16.5 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FullShulkerBoxCraftingManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
//#if MC >= 1.20.4
import net.minecraft.world.item.crafting.RecipeHolder;
//#endif
//#if MC >= 1.21.3
//$$ import net.minecraft.server.level.ServerLevel;
//#else
import net.minecraft.world.level.Level;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuFullShulkerMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("RETURN"))
    private static void carpetFga$updateFullShulkerResult(
                                                          //#if MC == 1.16.5
                                                          //$$ int containerId,
                                                          //#else
                                                          AbstractContainerMenu menu,
                                                          //#endif
                                                          //#if MC >= 1.21.3
                                                          //$$ ServerLevel level,
                                                          //#else
                                                          Level level,
                                                          //#endif
                                                          Player player, CraftingContainer crafting,
                                                          ResultContainer result,
                                                          //#if MC >= 1.21
                                                          RecipeHolder<CraftingRecipe> recipe,
                                                          //#endif
                                                          CallbackInfo ci) {
        //#if MC == 1.16.5
        //$$ AbstractContainerMenu menu = player.containerMenu;
        //#endif
        FullShulkerBoxCraftingManager.updateResult(menu, level, player, crafting, result);
    }
}
//#endif
