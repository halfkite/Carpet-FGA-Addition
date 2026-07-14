package carpet.fga;

import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FGAModDetector {
    private static final Set<UUID> MODDED_PLAYERS = ConcurrentHashMap.newKeySet();

    private FGAModDetector() {
    }

    public static void markAsModded(ServerPlayer player) {
        MODDED_PLAYERS.add(player.getUUID());
    }

    public static void remove(ServerPlayer player) {
        MODDED_PLAYERS.remove(player.getUUID());
    }

    public static boolean hasMod(ServerPlayer player) {
        return MODDED_PLAYERS.contains(player.getUUID());
    }
}
