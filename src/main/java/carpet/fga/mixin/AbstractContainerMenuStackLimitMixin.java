//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.DroppedItemStackLimitConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla click and drag handling sizes the carried stack with the item level
 * stack limit. Once the inventory or container scope raises a slot above it,
 * those reads understate the capacity of the affected slots: a drag can shrink
 * a full slot back down to the item limit while growing the carried stack, and
 * collect, swap and clone clicks stop at the item limit. Sizing them from the
 * active scopes keeps the carried stack consistent with the slots it came from;
 * every concrete placement still clamps against the receiving slot.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuStackLimitMixin {
    @ModifyExpressionValue(
            method = "doClick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I")
    )
    private int carpetFga$scopedCarriedCapacity(int original) {
        return DroppedItemStackLimitConfig.effectiveMenuCapacity(original);
    }

    @ModifyExpressionValue(
            method = "canItemQuickReplace",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I")
    )
    private static int carpetFga$scopedSlotCapacity(int original, Slot slot, ItemStack stack, boolean stackSizeMatters) {
        // The guarded vanilla branch only reads this once the slot is present.
        return slot == null ? original : Math.max(original, slot.getMaxStackSize(stack));
    }

    @ModifyExpressionValue(
            method = "getQuickCraftPlaceCount",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I")
    )
    private static int carpetFga$scopedQuickCraftCount(int original) {
        return DroppedItemStackLimitConfig.effectiveMenuCapacity(original);
    }
}
//#endif
