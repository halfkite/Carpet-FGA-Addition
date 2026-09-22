//#if MC >= 1.20.1 && MC <= 26.3
package carpet.fga.mixin;

//#if MC >= 1.21
import carpet.fga.FullShulkerBoxCraftingManager;
//#endif
import carpet.fga.WoodStonecuttingRecipes;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
//#if MC >= 1.21
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StonecutterMenu.class)
abstract class StonecutterMenuWoodInputMixin {
    //#if MC >= 1.21
    @Shadow @Final private Level level;
    //#endif

    @Inject(method = "setupResultSlot", at = @At("RETURN"))
    private void carpetFga$hideUnderfilledWoodResult(CallbackInfo callback) {
        StonecutterMenu menu = (StonecutterMenu) (Object) this;
        WoodStonecuttingRecipes.registerInputSlot(menu.getSlot(0), menu);
        //#if MC >= 1.21
        if (!FullShulkerBoxCraftingManager.stonecutterBoxContent(
                this.level, menu.getSlot(0).getItem()).isEmpty()) return;
        //#endif
        int required = WoodStonecuttingRecipes.requiredInputCount(menu);
        if (required > 1 && menu.getSlot(0).getItem().getCount() < required) {
            menu.getSlot(1).set(ItemStack.EMPTY);
        }
    }
}
//#endif
