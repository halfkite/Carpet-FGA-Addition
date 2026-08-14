//#if MC >= 1.16.5 && MC < 1.20.2
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.FGAPayloads;
//$$ import io.netty.buffer.Unpooled;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.multiplayer.ClientPacketListener;
//$$ import net.minecraft.network.FriendlyByteBuf;
//$$ import net.minecraft.network.protocol.game.ClientboundLoginPacket;
//$$ import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ @Mixin(ClientPacketListener.class)
//$$ public abstract class LegacyClientPacketListenerMixin {
//$$     @Inject(method = "handleLogin", at = @At("TAIL"))
//$$     private void sendHandshake(ClientboundLoginPacket packet, CallbackInfo ci) {
//$$         ClientPacketListener connection = Minecraft.getInstance().getConnection();
//$$         if (connection != null) {
//$$             FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer());
//$$             data.writeVarInt(1);
//$$             connection.send(new ServerboundCustomPayloadPacket(FGAPayloads.HANDSHAKE_CHANNEL, data));
//$$         }
//$$     }
//$$ }
//#endif
