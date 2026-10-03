//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.FGAText;
import carpet.patches.EntityPlayerMPFake;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class AbnormalDisconnectNoticeMixin {
    @Shadow public ServerPlayer player;
    @Unique private static final Logger carpetFga$LOGGER = LoggerFactory.getLogger("carpet-fga-addition");

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void carpetFga$notice(DisconnectionDetails details, CallbackInfo ci) {
        String mode = FGASettings.abnormalDisconnectNotice;
        if ("false".equals(mode) || player instanceof EntityPlayerMPFake) return;
        Component reason = details.reason();
        if (!(reason.getContents() instanceof TranslatableContents translation)) return;
        String key = translation.getKey();
        // EOF is also sent for normal client quits: do not mislabel it as a crash.
        if (!"disconnect.timeout".equals(key) && !"disconnect.genericReason".equals(key)) return;
        MinecraftServer server = player.level().getServer();
        String name = player.getName().getString();
        server.execute(() -> {
            if (!server.isRunning() || server.isStopped()) return;
            Component message = FGAText.text("carpet-fga-addition.features.disconnect.notice", name, reason.getString());
            if ("true".equals(mode)) server.getPlayerList().broadcastSystemMessage(message, false);
            else carpetFga$LOGGER.warn(message.getString());
        });
    }
}
//#endif
