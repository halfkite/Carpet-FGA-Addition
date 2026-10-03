//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.VehicleJumpAccess;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
//#if MC >= 26.1
//$$ import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
//#else
import net.minecraft.world.entity.vehicle.Boat;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

//#if MC >= 26.1
//$$ @Mixin(AbstractBoat.class)
//$$ public abstract class PlayerBoatFeaturesMixin implements VehicleJumpAccess {
//#else
@Mixin(Boat.class)
public abstract class PlayerBoatFeaturesMixin implements VehicleJumpAccess {
//#endif
    @Unique private long carpetFga$lastJump = -20;

    @Override
    public boolean carpetFga$tryJump() {
//#if MC >= 26.1
//$$         AbstractBoat boat = (AbstractBoat) (Object) this;
//#else
        Boat boat = (Boat) (Object) this;
//#endif
        if (!FGASettings.vehicleJump || !(boat.getFirstPassenger() instanceof ServerPlayer driver)
                || boat.level().getGameTime() - carpetFga$lastJump < 10
                || !boat.onGround() && (!boat.isInWater() || boat.isUnderWater())) return false;
        carpetFga$lastJump = boat.level().getGameTime();
        boat.setDeltaMovement(boat.getDeltaMovement().with(net.minecraft.core.Direction.Axis.Y, 0.42));
        // The driving client simulates boats; use vanilla velocity synchronization to apply its impulse.
        driver.connection.send(new ClientboundSetEntityMotionPacket(boat));
        return true;
    }
}
//#endif
