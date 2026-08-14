//#if MC == 1.21.1
package carpet.fga;

import carpet.fga.mixin.ChunkMapPlayerLoadDistanceAccessor;
import carpet.fga.mixin.DistanceManagerPlayerLoadDistanceAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.util.EnumSet;

public final class PlayerLoadDistanceManager {
    public static final int NONE = -2;
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/player-load-distance");
    private static final Map<UUID, Integer> TEMPORARY = new HashMap<>();
    private static final Map<UUID, TicketSet> TICKETS = new HashMap<>();
    private static final Map<UUID, AppliedState> APPLIED = new HashMap<>();
    private static final TicketType<String> LOAD_TICKET = TicketType.create(
            "carpet_fga_player_load", Comparator.<String>naturalOrder());
    private static MinecraftServer server;
    private static int originalViewDistance;
    private static int appliedViewDistance;

    private PlayerLoadDistanceManager() {}

    public static synchronized void load(MinecraftServer current) {
        server = current;
        originalViewDistance = current.getPlayerList().getViewDistance();
        appliedViewDistance = originalViewDistance;
        TEMPORARY.clear();
        TICKETS.clear();
        APPLIED.clear();
        PlayerLoadDistanceConfig.load(current);
        applyAll();
    }

    public static synchronized boolean enabled() {
        return server != null && !"false".equals(FGASettings.playerLoadDistance);
    }

    public static synchronized void onRuleChanged() {
        if (server != null) applyAll();
    }

    public static synchronized void tick(MinecraftServer current) {
        if (server != current) return;
        updateGlobalViewDistance();
        for (ServerPlayer player : current.getPlayerList().getPlayers()) apply(player);
        APPLIED.keySet().removeIf(uuid -> current.getPlayerList().getPlayer(uuid) == null);
    }

    public static synchronized void set(ServerPlayer player, int distance, boolean persistent) throws Exception {
        if (persistent) {
            TEMPORARY.remove(player.getUUID());
            PlayerLoadDistanceConfig.set(player.getUUID(), player.getGameProfile().getName(), distance);
        } else {
            TEMPORARY.put(player.getUUID(), distance);
        }
        applyAll();
        refreshTab();
    }

    public static synchronized void reset(ServerPlayer player, boolean persistent) throws Exception {
        TEMPORARY.remove(player.getUUID());
        if (persistent) PlayerLoadDistanceConfig.remove(player.getUUID());
        applyAll();
        refreshTab();
    }

    public static synchronized int configured(ServerPlayer player) {
        Integer temporary = TEMPORARY.get(player.getUUID());
        if (temporary != null) return temporary;
        PlayerLoadDistanceConfig.Entry stored = PlayerLoadDistanceConfig.get(player.getUUID());
        return stored == null ? Integer.MIN_VALUE : stored.distance();
    }

    public static synchronized int effective(ServerPlayer player) {
        int configured = configured(player);
        if (configured == Integer.MIN_VALUE) return Math.min(originalViewDistance, Math.max(2, player.requestedViewDistance()));
        if (configured == NONE) return NONE;
        if (configured == -1) return -1;
        if (configured == 0) return 0;
        return Math.min(configured, Math.max(2, player.requestedViewDistance()));
    }

    public static synchronized Component decorate(ServerPlayer player, Component base) {
        if (!enabled() || configured(player) == Integer.MIN_VALUE) return base;
        return Component.literal(formatDistance(configured(player)) + " ").withStyle(ChatFormatting.GRAY).append(base);
    }

    public static synchronized boolean hasOverride(ServerPlayer player) {
        return enabled() && configured(player) != Integer.MIN_VALUE;
    }

    public static synchronized String formatDistance(int distance) {
        return distance == NONE ? "none" : Integer.toString(distance);
    }

    public static int parseDistance(String raw) {
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if ("none".equals(value) || "无".equals(value)) return NONE;
        int distance = Integer.parseInt(value);
        if (distance < -1 || distance > 32) throw new IllegalArgumentException("distance must be -1, 0-32, or none");
        return distance;
    }

