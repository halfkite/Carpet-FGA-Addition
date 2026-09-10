//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga.mixin;

//#if MC >= 1.21
import carpet.fga.FullShulkerBoxCraftingManager;
//#endif
import carpet.fga.WoodStonecuttingRecipes;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StonecutterMenu.class)
abstract class StonecutterMenuWoodInputMixin {
    @Inject(method = "setupResultSlot", at = @At("RETURN"))
    private void carpetFga$hideUnderfilledWoodResult(CallbackInfo callback) {
        StonecutterMenu menu = (StonecutterMenu) (Object) this;
        WoodStonecuttingRecipes.registerInputSlot(menu.getSlot(0), menu);
        //#if MC >= 1.21
        if (!FullShulkerBoxCraftingManager.stonecutterBoxContent(
                menu.getSlot(0).getItem()).isEmpty()) return;
        //#endif
        int required = WoodStonecuttingRecipes.requiredInputCount(menu);
        if (required > 1 && menu.getSlot(0).getItem().getCount() < required) {
            menu.getSlot(1).set(ItemStack.EMPTY);
        }
    }
}
//#endif
