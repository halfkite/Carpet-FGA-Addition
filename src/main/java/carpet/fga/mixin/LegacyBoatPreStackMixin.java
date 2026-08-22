//#if MC == 1.20.1
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.DeathDropPreStackManager;
//$$ import carpet.fga.FGASettings;
//$$ import net.minecraft.server.level.ServerLevel;
//$$ import net.minecraft.world.damagesource.DamageSource;
//$$ import net.minecraft.world.entity.vehicle.Boat;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ @Mixin(Boat.class)
//$$ public abstract class LegacyBoatPreStackMixin {
//$$     @Inject(method = "destroy(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
//$$     private void carpetFga$beginVehicleDrop(DamageSource source, CallbackInfo callback) {
//$$         Boat vehicle = (Boat) (Object) this;
//$$         if (vehicle.level() instanceof ServerLevel level && FGASettings.shouldPreStackDeathDrops(vehicle)) {
//$$             DeathDropPreStackManager.begin(vehicle, level);
//$$         }
//$$     }
//$$
//$$     @Inject(method = "destroy(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("RETURN"))
//$$     private void carpetFga$finishVehicleDrop(DamageSource source, CallbackInfo callback) {
//$$         Boat vehicle = (Boat) (Object) this;
//$$         if (vehicle.level() instanceof ServerLevel && FGASettings.shouldPreStackDeathDrops(vehicle)) {
//$$             DeathDropPreStackManager.finish(vehicle, true);
//$$         }
//$$     }
//$$ }
//#endif
