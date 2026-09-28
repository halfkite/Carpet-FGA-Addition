//#if MC >= 26.3
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.FGAModDetector;
//$$ import carpet.fga.FGAPayloads;
//$$ import carpet.fga.FakePlayerNameAlias;
//$$ import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
//$$ import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
//$$ import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.network.ServerGamePacketListenerImpl;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /** 26.3+ handshake resynchronization keeps packet construction inside the modern codec scope. */
//$$ @Mixin(ServerGamePacketListenerImpl.class)
//$$ public abstract class ModernLongNameServerHandshakeMixin {
//$$     @Shadow
//$$     public ServerPlayer player;
//$$
//$$     @Inject(
//$$             method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;)V",
//$$             at = @At("HEAD"), cancellable = true
//$$     )
//$$     private void fga$handleModernLongNameHandshake(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
//$$         if (!(packet.payload() instanceof FGAPayloads.HandshakePayload)) {
//$$             return;
//$$         }
//$$         FGAModDetector.markAsModded(player);
//$$         var longNamePlayers = ((ServerCommonPacketListenerAccessor) this).carpetFga$getServer()
//$$                 .getPlayerList().getPlayers().stream()
//$$                 .filter(onlinePlayer -> onlinePlayer.getGameProfile().name().length() > 16)
//$$                 .toList();
//$$         if (!longNamePlayers.isEmpty()) {
//$$             player.connection.send(new ClientboundPlayerInfoRemovePacket(longNamePlayers.stream()
//$$                     .map(ServerPlayer::getUUID).toList()));
//$$             FakePlayerNameAlias.withFullNames(() -> {
//$$                 player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(longNamePlayers));
//$$                 return null;
//$$             });
//$$         }
//$$         ci.cancel();
//$$     }
//$$ }
//#endif
