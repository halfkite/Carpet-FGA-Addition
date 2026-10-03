//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.VehicleJumpAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class LegacyPlayerVehicleInputMixin {
    @Unique private boolean carpetFga$lastJumpInput;

    @Inject(method = "setPlayerInput", at = @At("HEAD"))
    private void carpetFga$jump(float strafe, float forward, boolean jump, boolean shift, CallbackInfo ci) {
        boolean risingEdge = jump && !carpetFga$lastJumpInput;
        carpetFga$lastJumpInput = jump;
        if (!FGASettings.vehicleJump || !risingEdge) return;
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (player.isSpectator()) return;
        Entity vehicle = player.getVehicle();
        if (vehicle != null && vehicle.getFirstPassenger() == player && vehicle instanceof VehicleJumpAccess access) {
            access.carpetFga$tryJump();
        }
    }
}
//#endif
