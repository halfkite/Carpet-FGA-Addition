//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.VehicleJumpAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecart.class)
public abstract class LegacyPlayerMinecartJumpMixin implements VehicleJumpAccess {
    @Shadow private boolean onRails;
    @Shadow protected abstract void comeOffTrack();
    @Shadow protected abstract void moveAlongTrack(BlockPos pos, BlockState state);
    @Unique private int carpetFga$flightTicks;
    @Unique private long carpetFga$lastJump = -20;

    @Override
    public boolean carpetFga$tryJump() {
        AbstractMinecart cart = (AbstractMinecart) (Object) this;
        if (!FGASettings.vehicleJump || !(cart instanceof Minecart)
                || !(cart.getFirstPassenger() instanceof ServerPlayer)
                || carpetFga$flightTicks > 0 || cart.level().getGameTime() - carpetFga$lastJump < 10
                || !cart.onGround() && !cart.isOnRails()) return false;
        carpetFga$lastJump = cart.level().getGameTime();
        carpetFga$flightTicks = 1;
        cart.setOnGround(false);
        cart.setDeltaMovement(cart.getDeltaMovement().with(net.minecraft.core.Direction.Axis.Y, 0.42));
        return true;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/AbstractMinecart;moveAlongTrack(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void carpetFga$flight(AbstractMinecart cart, BlockPos pos, BlockState state) {
        if (carpetFga$flightTicks == 0 || cart.level().isClientSide()) {
            moveAlongTrack(pos, state);
            return;
        }
        onRails = false;
        comeOffTrack();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void carpetFga$finishFlight(CallbackInfo ci) {
        AbstractMinecart cart = (AbstractMinecart) (Object) this;
        if (carpetFga$flightTicks > 0 && (cart.onGround() || ++carpetFga$flightTicks > 40)) {
            carpetFga$flightTicks = 0;
        }
    }
}
//#endif
