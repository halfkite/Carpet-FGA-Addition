//#if MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
//#if MC < 1.20.5
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#endif

/** Extends pickup and automatic inventory merge capacity on the server. */
@Mixin(Inventory.class)
public abstract class InventoryStackLimitInventoryMixin {
    public int
            //#if MC >= 1.20.5
            getMaxStackSize(ItemStack stack) {
            //#else
            //$$ getMaxStackSize() {
            //#endif
        //#if MC >= 1.20.5
        return FGASettings.effectiveInventoryStackLimit(stack);
        //#else
        //$$ return Math.max(64, carpet.fga.DroppedItemStackLimitConfig.snapshot().inventoryLimit());
        //#endif
    }

    //#if MC < 1.20.5
//$$     @Inject(
//$$             method = "hasRemainingSpaceForItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z",
//$$             at = @At("HEAD"),
//$$             cancellable = true
//$$     )
//$$     private void carpetFga$hasConfiguredSpace(ItemStack existing, ItemStack incoming,
//$$                                                CallbackInfoReturnable<Boolean> cir) {
//$$         int configured = FGASettings.effectiveInventoryStackLimit(existing);
//$$         if (configured <= existing.getMaxStackSize()) return;
//$$         cir.setReturnValue(!existing.isEmpty()
//$$                 && ItemStack.isSameItemSameTags(existing, incoming)
//$$                 && existing.isStackable()
//$$                 && existing.getCount() < configured);
//$$     }

//$$     @Inject(
//$$             method = "addResource(ILnet/minecraft/world/item/ItemStack;)I",
//$$             at = @At("HEAD"),
//$$             cancellable = true
//$$     )
//$$     private void carpetFga$addWithConfiguredLimit(int slot, ItemStack incoming,
//$$                                                   CallbackInfoReturnable<Integer> cir) {
//$$         int configured = FGASettings.effectiveInventoryStackLimit(incoming);
//$$         if (configured <= incoming.getMaxStackSize()) return;
//$$         Inventory inventory = (Inventory) (Object) this;
//$$         ItemStack existing = inventory.getItem(slot);
//$$         if (existing.isEmpty()) {
//$$             existing = incoming.copy();
//$$             existing.setCount(0);
//$$             inventory.setItem(slot, existing);
//$$         }
//$$         int moved = Math.min(incoming.getCount(), Math.max(0, configured - existing.getCount()));
//$$         if (moved > 0) {
//$$             existing.grow(moved);
//$$             existing.setPopTime(5);
//$$         }
//$$         cir.setReturnValue(incoming.getCount() - moved);
//$$     }
    //#endif
}
//#endif
