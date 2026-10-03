//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.FGASettings;
//#if MC >= 1.21.3
//$$ import net.minecraft.server.level.ServerLevel;
//#endif
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
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

@Mixin(LivingEntity.class)
public abstract class VehicleNoCrammingMixin {
//#if MC >= 1.21.3
//$$     @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
//$$     private void carpetFga$noCramming(ServerLevel level, DamageSource source, float amount,
//$$                                      CallbackInfoReturnable<Boolean> cir) {
//#else
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void carpetFga$noCramming(DamageSource source, float amount,
                                     CallbackInfoReturnable<Boolean> cir) {
//#endif
        if (!FGASettings.vehicleNoCramming || !source.is(DamageTypes.CRAMMING)) return;
        Entity vehicle = ((LivingEntity) (Object) this).getVehicle();
//#if MC >= 26.1
//$$         if (vehicle instanceof AbstractBoat || vehicle instanceof Minecart) cir.setReturnValue(false);
//#else
        if (vehicle instanceof Boat || vehicle instanceof Minecart) cir.setReturnValue(false);
//#endif
    }
}
//#endif
