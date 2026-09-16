//#if MC >= 1.21 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.EnchantedGoldenCarrotManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResultSlot.class)
public abstract class ResultSlotEnchantedGoldenCarrotMixin {
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void carpetFga$blockDisabledEnchantedGoldenCarrotRemoval(
            int amount, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stack = ((ResultSlot) (Object) this).getItem();
        if (EnchantedGoldenCarrotManager.blockResultTake(stack)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
    private void carpetFga$blockDisabledEnchantedGoldenCarrot(
            Player player, ItemStack stack, CallbackInfo ci) {
        if (EnchantedGoldenCarrotManager.blockResultTake(stack)) {
            ci.cancel();
        }
    }
}
//#endif