    public static String describeDistance(int distance) {
        return switch (distance) {
            case NONE -> "none / 无加载";
            case -1 -> "-1 / 中心弱加载";
            case 0 -> "0 / 中心强加载 + 3x3 弱加载";
            default -> formatDistance(distance) + " / 区块半径";
        };
    }

    public static synchronized void onLogin(ServerPlayer player) {
        APPLIED.remove(player.getUUID());
        applyAll();
        Map<UUID, PlayerLoadDistanceConfig.Entry> values = PlayerLoadDistanceConfig.snapshot();
        if (!values.isEmpty()) {
            StringBuilder message = new StringBuilder("持久化加载距离 / Persistent player load distances:\n");
            values.forEach((uuid, entry) -> message.append(entry.name()).append(" = ")
                    .append(describeDistance(entry.distance())).append(server.getPlayerList().getPlayer(uuid) == null ? " offline" : " online").append('\n'));
            player.sendSystemMessage(Component.literal(message.toString()).withStyle(ChatFormatting.GOLD));
        }
    }

    public static synchronized void reapply(ServerPlayer player) { if (enabled()) apply(player); }

    public static synchronized void onLogout(ServerPlayer player) {
        clearTickets(player.getUUID());
        APPLIED.remove(player.getUUID());
        refreshTab();
    }

    public static synchronized void clear() {
        if (server != null && originalViewDistance > 0) server.getPlayerList().setViewDistance(originalViewDistance);
        if (server != null) {
            server.getPlayerList().getPlayers().forEach(PlayerLoadDistanceManager::restorePlayer);
        }
        for (UUID uuid : Set.copyOf(TICKETS.keySet())) clearTickets(uuid);
        TEMPORARY.clear();
        TICKETS.clear();
        APPLIED.clear();
        PlayerLoadDistanceConfig.clear();
        server = null;
        originalViewDistance = 0;
        appliedViewDistance = 0;
    }

    private static void applyAll() {
        if (!enabled()) {
            if (server != null && originalViewDistance > 0) {
                server.getPlayerList().setViewDistance(originalViewDistance);
                server.getPlayerList().getPlayers().forEach(PlayerLoadDistanceManager::restorePlayer);
            }
            for (UUID uuid : Set.copyOf(TICKETS.keySet())) clearTickets(uuid);
            APPLIED.clear();
            refreshTab();
            return;
        }
        updateGlobalViewDistance();
        server.getPlayerList().getPlayers().forEach(PlayerLoadDistanceManager::apply);
    }

