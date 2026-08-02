//#if MC >= 1.20.5 && MC < 26.2
package carpet.fga.mixin;

import carpet.fga.DeathDropPreStackManager;
import carpet.fga.FGACompat;
import carpet.fga.FGASettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures configured vehicle destruction drops, including hopper minecarts. */
@Mixin(VehicleEntity.class)
public abstract class VehicleEntityMixin {
    //#if MC >= 1.21.4
    @Inject(method = "destroy(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void carpetFga$beginVehicleDrop(ServerLevel level, DamageSource source, CallbackInfo callback) {
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (FGASettings.shouldPreStackDeathDrops(vehicle)) {
            DeathDropPreStackManager.begin(vehicle, level);
        }
    }

    @Inject(method = "destroy(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("RETURN"))
    private void carpetFga$finishVehicleDrop(ServerLevel level, DamageSource source, CallbackInfo callback) {
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (FGASettings.shouldPreStackDeathDrops(vehicle)) {
            DeathDropPreStackManager.finish(vehicle, true);
        }
    }
    //#else
    //$$ @Inject(method = "destroy(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    //$$ private void carpetFga$beginVehicleDrop(DamageSource source, CallbackInfo callback) {
    //$$     VehicleEntity vehicle = (VehicleEntity) (Object) this;
    //$$     if (FGACompat.level(vehicle) instanceof ServerLevel level
    //$$             && FGASettings.shouldPreStackDeathDrops(vehicle)) {
    //$$         DeathDropPreStackManager.begin(vehicle, level);
    //$$     }
    //$$ }

    //$$ @Inject(method = "destroy(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("RETURN"))
    //$$ private void carpetFga$finishVehicleDrop(DamageSource source, CallbackInfo callback) {
    //$$     VehicleEntity vehicle = (VehicleEntity) (Object) this;
    //$$     if (FGACompat.level(vehicle) instanceof ServerLevel level
    //$$             && FGASettings.shouldPreStackDeathDrops(vehicle)) {
    //$$         DeathDropPreStackManager.finish(vehicle, true);
    //$$     }
    //$$ }
    //#endif

    /*
    @Inject(method = "destroy(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void carpetFga$beginVehicleDrop(DamageSource source, CallbackInfo callback) {
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (FGACompat.level(vehicle) instanceof ServerLevel level
                && FGASettings.shouldPreStackDeathDrops(vehicle)) {
            DeathDropPreStackManager.begin(vehicle, level);
        }
    }

    @Inject(method = "destroy(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("RETURN"))
    private void carpetFga$finishVehicleDrop(DamageSource source, CallbackInfo callback) {
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (FGACompat.level(vehicle) instanceof ServerLevel level
                && FGASettings.shouldPreStackDeathDrops(vehicle)) {
            DeathDropPreStackManager.finish(vehicle, true);
        }
    }
    */
}
//#endif
