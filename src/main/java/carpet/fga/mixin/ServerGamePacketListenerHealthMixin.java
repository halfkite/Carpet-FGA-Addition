//#if MC >= 1.19.4 && MC < 1.20.2
package carpet.fga.mixin;

import carpet.fga.PlayerHealthDisplay;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Sends a receiver-specific player-list packet on the pre-1.20.5 listener API. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerHealthMixin {
    @Shadow public ServerPlayer player;

    @Unique private boolean carpetFga$sendingHealthPacket;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void carpetFga$decoratePlayerList(Packet<?> packet, CallbackInfo ci) {
        if (carpetFga$sendingHealthPacket
                || !(packet instanceof ClientboundPlayerInfoUpdatePacket infoPacket)
                || !PlayerHealthDisplay.shouldDecorate(player)) {
            return;
        }

        List<ServerPlayer> subjects = new ArrayList<>();
        for (ClientboundPlayerInfoUpdatePacket.Entry entry : infoPacket.entries()) {
            ServerPlayer subject = player.server.getPlayerList().getPlayer(entry.profileId());
            if (subject == null) return;
            subjects.add(subject);
        }

        ClientboundPlayerInfoUpdatePacket customized = PlayerHealthDisplay.forReceiver(player,
                () -> new ClientboundPlayerInfoUpdatePacket(infoPacket.actions(), subjects));
        carpetFga$sendingHealthPacket = true;
        try {
            ((ServerGamePacketListenerImpl) (Object) this).send(customized);
        } finally {
            carpetFga$sendingHealthPacket = false;
        }
        ci.cancel();
    }
}
//#endif
