//#if MC == 1.20.1 || MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.MinecartFeatureManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Minecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecart.class)
public abstract class MinecartChainInteractionMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void carpetFga$toggleChain(Player player, InteractionHand hand,
                                       CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = MinecartFeatureManager.interactChain((Minecart) (Object) this, player, hand);
        if (result != null) cir.setReturnValue(result);
    }
}
//#endif
