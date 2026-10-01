//#if MC == 26.3
package carpet.fga.mixin;

import carpet.fga.FakePlayerItemSortManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SorterFakeLeaveNoticeMixin {
    @Shadow public ServerPlayer player;

    @Redirect(
            method = "removePlayerFromWorld()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V")
    )
    private void carpetFga$hideSorterFakeLeaveFromPlayers(PlayerList playerList, Component message, boolean overlay) {
        if (FakePlayerItemSortManager.shouldSuppressSorterFakeNotice(player)) {
            // Preserve the server log while avoiding a public chat announcement.
            ((ServerCommonPacketListenerAccessor) (Object) this).carpetFga$getServer().sendSystemMessage(message);
        } else {
            playerList.broadcastSystemMessage(message, overlay);
        }
    }
}
//#endif
