//#if MC >= 1.20.5
package carpet.fga.mixin;

import carpet.fga.FGAModDetector;
import carpet.fga.FGAPayloads;
//#if MC >= 1.21 && MC <= 1.21.1
import carpet.fga.QuickCraftEntityPlacementServer;
//#endif
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(
            method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void handleHandshake(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        //#if MC >= 1.21 && MC <= 1.21.1
        if (packet.payload() instanceof FGAPayloads.EntityPlaceHelloPayload payload) {
            QuickCraftEntityPlacementServer.handleHello(player, payload);
            ci.cancel();
            return;
        }
        if (packet.payload() instanceof FGAPayloads.EntityPlaceRequestPayload payload) {
            QuickCraftEntityPlacementServer.handleRequest(player, payload);
            ci.cancel();
            return;
        }
        //#endif
        if (packet.payload() instanceof FGAPayloads.HandshakePayload) {
            FGAModDetector.markAsModded(player);
            List<ServerPlayer> longNamePlayers = ((ServerCommonPacketListenerAccessor) this).carpetFga$getServer()
                    .getPlayerList().getPlayers().stream()
                    .filter(onlinePlayer -> onlinePlayer.getGameProfile()
                            //#if MC >= 1.21.10
                            //$$ .name()
                            //#else
                            .getName()
                            //#endif
                            .length() > 16)
                    .toList();
            if (!longNamePlayers.isEmpty()) {
                player.connection.send(new ClientboundPlayerInfoRemovePacket(longNamePlayers.stream()
                        .map(ServerPlayer::getUUID)
                        .toList()));
                player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(longNamePlayers));
            }
            ci.cancel();
        }
    }
}
//#endif
