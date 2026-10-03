//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
//#if MC >= 26.1
//$$ import net.minecraft.world.entity.vehicle.minecart.Minecart;
//$$ import net.minecraft.world.phys.Vec3;
//#else
import net.minecraft.world.entity.vehicle.Minecart;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecart.class)
public abstract class PlayerMinecartInteractionMixin {
//#if MC >= 26.1
//$$     @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
//$$     private void carpetFga$board(Player player, InteractionHand hand, Vec3 hit,
//#else
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void carpetFga$board(Player player, InteractionHand hand,
//#endif
                                CallbackInfoReturnable<InteractionResult> cir) {
        Minecart cart = (Minecart) (Object) this;
        if ("false".equals(FGASettings.playerVehicleCapacity) || !cart.isVehicle() || player.isSecondaryUseActive()) return;
        if (cart.level().isClientSide()) cir.setReturnValue(InteractionResult.CONSUME);
//#if MC >= 26.1
//$$         else cir.setReturnValue(player.startRiding(cart) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS);
//#else
        else cir.setReturnValue(player.startRiding(cart) ? InteractionResult.SUCCESS : InteractionResult.PASS);
//#endif
    }
}
//#endif
