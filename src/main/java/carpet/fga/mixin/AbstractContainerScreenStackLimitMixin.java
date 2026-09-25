//#if MC >= 1.16.5 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.DroppedItemStackLimitConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client side drag preview and remainder use the same item level stack limit
 * that the server side menu no longer uses once a scope is active. Without
 * this the preview would show fewer items per dragged slot than the server
 * actually places, which looks like items appearing or vanishing.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenStackLimitMixin {
    @ModifyExpressionValue(
            method = {
                    //#if MC >= 26.0
                    //$$ "extractSlot",
                    //#else
                    "renderSlot",
                    //#endif
                    "recalculateQuickCraftRemaining"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I")
    )
    private int carpetFga$scopedDragPreview(int original) {
        return DroppedItemStackLimitConfig.effectiveMenuCapacity(original);
    }
}
//#endif
