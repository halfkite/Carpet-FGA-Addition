//#if MC >= 1.16.5 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FullShulkerBoxCraftingManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotFullShulkerMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void carpetFga$validateFullShulkerResult(Player player, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ResultSlot resultSlot) {
            CraftingContainer crafting = ((ResultSlotCraftingAccessor) resultSlot).carpetFga$getCraftSlots();
            if (FullShulkerBoxCraftingManager.isCustomResult(crafting)) {
                cir.setReturnValue(FullShulkerBoxCraftingManager.mayTake(crafting, player));
            }
        }
    }
}
//#endif
