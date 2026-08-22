//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.WoodStonecuttingRecipes;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.inventory.StonecutterMenu$2")
abstract class StonecutterResultSlotWoodMixin {
    @Redirect(
            method = "onTake",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/Slot;remove(I)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack carpetFga$consumeWoodInput(Slot inputSlot, int amount) {
        int required = WoodStonecuttingRecipes.isTwoInputRecipe(inputSlot)
                ? WoodStonecuttingRecipes.requiredInputCount(inputSlot)
                : amount;
        return inputSlot.remove(required);
    }
}
//#endif
