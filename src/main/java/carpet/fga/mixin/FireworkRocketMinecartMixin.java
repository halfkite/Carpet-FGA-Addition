//#if MC == 1.20.1 || MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.MinecartFeatureManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FireworkRocketItem.class)
public abstract class FireworkRocketMinecartMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void carpetFga$boostRiddenMinecart(Level level, Player player, InteractionHand hand,
                                               CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (MinecartFeatureManager.boost(player, hand)) {
            cir.setReturnValue(InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide));
        }
    }
}
//#endif
