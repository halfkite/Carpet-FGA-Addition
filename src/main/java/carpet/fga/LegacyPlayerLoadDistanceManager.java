//#if MC == 1.20.1
//$$ package carpet.fga;
//$$
//$$ import carpet.fga.mixin.LegacyChunkMapPlayerLoadDistanceAccessor;
//$$ import carpet.fga.mixin.DistanceManagerPlayerLoadDistanceAccessor;
//$$ import net.minecraft.ChatFormatting;
//$$ import net.minecraft.core.SectionPos;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.server.level.ChunkMap;
//$$ import net.minecraft.server.level.ServerLevel;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.level.TicketType;
//$$ import net.minecraft.world.level.ChunkPos;
//$$ import org.apache.commons.lang3.mutable.MutableObject;
//$$
//$$ import java.util.Comparator;
//$$ import java.util.EnumSet;
//$$ import java.util.HashMap;
//$$ import java.util.HashSet;
//$$ import java.util.Map;
//$$ import java.util.Set;
//$$ import java.util.UUID;
//$$
//$$ public final class LegacyPlayerLoadDistanceManager {
//$$     private static final Map<UUID, Integer> TEMPORARY = new HashMap<>();
//$$     private static final Map<UUID, Integer> REQUESTED = new HashMap<>();
//$$     private static final Map<UUID, AppliedState> APPLIED = new HashMap<>();
//$$     private static final Map<UUID, TicketSet> TICKETS = new HashMap<>();
//$$     private static final TicketType<String> LOAD_TICKET = TicketType.create(
//$$             "carpet_fga_player_load", Comparator.<String>naturalOrder());
//$$     private static MinecraftServer server;
//$$     private static int originalViewDistance;
//$$     private static int appliedViewDistance;
//$$
//$$     private LegacyPlayerLoadDistanceManager() {
//$$     }
//$$
//$$     public static synchronized void load(MinecraftServer current) {
//$$         server = current;
//$$         originalViewDistance = current.getPlayerList().getViewDistance();
//$$         appliedViewDistance = originalViewDistance;
//$$         TEMPORARY.clear();
//$$         REQUESTED.clear();
//$$         APPLIED.clear();
//$$         TICKETS.clear();
//$$         PlayerLoadDistanceConfig.load(current);
//$$         applyAll();
//$$     }
//$$
//$$     public static synchronized boolean enabled() {
//$$         return server != null && !"false".equals(FGASettings.playerLoadDistance);
//$$     }
//$$
//$$     public static synchronized void onRuleChanged() {
//$$         if (server != null) applyAll();
//$$     }
//$$
//$$     public static synchronized void tick(MinecraftServer current) {
//$$         if (server != current) return;
//$$         updateGlobalViewDistance();
//$$         for (ServerPlayer player : current.getPlayerList().getPlayers()) apply(player);
//$$         APPLIED.keySet().removeIf(uuid -> current.getPlayerList().getPlayer(uuid) == null);
//$$     }
//$$
//$$     public static synchronized void set(ServerPlayer player, int distance, boolean persistent) throws Exception {
//$$         if (persistent) {
//$$             TEMPORARY.remove(player.getUUID());
//$$             PlayerLoadDistanceConfig.set(player.getUUID(), player.getGameProfile().getName(), distance);
//$$         } else {
//$$             TEMPORARY.put(player.getUUID(), distance);
//$$         }
//$$         applyAll();
//$$         refreshTab();
//$$     }
//$$
//$$     public static synchronized void reset(ServerPlayer player, boolean persistent) throws Exception {
//$$         TEMPORARY.remove(player.getUUID());
//$$         if (persistent) PlayerLoadDistanceConfig.remove(player.getUUID());
//$$         applyAll();
//$$         refreshTab();
//$$     }
//$$
//$$     public static synchronized int configured(ServerPlayer player) {
//$$         Integer temporary = TEMPORARY.get(player.getUUID());
//$$         if (temporary != null) return temporary;
//$$         PlayerLoadDistanceConfig.Entry stored = PlayerLoadDistanceConfig.get(player.getUUID());
//$$         return stored == null ? Integer.MIN_VALUE : stored.distance();
//$$     }
//$$
//$$     public static synchronized int effective(ServerPlayer player) {
//$$         int requested = Math.max(2, REQUESTED.getOrDefault(player.getUUID(), originalViewDistance));
//$$         int configured = configured(player);
//$$         if (configured == Integer.MIN_VALUE) return Math.min(originalViewDistance, requested);
//$$         if (configured <= 0) return configured;
//$$         return Math.min(configured, requested);
//$$     }
//$$
//$$     public static synchronized Component decorate(ServerPlayer player, Component base) {
//$$         if (!enabled() || configured(player) == Integer.MIN_VALUE) return base;
//$$         return Component.literal(PlayerLoadDistanceCompat.formatDistance(configured(player)) + " ")
//$$                 .withStyle(ChatFormatting.GRAY).append(base);
//$$     }
//$$
//$$     public static synchronized void onLogin(ServerPlayer player) {
//$$         APPLIED.remove(player.getUUID());
//$$         REQUESTED.putIfAbsent(player.getUUID(), originalViewDistance);
//$$         applyAll();
//$$         Map<UUID, PlayerLoadDistanceConfig.Entry> values = PlayerLoadDistanceConfig.snapshot();
//$$         if (!values.isEmpty()) {
//$$             StringBuilder message = new StringBuilder("持久化加载距离 / Persistent player load distances:\n");
//$$             values.forEach((uuid, entry) -> message.append(entry.name()).append(" = ")
//$$                     .append(PlayerLoadDistanceCompat.describeDistance(entry.distance()))
//$$                     .append(server.getPlayerList().getPlayer(uuid) == null ? " offline" : " online").append('\n'));
//$$             player.sendSystemMessage(Component.literal(message.toString()).withStyle(ChatFormatting.GOLD));
//$$         }
//$$     }
//$$
//$$     public static synchronized void onLogout(ServerPlayer player) {
//$$         clearTickets(player.getUUID());
//$$         APPLIED.remove(player.getUUID());
//$$         REQUESTED.remove(player.getUUID());
//$$         refreshTab();
//$$     }
//$$
//$$     public static synchronized void onClientInformation(ServerPlayer player, int requested) {
//$$         REQUESTED.put(player.getUUID(), Math.max(2, requested));
//$$         apply(player);
//$$     }
//$$
//$$     public static synchronized void reapply(ServerPlayer player) {
//$$         if (enabled()) apply(player);
//$$     }
//$$
//$$     public static synchronized boolean cancelVanillaTracking(ServerPlayer player, ChunkPos position,
//$$                                                               boolean wasInRange, boolean isInRange) {
//$$         if (!enabled() || configured(player) == Integer.MIN_VALUE) return false;
//$$         int distance = effective(player);
//$$         boolean desired = distance >= 1 && inRange(position, player.chunkPosition(), distance);
//$$         return isInRange && !desired;
//$$     }
//$$
//$$     public static synchronized void clear() {
//$$         if (server != null && originalViewDistance > 0) {
//$$             server.getPlayerList().setViewDistance(originalViewDistance);
//$$             server.getPlayerList().getPlayers().forEach(LegacyPlayerLoadDistanceManager::restorePlayer);
//$$         }
//$$         for (UUID uuid : Set.copyOf(TICKETS.keySet())) clearTickets(uuid);
//$$         TEMPORARY.clear();
//$$         REQUESTED.clear();
//$$         APPLIED.clear();
//$$         PlayerLoadDistanceConfig.clear();
//$$         server = null;
//$$         originalViewDistance = 0;
//$$         appliedViewDistance = 0;
//$$     }
//$$
//$$     private static void applyAll() {
//$$         if (!enabled()) {
//$$             if (server != null && originalViewDistance > 0) {
//$$                 server.getPlayerList().setViewDistance(originalViewDistance);
//$$                 server.getPlayerList().getPlayers().forEach(LegacyPlayerLoadDistanceManager::restorePlayer);
//$$             }
//$$             for (UUID uuid : Set.copyOf(TICKETS.keySet())) clearTickets(uuid);
//$$             APPLIED.clear();
//$$             refreshTab();
//$$             return;
//$$         }
//$$         updateGlobalViewDistance();
//$$         server.getPlayerList().getPlayers().forEach(LegacyPlayerLoadDistanceManager::apply);
//$$     }
//$$
//$$     private static void updateGlobalViewDistance() {
//$$         if (!enabled()) return;
//$$         int maximum = originalViewDistance;
//$$         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
//$$             int value = configured(player);
//$$             if (value >= 1) maximum = Math.max(maximum, value);
//$$         }
//$$         maximum = Math.min(32, maximum);
//$$         if (maximum != appliedViewDistance) {
//$$             server.getPlayerList().setViewDistance(maximum);
//$$             appliedViewDistance = maximum;
//$$         }
//$$     }
//$$
//$$     private static void apply(ServerPlayer player) {
//$$         if (!enabled()) return;
//$$         int distance = effective(player);
//$$         AppliedState previous = APPLIED.get(player.getUUID());
//$$         AppliedState next = new AppliedState(player.serverLevel(), player.chunkPosition(), distance);
//$$         if (next.equals(previous)) return;
//$$         clearTickets(player.getUUID());
//$$         LegacyChunkMapPlayerLoadDistanceAccessor map = accessor(player.serverLevel());
//$$         if (distance <= 0) {
//$$             if (previous == null || previous.distance > 0 || previous.level != next.level) {
//$$                 map.carpetFga$updatePlayerStatus(player, false);
//$$             }
//$$             if (distance != PlayerLoadDistanceCompat.NONE) addSpecialTickets(player, distance);
//$$             APPLIED.put(player.getUUID(), next);
//$$             return;
//$$         }
//$$         if (previous != null && previous.distance <= 0 && previous.level == next.level) {
//$$             map.carpetFga$updatePlayerStatus(player, true);
//$$         } else {
//$$             reconcile(player, previous, next);
//$$         }
//$$         APPLIED.put(player.getUUID(), next);
//$$     }
//$$
//$$     private static void reconcile(ServerPlayer player, AppliedState previous, AppliedState next) {
//$$         if (previous == null || previous.level != next.level || previous.distance <= 0) return;
//$$         int radius = Math.max(previous.distance, next.distance) + 1;
//$$         int minX = Math.min(previous.center.x, next.center.x) - radius;
//$$         int maxX = Math.max(previous.center.x, next.center.x) + radius;
//$$         int minZ = Math.min(previous.center.z, next.center.z) - radius;
//$$         int maxZ = Math.max(previous.center.z, next.center.z) + radius;
//$$         LegacyChunkMapPlayerLoadDistanceAccessor map = accessor(next.level);
//$$         for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
//$$             ChunkPos position = new ChunkPos(x, z);
//$$             boolean oldTracked = inRange(position, previous.center, previous.distance);
//$$             boolean newTracked = inRange(position, next.center, next.distance);
//$$             if (oldTracked != newTracked) {
//$$                 map.carpetFga$updateChunkTracking(player, position, new MutableObject<>(), oldTracked, newTracked);
//$$             }
//$$         }
//$$     }
//$$
//$$     private static boolean inRange(ChunkPos position, ChunkPos center, int distance) {
//$$         return ChunkMap.isChunkInRange(position.x, position.z, center.x, center.z, distance);
//$$     }
//$$
//$$     private static void addSpecialTickets(ServerPlayer player, int distance) {
//$$         TicketSet tickets = new TicketSet(player.getUUID(), player.serverLevel(), player.chunkPosition());
//$$         DistanceManagerPlayerLoadDistanceAccessor access = (DistanceManagerPlayerLoadDistanceAccessor)
//$$                 accessor(tickets.level).carpetFga$getDistanceManager();
//$$         addTicket(access, tickets.center, distance == 0 ? 31 : 33, tickets.identifier);
//$$         if (distance == 0) {
//$$             for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
//$$                 if (dx == 0 && dz == 0) continue;
//$$                 addTicket(access, new ChunkPos(tickets.center.x + dx, tickets.center.z + dz), 33, tickets.identifier);
//$$             }
//$$         }
//$$         TICKETS.put(player.getUUID(), tickets);
//$$     }
//$$
//$$     private static void addTicket(DistanceManagerPlayerLoadDistanceAccessor access, ChunkPos position,
//$$                                   int level, String identifier) {
//$$         access.carpetFga$addTicket(LOAD_TICKET, position, level, identifier);
//$$     }
//$$
//$$     private static void clearTickets(UUID player) {
//$$         TicketSet tickets = TICKETS.remove(player);
//$$         if (tickets == null) return;
//$$         DistanceManagerPlayerLoadDistanceAccessor access = (DistanceManagerPlayerLoadDistanceAccessor)
//$$                 accessor(tickets.level).carpetFga$getDistanceManager();
//$$         for (ChunkPos position : tickets.positions) {
//$$             access.carpetFga$removeTicket(LOAD_TICKET, position, 31, tickets.identifier);
//$$             access.carpetFga$removeTicket(LOAD_TICKET, position, 33, tickets.identifier);
//$$         }
//$$     }
//$$
//$$     private static void restorePlayer(ServerPlayer player) {
//$$         clearTickets(player.getUUID());
//$$         AppliedState previous = APPLIED.get(player.getUUID());
//$$         if (previous != null && previous.distance <= 0) accessor(player.serverLevel())
//$$                 .carpetFga$updatePlayerStatus(player, true);
//$$     }
//$$
//$$     private static LegacyChunkMapPlayerLoadDistanceAccessor accessor(ServerLevel level) {
//$$         return (LegacyChunkMapPlayerLoadDistanceAccessor) level.getChunkSource().chunkMap;
//$$     }
//$$
//$$     private static void refreshTab() {
//$$         if (server == null) return;
//$$         for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
//$$             ClientboundPlayerInfoUpdatePacket packet = PlayerHealthDisplay.forReceiver(viewer, () ->
//$$                     new ClientboundPlayerInfoUpdatePacket(
//$$                             EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
//$$                             server.getPlayerList().getPlayers()));
//$$             viewer.connection.send(packet);
//$$         }
//$$     }
//$$
//$$     private record AppliedState(ServerLevel level, ChunkPos center, int distance) {
//$$     }
//$$
//$$     private static final class TicketSet {
//$$         private final String identifier;
//$$         private final ServerLevel level;
//$$         private final ChunkPos center;
//$$         private final Set<ChunkPos> positions = new HashSet<>();
//$$
//$$         private TicketSet(UUID player, ServerLevel level, ChunkPos center) {
//$$             this.identifier = "carpet-fga-player-load:" + player + ":" + level.dimension().location();
//$$             this.level = level;
//$$             this.center = center;
//$$             for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
//$$                 positions.add(new ChunkPos(center.x + dx, center.z + dz));
//$$             }
//$$         }
//$$     }
//$$ }
//#endif
