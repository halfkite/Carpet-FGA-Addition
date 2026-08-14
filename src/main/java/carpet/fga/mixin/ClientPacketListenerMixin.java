//#if MC >= 1.20.2
package carpet.fga.mixin;

import carpet.fga.FGAPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void sendHandshake(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
//#if MC >= 1.20.5
            connection.send(new ServerboundCustomPayloadPacket(new FGAPayloads.HandshakePayload(1)));
//#else
//$$         connection.send(new ServerboundCustomPayloadPacket(new FGAPayloads.HandshakePayload()));
//#endif
        }
    }

}
//#endif
