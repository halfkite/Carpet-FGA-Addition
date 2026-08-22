//#if MC == 1.20.1 || MC == 1.21.1
package carpet.fga;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Keeps the command and player-list integration stable across the two tracking implementations. */
public final class PlayerLoadDistanceCompat {
    public static final int NONE = -2;

    private PlayerLoadDistanceCompat() {
    }

    public static void load(MinecraftServer server) {
        //#if MC == 1.20.1
        //$$ LegacyPlayerLoadDistanceManager.load(server);
        //#else
        PlayerLoadDistanceManager.load(server);
        //#endif
    }

    public static void tick(MinecraftServer server) {
        //#if MC == 1.20.1
        //$$ LegacyPlayerLoadDistanceManager.tick(server);
        //#else
        PlayerLoadDistanceManager.tick(server);
        //#endif
    }

    public static void clear() {
        //#if MC == 1.20.1
        //$$ LegacyPlayerLoadDistanceManager.clear();
        //#else
        PlayerLoadDistanceManager.clear();
        //#endif
    }

    public static void onRuleChanged() {
        //#if MC == 1.20.1
        //$$ LegacyPlayerLoadDistanceManager.onRuleChanged();
        //#else
        PlayerLoadDistanceManager.onRuleChanged();
        //#endif
    }

    public static void onLogin(ServerPlayer player) {
        //#if MC == 1.20.1
        //$$ LegacyPlayerLoadDistanceManager.onLogin(player);
        //#else
        PlayerLoadDistanceManager.onLogin(player);
        //#endif
    }

    public static void onLogout(ServerPlayer player) {
        //#if MC == 1.20.1
        //$$ LegacyPlayerLoadDistanceManager.onLogout(player);
        //#else
        PlayerLoadDistanceManager.onLogout(player);
        //#endif
    }

    public static void reapply(ServerPlayer player) {
        //#if MC == 1.20.1
        //$$ LegacyPlayerLoadDistanceManager.reapply(player);
        //#else
        PlayerLoadDistanceManager.reapply(player);
        //#endif
    }

    public static void set(ServerPlayer player, int distance, boolean persistent) throws Exception {
        //#if MC == 1.20.1
        //$$ LegacyPlayerLoadDistanceManager.set(player, distance, persistent);
        //#else
        PlayerLoadDistanceManager.set(player, distance, persistent);
        //#endif
    }

    public static void reset(ServerPlayer player, boolean persistent) throws Exception {
        //#if MC == 1.20.1
        //$$ LegacyPlayerLoadDistanceManager.reset(player, persistent);
        //#else
        PlayerLoadDistanceManager.reset(player, persistent);
        //#endif
    }

    public static int configured(ServerPlayer player) {
        //#if MC == 1.20.1
        //$$ return LegacyPlayerLoadDistanceManager.configured(player);
        //#else
        return PlayerLoadDistanceManager.configured(player);
        //#endif
    }

    public static int effective(ServerPlayer player) {
        //#if MC == 1.20.1
        //$$ return LegacyPlayerLoadDistanceManager.effective(player);
        //#else
        return PlayerLoadDistanceManager.effective(player);
        //#endif
    }

    public static Component decorate(ServerPlayer player, Component base) {
        //#if MC == 1.20.1
        //$$ return LegacyPlayerLoadDistanceManager.decorate(player, base);
        //#else
        return PlayerLoadDistanceManager.decorate(player, base);
        //#endif
    }

    public static int parseDistance(String raw) {
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if ("none".equals(value) || "无".equals(value)) return NONE;
        int distance = Integer.parseInt(value);
        if (distance < -1 || distance > 32) {
            throw new IllegalArgumentException("distance must be -1, 0-32, or none");
        }
        return distance;
    }

    public static String formatDistance(int distance) {
        return distance == NONE ? "none" : Integer.toString(distance);
    }

    public static String describeDistance(int distance) {
        return switch (distance) {
            case NONE -> "none / 无加载";
            case -1 -> "-1 / 中心弱加载";
            case 0 -> "0 / 中心强加载 + 3x3 弱加载";
            default -> formatDistance(distance) + " / 区块半径";
        };
    }
}
//#endif
