//#if MC >= 1.21.3 && MC <= 26.3
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.FGASettings;
//$$ import carpet.fga.VehicleJumpAccess;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.world.entity.Entity;
//$$ import net.minecraft.world.entity.player.Input;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ @Mixin(ServerPlayer.class)
//$$ public abstract class PlayerVehicleInputMixin {
//$$     @Inject(method = "setLastClientInput", at = @At("HEAD"))
//$$     private void carpetFga$jump(Input input, CallbackInfo ci) {
//$$         if (!FGASettings.vehicleJump || !input.jump()) return;
//$$         ServerPlayer player = (ServerPlayer) (Object) this;
//$$         if (player.getLastClientInput().jump() || player.isSpectator()) return;
//$$         Entity vehicle = player.getVehicle();
//$$         if (vehicle != null && vehicle.getFirstPassenger() == player && vehicle instanceof VehicleJumpAccess jump) {
//$$             jump.carpetFga$tryJump();
//$$         }
//$$     }
//$$ }
//#endif
