package carpet.fga.mixin;

import carpet.fga.FGAModDetector;
import carpet.fga.FakePlayerNameAlias;
import carpet.fga.PlayerHealthDisplay;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
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
        if ((!sendFullNames && !decorateHealth) || players.size() != infoPacket.entries().size()) {
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
