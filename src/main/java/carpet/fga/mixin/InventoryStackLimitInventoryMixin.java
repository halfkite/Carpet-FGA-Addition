//#if MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
//#if MC < 1.20.5
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Redirect;
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
//$$     @Redirect(
//$$             method = {
//$$                     "hasRemainingSpaceForItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z",
//$$                     "addResource(ILnet/minecraft/world/item/ItemStack;)I"
//$$             },
//$$             at = @At(
//$$                     value = "INVOKE",
//$$                     target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"
//$$             )
//$$     )
//$$     private int carpetFga$inventoryItemLimit(ItemStack stack) {
//$$         return FGASettings.effectiveInventoryStackLimit(stack);
//$$     }
    //#endif
}
//#endif
