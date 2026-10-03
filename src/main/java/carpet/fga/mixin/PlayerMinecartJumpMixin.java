//#if MC >= 1.21.3 && MC <= 26.3
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.FGASettings;
//$$ import carpet.fga.VehicleJumpAccess;
//$$ import net.minecraft.server.level.ServerLevel;
//$$ import net.minecraft.server.level.ServerPlayer;
//#if MC >= 26.1
//$$ import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
//$$ import net.minecraft.world.entity.vehicle.minecart.Minecart;
//$$ import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
//$$ import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
//#else
//$$ import net.minecraft.world.entity.vehicle.AbstractMinecart;
//$$ import net.minecraft.world.entity.vehicle.Minecart;
//$$ import net.minecraft.world.entity.vehicle.MinecartBehavior;
//$$ import net.minecraft.world.entity.vehicle.NewMinecartBehavior;
//#endif
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.Unique;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Redirect;
//$$
//$$ @Mixin(AbstractMinecart.class)
//$$ public abstract class PlayerMinecartJumpMixin implements VehicleJumpAccess {
//$$     @Shadow protected abstract void comeOffTrack(ServerLevel level);
//$$     @Unique private int carpetFga$flightTicks;
//$$     @Unique private long carpetFga$lastJump = -20;
//$$
//$$     @Override
//$$     public boolean carpetFga$tryJump() {
//$$         AbstractMinecart cart = (AbstractMinecart) (Object) this;
//$$         if (!FGASettings.vehicleJump || !(cart instanceof Minecart)
//$$                 || !(cart.getFirstPassenger() instanceof ServerPlayer)
//$$                 || carpetFga$flightTicks > 0 || cart.level().getGameTime() - carpetFga$lastJump < 10
//$$                 || !cart.onGround() && !cart.isOnRails()) return false;
//$$         carpetFga$lastJump = cart.level().getGameTime();
//$$         carpetFga$flightTicks = 1;
//$$         cart.setOnGround(false);
//$$         cart.setDeltaMovement(cart.getDeltaMovement().with(net.minecraft.core.Direction.Axis.Y, 0.42));
//$$         return true;
//$$     }
//$$
//$$     @Redirect(method = "tick", at = @At(value = "INVOKE",
//#if MC >= 26.1
//$$             target = "Lnet/minecraft/world/entity/vehicle/minecart/MinecartBehavior;tick()V"))
//#else
//$$             target = "Lnet/minecraft/world/entity/vehicle/MinecartBehavior;tick()V"))
//#endif
//$$     private void carpetFga$flight(MinecartBehavior behavior) {
//$$         AbstractMinecart cart = (AbstractMinecart) (Object) this;
//$$         if (carpetFga$flightTicks == 0 || !(cart.level() instanceof ServerLevel level)) {
//$$             behavior.tick();
//$$             return;
//$$         }
//$$         cart.setOnRails(false);
//$$         cart.applyGravity();
//$$         comeOffTrack(level);
//$$         cart.applyEffectsFromBlocks();
//$$         if (behavior instanceof NewMinecartBehavior modern) {
//$$             modern.lerpSteps.add(new NewMinecartBehavior.MinecartStep(cart.position(), cart.getDeltaMovement(),
//$$                     cart.getYRot(), cart.getXRot(), 1.0F));
//$$         }
//$$         // Resume rail physics on landing, or after a bounded airborne interval (falling into void).
//$$         if (cart.onGround() || ++carpetFga$flightTicks > 40) carpetFga$flightTicks = 0;
//$$     }
//$$ }
//#endif
