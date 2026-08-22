//#if MC == 1.20.1
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.DeathDropPreStackManager;
//$$ import carpet.fga.FGASettings;
//$$ import net.minecraft.server.level.ServerLevel;
//$$ import net.minecraft.world.damagesource.DamageSource;
//$$ import net.minecraft.world.entity.vehicle.AbstractMinecart;
//$$ import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ @Mixin(AbstractMinecart.class)
//$$ public abstract class LegacyMinecartPreStackMixin {
//$$     @Inject(method = "destroy(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
//$$     private void carpetFga$beginVehicleDrop(DamageSource source, CallbackInfo callback) {
//$$         AbstractMinecart vehicle = (AbstractMinecart) (Object) this;
//$$         if (vehicle instanceof AbstractMinecartContainer) return;
//$$         if (vehicle.level() instanceof ServerLevel level && FGASettings.shouldPreStackDeathDrops(vehicle)) {
//$$             DeathDropPreStackManager.begin(vehicle, level);
//$$         }
//$$     }
//$$
//$$     @Inject(method = "destroy(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("RETURN"))
//$$     private void carpetFga$finishVehicleDrop(DamageSource source, CallbackInfo callback) {
//$$         AbstractMinecart vehicle = (AbstractMinecart) (Object) this;
//$$         if (vehicle instanceof AbstractMinecartContainer) return;
//$$         if (vehicle.level() instanceof ServerLevel && FGASettings.shouldPreStackDeathDrops(vehicle)) {
//$$             DeathDropPreStackManager.finish(vehicle, true);
//$$         }
//$$     }
//$$ }
//#endif
