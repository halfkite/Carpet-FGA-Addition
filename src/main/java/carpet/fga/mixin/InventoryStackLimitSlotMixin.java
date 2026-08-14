//#if MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class InventoryStackLimitSlotMixin {
    @Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"), cancellable = true)
    private void carpetFga$effectiveStackLimit(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (stack != null && !stack.isEmpty()) {
            int vanillaItemLimit = stack.getMaxStackSize();
            Slot self = (Slot) (Object) this;
            int configuredLimit = self.container instanceof Inventory
                    ? FGASettings.effectiveInventoryStackLimit(stack)
                    : FGASettings.effectiveContainerStackLimit(stack);
            // Preserve one-item/special-purpose slots while expanding ordinary stack slots
            if (configuredLimit > vanillaItemLimit && cir.getReturnValueI() >= vanillaItemLimit) {
                cir.setReturnValue(configuredLimit);
            }
        }
    }
}
//#endif