    private static void updateGlobalViewDistance() {
        if (!enabled()) return;
        int maximum = originalViewDistance;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int value = configured(player);
            if (value >= 1) maximum = Math.max(maximum, value);
        }
        maximum = Math.min(32, maximum);
        if (maximum != appliedViewDistance) {
            server.getPlayerList().setViewDistance(maximum);
            appliedViewDistance = maximum;
        }
    }

    private static void apply(ServerPlayer player) {
        if (!enabled()) return;
        ChunkMapPlayerLoadDistanceAccessor map = (ChunkMapPlayerLoadDistanceAccessor) player.serverLevel().getChunkSource().chunkMap;
        int distance = effective(player);
        AppliedState previous = APPLIED.get(player.getUUID());
        AppliedState next = new AppliedState(player.serverLevel().dimension().location().toString(), player.chunkPosition(), distance);
        if (next.equals(previous)) return;
        clearTickets(player.getUUID());
        if (distance == NONE) {
            if (!isDetachedInSameDimension(previous, next)) map.carpetFga$updatePlayerStatus(player, false);
            map.carpetFga$applyChunkTrackingView(player, ChunkTrackingView.EMPTY);
            APPLIED.put(player.getUUID(), next);
            return;
        }
        if (distance == -1 || distance == 0) {
            if (!isDetachedInSameDimension(previous, next)) map.carpetFga$updatePlayerStatus(player, false);
            TicketSet tickets = new TicketSet(player.getUUID(), player.serverLevel(), player.chunkPosition());
            DistanceManagerPlayerLoadDistanceAccessor access =
                    (DistanceManagerPlayerLoadDistanceAccessor) map.carpetFga$getDistanceManager();
            addTicket(access, tickets.center, distance == 0 ? 31 : 33, tickets.identifier);
            if (distance == 0) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    addTicket(access, new ChunkPos(tickets.center.x + dx, tickets.center.z + dz), 33, tickets.identifier);
                }
            }
            TICKETS.put(player.getUUID(), tickets);
            map.carpetFga$applyChunkTrackingView(player, ChunkTrackingView.of(tickets.center, 0));
            APPLIED.put(player.getUUID(), next);
            return;
        }
        if (isDetachedInSameDimension(previous, next)) map.carpetFga$updatePlayerStatus(player, true);
        map.carpetFga$applyChunkTrackingView(player, ChunkTrackingView.of(player.chunkPosition(), distance));
        APPLIED.put(player.getUUID(), next);
    }

    private static void addTicket(DistanceManagerPlayerLoadDistanceAccessor access, ChunkPos pos, int level, Object id) {
        access.carpetFga$addTicket(LOAD_TICKET, pos, level, id.toString());
    }

    private static void clearTickets(UUID player) {
        TicketSet tickets = TICKETS.remove(player);
        if (tickets == null) return;
        DistanceManagerPlayerLoadDistanceAccessor access =
                (DistanceManagerPlayerLoadDistanceAccessor) ((ChunkMapPlayerLoadDistanceAccessor)
                        tickets.level.getChunkSource().chunkMap).carpetFga$getDistanceManager();
        for (ChunkPos pos : tickets.positions) {
            access.carpetFga$removeTicket(LOAD_TICKET, pos, 31, tickets.identifier.toString());
            access.carpetFga$removeTicket(LOAD_TICKET, pos, 33, tickets.identifier.toString());
        }
    }

    private static void restorePlayer(ServerPlayer player) {
        clearTickets(player.getUUID());
        ChunkMapPlayerLoadDistanceAccessor map = (ChunkMapPlayerLoadDistanceAccessor) player.serverLevel().getChunkSource().chunkMap;
        if (isDetached(APPLIED.get(player.getUUID()))) map.carpetFga$updatePlayerStatus(player, true);
        map.carpetFga$applyChunkTrackingView(player, ChunkTrackingView.of(player.chunkPosition(),
                Math.min(originalViewDistance, Math.max(2, player.requestedViewDistance()))));
    }

    private static boolean isDetached(AppliedState state) {
        return state != null && state.distance <= 0;
    }

    private static boolean isDetachedInSameDimension(AppliedState previous, AppliedState next) {
        return isDetached(previous) && previous.dimension.equals(next.dimension);
    }

    private static final class TicketSet {
        private final Object identifier;
        private final net.minecraft.server.level.ServerLevel level;
        private final ChunkPos center;
        private final Set<ChunkPos> positions = new HashSet<>();

        private TicketSet(UUID player, net.minecraft.server.level.ServerLevel level, ChunkPos center) {
            this.identifier = "carpet-fga-player-load:" + player + ":" + level.dimension().location();
            this.level = level;
            this.center = center;
            this.positions.add(center);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) positions.add(new ChunkPos(center.x + dx, center.z + dz));
        }
    }

    private static void refreshTab() {
        if (server == null) return;
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket packet =
                    PlayerHealthDisplay.forReceiver(viewer, () ->
                            new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(
                                    EnumSet.of(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                                    server.getPlayerList().getPlayers()));
            viewer.connection.send(packet);
        }
    }

    private record AppliedState(String dimension, ChunkPos center, int distance) {}
}
//#endif
