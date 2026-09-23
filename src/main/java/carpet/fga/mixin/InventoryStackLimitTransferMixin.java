//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla sizes the "put items back" batch with the item level stack limit.
 * Once the inventory scope raises a slot above that limit the batch becomes
 * zero or negative: split(0) returns an empty stack without shrinking the
 * source, so the vanilla loop never makes progress and spins forever, and a
 * negative batch even grows the source stack. Sizing the batch from the
 * destination slot capacity keeps every iteration moving at least one item.
 */
@Mixin(Inventory.class)
public abstract class InventoryStackLimitTransferMixin {
    @ModifyExpressionValue(
            method =
                    //#if MC >= 26.3
                    //$$ "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)V",
                    //#else
                    "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V",
                    //#endif
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I")
    )
    private int carpetFga$scopedReturnBatch(int original, ItemStack stack) {
        // The destination is always a slot of this player inventory.
        return Math.max(original, ((Inventory) (Object) this).getMaxStackSize(stack));
    }
}
//#endif
