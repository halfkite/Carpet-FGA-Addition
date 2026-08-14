//#if MC >= 1.16.5 && MC < 1.20.2
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.FGAModDetector;
//$$ import carpet.fga.FGAPayloads;
//$$ import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.network.ServerGamePacketListenerImpl;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ @Mixin(ServerGamePacketListenerImpl.class)
//$$ public abstract class LegacyServerGamePacketListenerImplMixin {
//$$     @Shadow
//$$     public ServerPlayer player;
//$$
//$$     @Inject(
//$$             method = "handleCustomPayload(Lnet/minecraft/network/protocol/game/ServerboundCustomPayloadPacket;)V",
//$$             at = @At("HEAD"),
//$$             cancellable = true
//$$     )
//$$     private void handleHandshake(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
//$$         if (FGAPayloads.HANDSHAKE_CHANNEL.equals(
//$$                 ((LegacyCustomPayloadAccessor) (Object) packet).carpetFga$getIdentifier())) {
//$$             FGAModDetector.markAsModded(player);
//$$             ci.cancel();
//$$         }
//$$     }
//$$ }
//#endif
