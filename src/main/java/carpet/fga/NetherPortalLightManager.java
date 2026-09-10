//#if MC == 1.21.1
package carpet.fga;

import carpet.fga.mixin.ChunkMapLoadedChunksAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks portals created while the feature is active and queues light refreshes only when enabled. */
public final class NetherPortalLightManager {
    private static final String DATA_NAME = "carpet_fga_nether_portal_light";
    private static final String MODE_FALSE = "false";
    private static final String MODE_TRUE = "true";
    private static final SavedData.Factory<PortalData> FACTORY = new SavedData.Factory<>(
            PortalData::new, PortalData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private static final Map<ServerLevel, Set<Long>> PENDING_CLIENT_REFRESHES = new ConcurrentHashMap<>();
    private static boolean clientRefreshArmed;

    private NetherPortalLightManager() {
    }

    public static boolean isActive() {
        return !MODE_FALSE.equals(FGASettings.netherPortalNoLight);
    }

    public static boolean shouldSuppress(ServerLevel level, long packedPosition, BlockState state) {
        if (!isActive() || !state.is(Blocks.NETHER_PORTAL)) return false;
        if (MODE_TRUE.equals(FGASettings.netherPortalNoLight)) return true;
        return data(level).portals.contains(key(level, BlockPos.of(packedPosition)));
    }

    public static void onBlockStateChange(ServerLevel level, BlockPos position,
                                           BlockState oldState, BlockState newState) {
        if (!isActive()) return;
        boolean wasPortal = oldState.is(Blocks.NETHER_PORTAL);
        boolean isPortal = newState.is(Blocks.NETHER_PORTAL);
        if (wasPortal == isPortal) return;

        PortalData portalData = data(level);
        String portalKey = key(level, position);
        boolean changed = isPortal ? portalData.portals.add(portalKey) : portalData.portals.remove(portalKey);
        if (changed) portalData.setDirty();
        if (changed) {
            level.getChunkSource().getLightEngine().checkBlock(position);
            queueClientRefresh(level, new ChunkPos(position));
        }
    }

    /** Called after startup or an active rule change; false intentionally has no corresponding path. */
    public static void activate(MinecraftServer server) {
        if (!isActive()) return;
        for (ServerLevel level : server.getAllLevels()) refreshLoaded(level);
    }

    /** Sends vanilla light packets after the threaded server light engine has applied queued checks. */
    public static void tick(MinecraftServer server) {
        if (!isActive()) {
            PENDING_CLIENT_REFRESHES.clear();
            clientRefreshArmed = false;
            return;
        }

        if (!clientRefreshArmed) {
            if (!PENDING_CLIENT_REFRESHES.isEmpty()) clientRefreshArmed = true;
            return;
        }

        for (Map.Entry<ServerLevel, Set<Long>> entry : PENDING_CLIENT_REFRESHES.entrySet()) {
            ServerLevel level = entry.getKey();
            for (long packedChunk : entry.getValue()) {
                sendBlockLightUpdate(level, new ChunkPos(packedChunk));
            }
        }
        PENDING_CLIENT_REFRESHES.clear();
        clientRefreshArmed = false;
    }

    public static boolean hasPendingClientRefreshes(ServerLevel level) {
        return isActive() && PENDING_CLIENT_REFRESHES.containsKey(level);
    }

    /** Called from the async light worker; dispatches pending packet refreshes to the server thread. */
    public static void schedulePendingClientRefreshes() {
        if (!isActive()) return;
        for (ServerLevel level : PENDING_CLIENT_REFRESHES.keySet()) {
            if (hasPendingClientRefreshes(level)) {
                level.getServer().execute(() -> flushClientRefreshes(level));
            }
        }
    }

    /** Flushes after the threaded light engine has finished the queued recalculation. */
    public static void flushClientRefreshes(ServerLevel level) {
        if (!isActive()) return;
        Set<Long> pending = PENDING_CLIENT_REFRESHES.remove(level);
        if (pending == null) return;
        for (long packedChunk : pending) {
            sendBlockLightUpdate(level, new ChunkPos(packedChunk));
        }
        clientRefreshArmed = false;
    }

    public static void clear() {
        PENDING_CLIENT_REFRESHES.clear();
        clientRefreshArmed = false;
    }

    private static void refreshLoaded(ServerLevel level) {
        ChunkMapLoadedChunksAccessor chunks = (ChunkMapLoadedChunksAccessor) level.getChunkSource().chunkMap;
        Set<Long> seen = new HashSet<>();
        for (ChunkHolder holder : chunks.carpetFga$getLoadedChunks()) {
            LevelChunk chunk = holder.getTickingChunk();
            if (chunk == null) chunk = holder.getChunkToSend();
            if (chunk == null || !seen.add(chunk.getPos().toLong())) continue;
            boolean[] hasPortal = {false};
            chunk.findBlockLightSources((position, state) -> {
                if (state.is(Blocks.NETHER_PORTAL)) {
                    hasPortal[0] = true;
                    level.getChunkSource().getLightEngine().checkBlock(position);
                }
            });
            if (hasPortal[0]) queueClientRefresh(level, chunk.getPos());
        }
    }

    private static void queueClientRefresh(ServerLevel level, ChunkPos chunkPos) {
        Set<Long> pending = PENDING_CLIENT_REFRESHES.computeIfAbsent(level, ignored -> ConcurrentHashMap.newKeySet());
        // Portal light can cross a chunk boundary. Refresh the source chunk and
        // its immediate neighbors without forcing an unloaded chunk to load.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                pending.add(ChunkPos.asLong(chunkPos.x + dx, chunkPos.z + dz));
            }
        }
    }

    private static void sendBlockLightUpdate(ServerLevel level, ChunkPos chunkPos) {
        if (!level.hasChunk(chunkPos.x, chunkPos.z)) return;
        // Send every block-light section. A portal's light may have spread into
        // neighboring sections/chunks, so a portal-section-only packet leaves
        // stale client light data behind.
        ClientboundLightUpdatePacket packet = new ClientboundLightUpdatePacket(
                chunkPos, level.getChunkSource().getLightEngine(), new java.util.BitSet(), null);
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
            player.connection.send(packet);
        }
    }

    private static PortalData data(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static String key(ServerLevel level, BlockPos position) {
        return level.dimension().location() + "|" + position.asLong();
    }

    private static final class PortalData extends SavedData {
        private final Set<String> portals = new HashSet<>();

        private static PortalData load(CompoundTag tag, HolderLookup.Provider provider) {
            PortalData data = new PortalData();
            ListTag list = tag.getList("portals", 8);
            for (int index = 0; index < list.size(); index++) {
                String value = list.getString(index);
                if (!value.isBlank()) data.portals.add(value);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag list = new ListTag();
            for (String portal : portals) list.add(StringTag.valueOf(portal));
            tag.put("portals", list);
            return tag;
        }
    }
}
//#endif
