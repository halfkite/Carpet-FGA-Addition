package carpet.fga;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.network.chat.Component;
//#if MC < 1.19
//$$ import net.minecraft.network.chat.TextComponent;
//#endif
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Enforces the FGA handshake only while inventory or container stack limits are active. */
public final class StackLimitClientRequirement {
    /**
     * The FGA client sends its handshake immediately after the login packet is
     * handled on the client. Give that packet a bounded window to arrive before
     * enforcing the server-side stack-limit requirement.
     */
    private static final int HANDSHAKE_GRACE_TICKS = 200;
    private static long ticks;
    private static final Map<UUID, Integer> HANDSHAKE_GRACE = new HashMap<>();

    private StackLimitClientRequirement() {
    }

    public static void tick(MinecraftServer server) {
        if (++ticks < 40 || !DroppedItemStackLimitConfig.requiresModdedClient()) return;
        ticks = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof EntityPlayerMPFake || FGAModDetector.hasMod(player)) {
                HANDSHAKE_GRACE.remove(player.getUUID());
                continue;
            }
            Integer remaining = HANDSHAKE_GRACE.get(player.getUUID());
            if (remaining != null && remaining > 0) {
                HANDSHAKE_GRACE.put(player.getUUID(), remaining - 40);
                continue;
            }
            HANDSHAKE_GRACE.remove(player.getUUID());
            player.connection.disconnect(
//#if MC >= 1.19
                    Component.literal(
//#else
//$$                     new TextComponent(
//#endif
                            "Server inventory/container stack limits require the FGA client"));
        }
    }

    public static void onPlayerLoggedIn(ServerPlayer player) {
        if (player != null) HANDSHAKE_GRACE.put(player.getUUID(), HANDSHAKE_GRACE_TICKS);
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        if (player != null) HANDSHAKE_GRACE.remove(player.getUUID());
    }

    public static void clear() {
        ticks = 0;
        HANDSHAKE_GRACE.clear();
    }
}
