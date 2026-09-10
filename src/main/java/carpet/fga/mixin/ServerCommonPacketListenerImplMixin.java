package carpet.fga.mixin;

import carpet.fga.FGAModDetector;
import carpet.fga.FakePlayerNameAlias;
import carpet.fga.PlayerHealthDisplay;
//#if MC == 1.21.1
import carpet.fga.PlayerLoadDistanceManager;
//#endif
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
//#if MC >= 1.20.2 && MC < 1.20.5
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
//#endif
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @Shadow
    private MinecraftServer server;

    @Unique
    private boolean fga$sendingCustomizedPlayerInfo;

//#if MC >= 1.20.2 && MC < 1.20.5
//$$     @Inject(
//$$             method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;)V",
//$$             at = @At("HEAD"),
//$$             cancellable = true
//$$     )
//$$     private void handleHandshake(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
//$$         if (packet.payload().id().equals(carpet.fga.FGAPayloads.HANDSHAKE_CHANNEL)) {
//$$             ServerCommonPacketListenerImpl listener = (ServerCommonPacketListenerImpl) (Object) this;
//$$             ServerPlayer receiver = server.getPlayerList().getPlayer(listener.getOwner().getId());
//$$             if (receiver != null) {
//$$                 FGAModDetector.markAsModded(receiver);
//$$             }
//$$             ci.cancel();
//$$         }
//$$     }
//#endif

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void sendFullNamesToModdedClient(Packet<?> packet, CallbackInfo ci) {
        if (fga$sendingCustomizedPlayerInfo || !(packet instanceof ClientboundPlayerInfoUpdatePacket infoPacket)) {
            return;
        }
        ServerCommonPacketListenerImpl listener = (ServerCommonPacketListenerImpl) (Object) this;
        ServerPlayer receiver = server.getPlayerList().getPlayer(listener.getOwner().getId());
        if (receiver == null) {
            return;
        }

        List<ServerPlayer> players = new ArrayList<>();
        boolean hasLongName = false;
        for (ClientboundPlayerInfoUpdatePacket.Entry entry : infoPacket.entries()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.profileId());
            if (player != null) {
                players.add(player);
                hasLongName |= player.getGameProfile().getName().length() > 16;
            }
        }
        boolean sendFullNames = FGAModDetector.hasMod(receiver) && hasLongName;
        boolean decorateHealth = PlayerHealthDisplay.shouldDecorate(receiver);
        //#if MC == 1.21.1
        boolean decorateLoadDistance = players.stream().anyMatch(PlayerLoadDistanceManager::hasOverride);
        //#else
        //$$ boolean decorateLoadDistance = false;
        //#endif
        if ((!sendFullNames && !decorateHealth && !decorateLoadDistance)
                || players.size() != infoPacket.entries().size()) {
            return;
        }

        java.util.function.Supplier<ClientboundPlayerInfoUpdatePacket> packetFactory =
                () -> PlayerHealthDisplay.forReceiver(receiver,
                        () -> new ClientboundPlayerInfoUpdatePacket(infoPacket.actions(), players));
        ClientboundPlayerInfoUpdatePacket customizedPacket = sendFullNames
                ? FakePlayerNameAlias.withFullNames(packetFactory)
                : packetFactory.get();
        fga$sendingCustomizedPlayerInfo = true;
        try {
            listener.send(customizedPacket);
        } finally {
            fga$sendingCustomizedPlayerInfo = false;
        }
        ci.cancel();
    }
}
