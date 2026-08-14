package carpet.fga.mixin;

import carpet.fga.VehicleStopManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class VehicleStopDismountMixin {
    @Unique
    private ServerPlayer carpetFga$departingDriver;

    @Inject(method = "removePassenger", at = @At("HEAD"))
    private void carpetFga$captureDriver(Entity passenger, CallbackInfo ci) {
        Entity vehicle = (Entity) (Object) this;
        boolean driver = vehicle instanceof AbstractMinecart
                //#if MC >= 1.17
                ? vehicle.getFirstPassenger() == passenger
                //#else
                //$$ ? (!vehicle.getPassengers().isEmpty() && vehicle.getPassengers().get(0) == passenger)
                //#endif
                : vehicle instanceof Boat && vehicle.getControllingPassenger() == passenger;
        carpetFga$departingDriver = driver && passenger instanceof ServerPlayer player
                ? player : null;
    }

    @Inject(method = "removePassenger", at = @At("TAIL"))
    private void carpetFga$stopAfterDriverDismount(Entity passenger, CallbackInfo ci) {
        ServerPlayer driver = carpetFga$departingDriver;
        carpetFga$departingDriver = null;
        if (driver != null && driver == passenger) {
            VehicleStopManager.driverDismounted((Entity) (Object) this, driver);
        }
    }
}
