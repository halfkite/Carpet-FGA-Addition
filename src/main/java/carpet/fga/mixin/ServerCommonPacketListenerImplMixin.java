package carpet.fga.mixin;

import carpet.fga.FGAModDetector;
import carpet.fga.FakePlayerNameAlias;
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
    private boolean fga$sendingFullNames;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void sendFullNamesToModdedClient(Packet<?> packet, CallbackInfo ci) {
        if (fga$sendingFullNames || !(packet instanceof ClientboundPlayerInfoUpdatePacket infoPacket)) {
            return;
        }
        ServerCommonPacketListenerImpl listener = (ServerCommonPacketListenerImpl) (Object) this;
        ServerPlayer receiver = server.getPlayerList().getPlayer(listener.getOwner().getId());
        if (receiver == null || !FGAModDetector.hasMod(receiver)) {
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
        if (!hasLongName || players.size() != infoPacket.entries().size()) {
            return;
        }

        ClientboundPlayerInfoUpdatePacket fullPacket = FakePlayerNameAlias.withFullNames(
                () -> new ClientboundPlayerInfoUpdatePacket(infoPacket.actions(), players));
        fga$sendingFullNames = true;
        try {
            listener.send(fullPacket);
        } finally {
            fga$sendingFullNames = false;
        }
        ci.cancel();
    }
}
