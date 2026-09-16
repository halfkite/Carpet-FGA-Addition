//#if MC >= 1.21 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.EnchantedGoldenCarrotManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
//#if MC == 1.21.1
import net.minecraft.world.InteractionResultHolder;
//#else
//$$ import net.minecraft.world.InteractionResult;
//#endif
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemEnchantedGoldenCarrotMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void carpetFga$useAsNormalGoldenCarrotWhenDisabled(
            Level level,
            Player player,
            InteractionHand hand,
            //#if MC == 1.21.1
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
            //#else
            //$$ CallbackInfoReturnable<InteractionResult> cir) {
            //#endif
        ItemStack stack = player.getItemInHand(hand);
        if (EnchantedGoldenCarrotManager.blockFoodUse(stack, player)) {
            //#if MC == 1.21.1
            cir.setReturnValue(InteractionResultHolder.fail(stack));
            //#else
            //$$ cir.setReturnValue(InteractionResult.FAIL);
            //#endif
        }
    }

}
//#endif
