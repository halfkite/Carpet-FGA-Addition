//#if MC == 1.20.1
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.LegacyPlayerLoadDistanceManager;
//$$ import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.network.ServerGamePacketListenerImpl;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ @Mixin(ServerGamePacketListenerImpl.class)
//$$ public abstract class LegacyServerGamePacketListenerPlayerLoadDistanceMixin {
//$$     @Shadow public ServerPlayer player;
//$$
//$$     @Inject(method = "handleClientInformation", at = @At("TAIL"))
//$$     private void carpetFga$refreshRequestedDistance(ServerboundClientInformationPacket packet,
//$$                                                       CallbackInfo callback) {
//$$         LegacyPlayerLoadDistanceManager.onClientInformation(player, packet.viewDistance());
//$$     }
//$$ }
//#endif
