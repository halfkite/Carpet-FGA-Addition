//#if MC >= 1.21.1
package carpet.fga.mixin;

import carpet.fga.ClientDimensionIdMapping;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ClientDimensionIdPacketMixin {
    @ModifyVariable(
            method =
                    //#if MC >= 1.21.6
                    //$$ "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
                    //#else
                    "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
                    //#endif
            at = @At("HEAD"),
            argsOnly = true,
            index = 1)
    private Packet<?> carpetFga$remapClientDimensionId(Packet<?> packet) {
        return ClientDimensionIdMapping.remap(packet);
    }
}
//#endif
