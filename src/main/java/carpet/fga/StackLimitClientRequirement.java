package carpet.fga;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.network.chat.Component;
//#if MC < 1.19
//$$ import net.minecraft.network.chat.TextComponent;
//#endif
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Enforces the FGA handshake only while inventory or container stack limits are active. */
public final class StackLimitClientRequirement {
    private static long ticks;

    private StackLimitClientRequirement() {
    }

    public static void tick(MinecraftServer server) {
        if (++ticks < 40 || !DroppedItemStackLimitConfig.requiresModdedClient()) return;
        ticks = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof EntityPlayerMPFake) && !FGAModDetector.hasMod(player)) {
                player.connection.disconnect(
//#if MC >= 1.19
                        Component.literal(
//#else
//$$                     new TextComponent(
//#endif
                                "Server inventory/container stack limits require the FGA client"));
            }
        }
    }

    public static void clear() {
        ticks = 0;
    }
}
