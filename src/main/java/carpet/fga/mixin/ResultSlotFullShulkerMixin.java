//#if MC >= 1.16.5 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FullShulkerBoxCraftingManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC == 1.16.5
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#endif

@Mixin(ResultSlot.class)
public abstract class ResultSlotFullShulkerMixin {
    @Shadow @Final private CraftingContainer craftSlots;

    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
    private void carpetFga$takeFullShulkerResult(Player player, ItemStack stack,
                                                 //#if MC == 1.16.5
                                                 //$$ CallbackInfoReturnable<ItemStack> cir
                                                 //#else
                                                 CallbackInfo ci
                                                 //#endif
    ) {
        if (FullShulkerBoxCraftingManager.take(craftSlots, player)) {
            //#if MC == 1.16.5
            //$$ cir.setReturnValue(stack);
            //#else
            ci.cancel();
            //#endif
        }
    }
}
//#endif
