//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.NewFeatureOptions;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
//#if MC >= 26.1
//$$ import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
//$$ import net.minecraft.world.entity.vehicle.minecart.Minecart;
//#else
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class PlayerVehicleCapacityMixin {
    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void carpetFga$capacity(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        int limit = NewFeatureOptions.capacity(FGASettings.playerVehicleCapacity);
        if (limit == 0 || !(passenger instanceof Player)) return;
        Entity vehicle = (Entity) (Object) this;
//#if MC >= 26.1
//$$         if (vehicle instanceof Minecart cart) {
//$$             cir.setReturnValue(cart.getPassengers().size() < limit);
//$$         } else if (vehicle instanceof AbstractBoat boat) {
//$$             cir.setReturnValue(boat.getPassengers().size() < limit && !boat.isEyeInFluid(FluidTags.WATER));
//$$         }
//#else
        if (vehicle instanceof Minecart cart) {
            cir.setReturnValue(cart.getPassengers().size() < limit);
        } else if (vehicle instanceof Boat boat) {
            cir.setReturnValue(boat.getPassengers().size() < limit && !boat.isEyeInFluid(FluidTags.WATER));
        }
//#endif
    }
}
//#endif
