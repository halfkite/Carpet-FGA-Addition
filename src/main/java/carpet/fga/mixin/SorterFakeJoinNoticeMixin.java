//#if MC == 26.3
package carpet.fga.mixin;

import carpet.fga.FakePlayerItemSortManager;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public abstract class SorterFakeJoinNoticeMixin {
    @Shadow @Final private MinecraftServer server;

    @Redirect(
            method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V")
    )
    private void carpetFga$hideSorterFakeJoinFromPlayers(PlayerList playerList, Component message, boolean overlay,
                                                         Connection connection, ServerPlayer joiningPlayer,
                                                         CommonListenerCookie cookie) {
        if (FakePlayerItemSortManager.shouldSuppressSorterFakeNotice(joiningPlayer)) {
            // Keep the dedicated-server console's system-chat record; only skip the client broadcast.
            server.sendSystemMessage(message);
        } else {
            playerList.broadcastSystemMessage(message, overlay);
        }
    }
}
//#endif
