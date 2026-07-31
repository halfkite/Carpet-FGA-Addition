//#if MC >= 1.19.4
package carpet.fga;

import carpet.logging.Logger;
import carpet.logging.LoggerRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class PlayerHealthDisplay {
    public static boolean __playerHealth;

    private static final String LOGGER_NAME = "playerHealth";
    private static final ThreadLocal<ServerPlayer> PACKET_RECEIVER = new ThreadLocal<>();
    private static final Map<UUID, HealthState> LAST_HEALTH = new HashMap<>();
    private static final Map<UUID, Boolean> LAST_SUBSCRIPTIONS = new HashMap<>();
    private static MinecraftServer server;
    private static String lastMode;

    private PlayerHealthDisplay() {}

    public static void registerLogger() {
        try {
            LoggerRegistry.registerLogger(LOGGER_NAME,
                    new Logger(PlayerHealthDisplay.class.getField("__playerHealth"), LOGGER_NAME, null, null));
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException("Could not register playerHealth logger", exception);
        }
    }

    public static void tick(MinecraftServer currentServer) {
        server = currentServer;
        String mode = FGASettings.playerHealthDisplay;
        List<ServerPlayer> players = currentServer.getPlayerList().getPlayers();
        boolean modeChanged = !mode.equals(lastMode);
        lastMode = mode;

        for (ServerPlayer viewer : players) {
            boolean subscribed = isSubscribed(viewer);
            Boolean previous = LAST_SUBSCRIPTIONS.put(viewer.getUUID(), subscribed);
            if (!modeChanged && (previous == null || previous != subscribed)) {
                sendFullRefresh(viewer, players);
            }
        }
        LAST_SUBSCRIPTIONS.keySet().removeIf(uuid -> currentServer.getPlayerList().getPlayer(uuid) == null);

        if (modeChanged) {
            for (ServerPlayer viewer : players) sendFullRefresh(viewer, players);
        }

        boolean active = !"false".equals(mode) || __playerHealth;
        for (ServerPlayer subject : players) {
            HealthState state = HealthState.of(subject);
            HealthState previous = LAST_HEALTH.put(subject.getUUID(), state);
            if (active && !state.equals(previous)) broadcastUpdate(currentServer, subject);
        }
        LAST_HEALTH.keySet().removeIf(uuid -> currentServer.getPlayerList().getPlayer(uuid) == null);
    }

    public static boolean shouldDecorate(ServerPlayer viewer) {
        String mode = FGASettings.playerHealthDisplay;
        return "true".equals(mode) || "nofake".equals(mode) || isSubscribed(viewer);
    }

    public static Component tabDisplayName(ServerPlayer subject, Component vanilla) {
        ServerPlayer viewer = PACKET_RECEIVER.get();
        Component base = vanilla != null ? vanilla : subject.getDisplayName();
        if (viewer == null || !shouldDecorate(viewer)) return base;
        if ("nofake".equals(FGASettings.playerHealthDisplay)
                && subject instanceof carpet.patches.EntityPlayerMPFake) return base;
        return base.copy().append(Component.literal(" ")).append(healthLine(subject));
    }

    public static <T> T forReceiver(ServerPlayer receiver, Supplier<T> factory) {
        ServerPlayer previous = PACKET_RECEIVER.get();
        PACKET_RECEIVER.set(receiver);
        try {
            return factory.get();
        } finally {
            if (previous == null) PACKET_RECEIVER.remove();
            else PACKET_RECEIVER.set(previous);
        }
    }

    public static ServerPlayer getOnlinePlayer(UUID uuid) {
        return server == null ? null : server.getPlayerList().getPlayer(uuid);
    }

    public static void remove(ServerPlayer player) {
        LAST_HEALTH.remove(player.getUUID());
        LAST_SUBSCRIPTIONS.remove(player.getUUID());
    }

    public static void clear(MinecraftServer currentServer) {
        LAST_HEALTH.clear();
        LAST_SUBSCRIPTIONS.clear();
        PACKET_RECEIVER.remove();
        lastMode = null;
        server = null;
    }

    private static boolean isSubscribed(ServerPlayer player) {
        Map<String, String> subscriptions = LoggerRegistry.getPlayerSubscriptions(player.getScoreboardName());
        return subscriptions != null && subscriptions.containsKey(LOGGER_NAME);
    }

    private static void sendFullRefresh(ServerPlayer viewer, List<ServerPlayer> subjects) {
        if (subjects.isEmpty()) return;
        viewer.connection.send(new ClientboundPlayerInfoUpdatePacket(
                EnumSet.allOf(ClientboundPlayerInfoUpdatePacket.Action.class), subjects));
    }

    private static void broadcastUpdate(MinecraftServer currentServer, ServerPlayer subject) {
        currentServer.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                EnumSet.allOf(ClientboundPlayerInfoUpdatePacket.Action.class), List.of(subject)));
    }

    private static MutableComponent healthLine(ServerPlayer player) {
        MutableComponent result = valueWithHeart(player.getHealth(), ChatFormatting.RED);
        float absorption = player.getAbsorptionAmount();
        if (absorption > 0.0F) {
            result.append(Component.literal(" / "));
            result.append(valueWithHeart(absorption, ChatFormatting.GOLD));
        }
        return result;
    }

    private static MutableComponent valueWithHeart(float value, ChatFormatting color) {
        return Component.literal(format(value) + " ").withStyle(color)
                .append(Component.literal("\u2764").withStyle(color));
    }

    private static String format(float value) {
        return value == Math.round(value) ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    private record HealthState(float health, float absorption) {
        private static HealthState of(ServerPlayer player) {
            return new HealthState(player.getHealth(), player.getAbsorptionAmount());
        }
    }
}
//#endif
