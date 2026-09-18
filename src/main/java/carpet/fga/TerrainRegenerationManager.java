//#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.3
package carpet.fga;

import com.google.gson.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import carpet.CarpetServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
//#if MC >= 1.21
import net.minecraft.world.level.chunk.status.ChunkStatus;
//#else
//$$ import net.minecraft.world.level.chunk.ChunkStatus;
//#endif
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Persistent multi-task queue for destructive terrain regeneration and fast void clearing. */
public final class TerrainRegenerationManager {
    private static final int FLUID_BORDER = 8;
    /** Upper bound of chunks per task; keeps startup regeneration and fluid-border clearing bounded. */
    private static final long MAX_TASK_CHUNKS = 4096;
    private static final String CHUNK_LIMIT_MESSAGE =
            "task exceeds the " + MAX_TASK_CHUNKS + " chunk limit / 任务超出 " + MAX_TASK_CHUNKS + " 区块上限";
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/terrain-regeneration");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile boolean forceNormal;
    private static final List<Task> TASKS = new ArrayList<>();
    private static final Map<UUID, Task> DRAFTS = new LinkedHashMap<>();
    private static final List<Task> STARTUP_TASKS = new ArrayList<>();
    /** Live runs: confirmed tasks being applied right now, no restart needed. */
    private static final Map<UUID, LiveState> LIVE = new LinkedHashMap<>();
    /** Chunks asked for per tick and chunks generating at once, so a live run stays smooth. */
    private static final int LIVE_TICKETS_PER_TICK = 8;
    private static final int LIVE_IN_FLIGHT = 64;
    /** Chunks cleared per tick; clearing one chunk writes a lot of blocks. */
    private static final int LIVE_CLEAR_PER_TICK = 4;
    private static final TicketType<ChunkPos> LIVE_TICKET =
            TicketType.create("carpet_fga_terrain_regeneration", Comparator.comparingLong(ChunkPos::toLong));
    private static Path configPath;
    private static Path worldRoot;
    private static boolean invalid;

    private TerrainRegenerationManager() {}

    public static boolean forceNormalGeneration() { return forceNormal; }

    /** Tasks only live for the current run: a restart starts with an empty list. */
    public static synchronized void beforeWorldLoad(MinecraftServer server) {
        clearMemory();
        worldRoot = server.getWorldPath(LevelResource.ROOT);
    }

    public static synchronized void onServerLoaded(MinecraftServer server) {
        // Nothing to resume: tasks are not persisted and no longer wait for a restart.
    }

    private static final class LiveState {
        final Task task;
        final Path backup;
        /** Chunks not handed to the chunk pipeline yet. */
        final Set<Long> remaining = new HashSet<>();
        /** Chunks currently ticketed and still generating or loading. */
        final Set<Long> ticketed = new LinkedHashSet<>();
        /** Chunks that are still in memory right now, so they cannot be regenerated yet. */
        int loadedNow;
        /** Region files already copied into the backup folder. */
        final Set<String> copied = new HashSet<>();
        /** Progress bar shown to the player who started the task. */
        net.minecraft.server.level.ServerBossEvent bossBar;
        ServerPlayer owner;
        final long startedAtNanos = System.nanoTime();
        int ticksSinceReport;

        LiveState(Task task, Path backup) {
            this.task = task;
            this.backup = backup;
        }
    }

    /**
     * Applies a confirmed task right away instead of at the next restart. Regeneration needs the
     * stored chunk data gone before the chunk is loaded again, so chunks that are in memory right now
     * are waited for and reported instead of being force unloaded.
     */
    public static synchronized Task run(UUID id) throws IOException {
        ensureWritable();
        Task task = TASKS.stream().filter(t -> t.id.equals(id)).findFirst().orElse(null);
        if (task == null) throw new IllegalArgumentException("task not found / 任务不存在");
        if (LIVE.containsKey(id)) throw new IllegalArgumentException("task is already running / 任务正在执行");
        if (task.status != Status.CONFIRMED) {
            throw new IllegalArgumentException("only a confirmed task can run / 只有已确认的任务可以执行");
        }
        Path backup = worldRoot.resolve("config/carpetfgaaddition/terrain-regeneration-backups")
                .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")))
                .resolve(task.id.toString());
        Files.createDirectories(backup);
        LiveState state = new LiveState(task, backup);
        if (task.type == Type.CLEAR) {
            // Clearing also touches the fluid border just outside the box, so those chunks load too.
            forEachClearAffectedChunk(task, pos -> state.remaining.add(chunkKey(pos)));
        } else {
            forEachChunk(task, pos -> state.remaining.add(chunkKey(pos)));
        }
        LIVE.put(id, state);
        // Regeneration must not be voided by the void world generator; this used to be set by the
        // startup path only, which is gone now that tasks run live.
        if (task.type == Type.REGENERATE) forceNormal = true;
        state.owner = onlineCreator(CarpetServer.minecraft_server, task.creator);
        state.bossBar = new net.minecraft.server.level.ServerBossEvent(
                Component.literal(bossTitle(task)), net.minecraft.world.BossEvent.BossBarColor.GREEN,
                net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
        if (state.owner != null) {
            state.bossBar.addPlayer(state.owner);
            state.owner.sendSystemMessage(FGAText.text("carpet.fga.terrain_regeneration.started", shortId(task.id)));
        }
        replace(task.withStatus(Status.RUNNING, null));
        save();
        return task;
    }

    /** Advances every live run; called from the server tick. */
    public static synchronized void tick(MinecraftServer server) {
        if (LIVE.isEmpty()) return;
        Iterator<Map.Entry<UUID, LiveState>> iterator = LIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            LiveState state = iterator.next().getValue();
            ServerLevel level = level(server, state.task.dimension);
            if (level == null) {
                finishLive(server, state, Status.FAILED, "dimension is not loaded");
                iterator.remove();
                continue;
            }
            try {
                updateBossBar(state);
                boolean done = state.task.type == Type.CLEAR ? tickClear(level, state) : tickRegenerate(level, state);
                if (done) {
                    finishLive(server, state, Status.COMPLETE, null);
                    iterator.remove();
                }
            } catch (Exception exception) {
                LOGGER.error("Terrain task {} failed while running live", state.task.id, exception);
                finishLive(server, state, Status.FAILED, exception.toString());
                iterator.remove();
            }
        }
    }

    private static boolean tickRegenerate(ServerLevel level, LiveState state) throws IOException {
        state.loadedNow = 0;
        int budget = Math.min(LIVE_TICKETS_PER_TICK, LIVE_IN_FLIGHT - state.ticketed.size());
        Iterator<Long> iterator = state.remaining.iterator();
        while (iterator.hasNext() && budget > 0) {
            long key = iterator.next();
            ChunkPos pos = unpackChunk(key);
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
                // Still in memory: deleting its data now would be undone when the chunk saves on
                // unload, so skip it and report instead of waiting for the player to walk away.
                state.loadedNow++;
                iterator.remove();
                continue;
            }
            prepareChunkData(state, pos);
            level.getChunkSource().addRegionTicket(LIVE_TICKET, pos, 0, pos);
            state.ticketed.add(key);
            iterator.remove();
            budget--;
        }
        releaseGenerated(level, state);
        return state.remaining.isEmpty() && state.ticketed.isEmpty();
    }

    private static boolean tickClear(ServerLevel level, LiveState state) throws IOException {
        state.loadedNow = 0;
        int cleared = 0;
        int budget = Math.min(LIVE_TICKETS_PER_TICK, LIVE_IN_FLIGHT - state.ticketed.size());
        Iterator<Long> iterator = state.remaining.iterator();
        while (iterator.hasNext() && budget > 0) {
            long key = iterator.next();
            ChunkPos pos = unpackChunk(key);
            level.getChunkSource().addRegionTicket(LIVE_TICKET, pos, 0, pos);
            state.ticketed.add(key);
            iterator.remove();
            budget--;
        }
        Iterator<Long> tickets = state.ticketed.iterator();
        while (tickets.hasNext() && cleared < LIVE_CLEAR_PER_TICK) {
            long key = tickets.next();
            ChunkPos pos = unpackChunk(key);
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) continue;
            if (isTaskChunk(state.task, pos)) {
                backupChunkFiles(state, pos);
                clearEntireChunk(chunk, level);
                clearChunkPoi(level, pos);
                removeEntities(level, pos);
                cleared++;
            }
            level.getChunkSource().removeRegionTicket(LIVE_TICKET, pos, 0, pos);
            tickets.remove();
        }
        if (state.remaining.isEmpty() && state.ticketed.isEmpty()) {
            clearBoundaryFluids(level, state.task);
            level.getChunkSource().save(true);
            return true;
        }
        return false;
    }

    private static void releaseGenerated(ServerLevel level, LiveState state) {
        Iterator<Long> tickets = state.ticketed.iterator();
        while (tickets.hasNext()) {
            long key = tickets.next();
            ChunkPos pos = unpackChunk(key);
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) == null) continue;
            level.getChunkSource().removeRegionTicket(LIVE_TICKET, pos, 0, pos);
            tickets.remove();
        }
    }

    private static boolean isTaskChunk(Task task, ChunkPos pos) {
        return pos.x >= task.minChunkX && pos.x <= task.maxChunkX && pos.z >= task.minChunkZ && pos.z <= task.maxChunkZ;
    }

    private static void removeEntities(ServerLevel level, ChunkPos pos) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                pos.getMaxBlockX() + 1.0D, level.getMaxBuildHeight(), pos.getMaxBlockZ() + 1.0D);
        for (var entity : level.getEntities((net.minecraft.world.entity.Entity) null, box, e -> true)) {
            if (!(entity instanceof net.minecraft.server.level.ServerPlayer)) entity.discard();
        }
    }

    /** Copies the stored data of one chunk into the live run's backup folder, without deleting it. */
    private static void backupChunkFiles(LiveState state, ChunkPos pos) throws IOException {
        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                //#if MC >= 1.21
                ResourceLocation.parse(state.task.dimension));
                //#else
                //$$ new ResourceLocation(state.task.dimension));
                //#endif
        Path dimensionPath = DimensionType.getStorageFolder(key, worldRoot);
        for (String type : List.of("region", "entities", "poi")) {
            Path folder = dimensionPath.resolve(type);
            if (!Files.isDirectory(folder)) continue;
            Path region = regionFile(folder, pos);
            if (!Files.isRegularFile(region)) continue;
            copyRegionFile(folder, type, pos, state.backup, state.copied);
        }
    }

    /** Drops every point of interest of one chunk, so a cleared area keeps no stale village data. */
    private static void clearChunkPoi(ServerLevel level, ChunkPos pos) {
        var poiManager = level.getPoiManager();
        List<BlockPos> points = poiManager
                .getInChunk(type -> true, pos, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
                .map(net.minecraft.world.entity.ai.village.poi.PoiRecord::getPos)
                .toList();
        for (BlockPos point : points) poiManager.remove(point);
    }

    /** Backs up and clears the stored data of one chunk so the next load regenerates it. */
    private static void prepareChunkData(LiveState state, ChunkPos pos) throws IOException {
        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                //#if MC >= 1.21
                ResourceLocation.parse(state.task.dimension));
                //#else
                //$$ new ResourceLocation(state.task.dimension));
                //#endif
        Path dimensionPath = DimensionType.getStorageFolder(key, worldRoot);
        for (String type : List.of("region", "entities", "poi")) {
            Path folder = dimensionPath.resolve(type);
            if (!Files.isDirectory(folder)) continue;
            Path region = regionFile(folder, pos);
            if (!Files.isRegularFile(region)) continue;
            copyRegionFile(folder, type, pos, state.backup, state.copied);
            try (net.minecraft.world.level.chunk.storage.RegionFile file =
                         new net.minecraft.world.level.chunk.storage.RegionFile(
                                 //#if MC >= 1.21
                                 new net.minecraft.world.level.chunk.storage.RegionStorageInfo("fga", key, type),
                                 //#endif
                                 region, folder, false)) {
                file.clear(pos);
            }
        }
    }

    private static MinecraftServer serverOrNull() {
        return CarpetServer.minecraft_server;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String bossTitle(Task task) {
        return "地形任务 " + shortId(task.id) + " / terrain task";
    }

    /** The player who created the task, when they are still online. */
    private static ServerPlayer onlineCreator(MinecraftServer server, String name) {
        if (server == null || name == null) return null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getGameProfile().getName().equalsIgnoreCase(name)) return player;
        }
        return null;
    }

    /** Progress bar plus a chat line every five seconds, so the executor can follow the task. */
    private static void updateBossBar(LiveState state) {
        int total = (int) state.task.chunks();
        int done = total - state.remaining.size() - state.ticketed.size();
        int percent = total <= 0 ? 100 : done * 100 / total;
        if (state.bossBar != null) {
            state.bossBar.setProgress(total <= 0 ? 1.0F : Math.min(1.0F, (float) done / total));
            state.bossBar.setName(Component.literal(bossTitle(state.task) + "  " + done + "/" + total + " (" + percent + "%)"));
        }
        if (state.owner == null || state.owner.isRemoved()) return;
        if (state.ticksSinceReport++ % 100 != 0) return;
        long seconds = Math.max(1L, (System.nanoTime() - state.startedAtNanos) / 1_000_000_000L);
        state.owner.sendSystemMessage(FGAText.text("carpet.fga.terrain_regeneration.progress",
                shortId(state.task.id), done, total, percent, seconds / 60L, seconds % 60L));
    }

    private static void finishLive(MinecraftServer server, LiveState state, Status status, String error) {
        ServerLevel level = level(server, state.task.dimension);
        if (level != null) {
            for (long key : state.ticketed) {
                ChunkPos pos = unpackChunk(key);
                level.getChunkSource().removeRegionTicket(LIVE_TICKET, pos, 0, pos);
            }
        }
        state.ticketed.clear();
        long seconds = Math.max(1L, (System.nanoTime() - state.startedAtNanos) / 1_000_000_000L);
        if (state.bossBar != null) {
            state.bossBar.setProgress(1.0F);
            state.bossBar.removeAllPlayers();
            state.bossBar = null;
        }
        if (state.owner != null && !state.owner.isRemoved() && state.loadedNow > 0) {
            state.owner.sendSystemMessage(FGAText.text("carpet.fga.terrain_regeneration.skipped_loaded",
                    state.loadedNow));
        }
        if (state.owner != null && !state.owner.isRemoved()) {
            state.owner.sendSystemMessage(FGAText.text(
                    status == Status.COMPLETE
                            ? "carpet.fga.terrain_regeneration.complete"
                            : "carpet.fga.terrain_regeneration.failed_done",
                    shortId(state.task.id), state.task.chunks(), seconds / 60L, seconds % 60L));
        }
        forceNormal = LIVE.values().stream().anyMatch(other -> other.task.type == Type.REGENERATE);
        replace(state.task.withStatus(status, error));
        markSources(state.task.sources, status, error);
        try { save(); } catch (IOException exception) { LOGGER.error("Failed to save terrain task result", exception); }
    }

    private static void replace(Task task) {
        for (int i = 0; i < TASKS.size(); i++) {
            if (TASKS.get(i).id.equals(task.id)) { TASKS.set(i, task); return; }
        }
        TASKS.add(task);
    }

    private static long chunkKey(ChunkPos pos) {
        //#if MC >= 26.1.2
        //$$ return pos.pack();
        //#else
        return pos.toLong();
        //#endif
    }

    private static ChunkPos unpackChunk(long key) {
        //#if MC >= 26.1.2
        //$$ return new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key));
        //#else
        return new ChunkPos(key);
        //#endif
    }

    public static synchronized Task draft(Type type, ResourceLocation dimension, int minX, int minZ, int maxX, int maxZ,
                                          String creator) throws IOException {
        ensureWritable();
        int minChunkX = Math.floorDiv(Math.min(minX, maxX), 16);
        int maxChunkX = Math.floorDiv(Math.max(minX, maxX), 16);
        int minChunkZ = Math.floorDiv(Math.min(minZ, maxZ), 16);
        int maxChunkZ = Math.floorDiv(Math.max(minZ, maxZ), 16);
        if (spanExceedsLimit(minChunkX, minChunkZ, maxChunkX, maxChunkZ)) {
            throw new IllegalArgumentException(CHUNK_LIMIT_MESSAGE);
        }
        Task task = new Task(UUID.randomUUID(), type, dimension.toString(), minChunkX, minChunkZ, maxChunkX, maxChunkZ,
                Status.DRAFT, creator, System.currentTimeMillis(), List.of(), null);
        DRAFTS.put(task.id, task);
        save();
        return task;
    }

    public static synchronized Task confirm(UUID id) throws IOException {
        ensureWritable();
        Task draft = DRAFTS.remove(id);
        if (draft == null) throw new IllegalArgumentException("draft not found / 未找到草稿");
        if (draft.exceedsLimit()) {
            DRAFTS.put(id, draft);
            throw new IllegalArgumentException(CHUNK_LIMIT_MESSAGE);
        }
        Task confirmed = draft.withStatus(Status.CONFIRMED, null);
        TASKS.add(confirmed);
        save();
        return confirmed;
    }

    public static synchronized boolean cancel(UUID id) throws IOException {
        LiveState live = LIVE.remove(id);
        if (live != null) {
            MinecraftServer server = CarpetServer.minecraft_server;
            if (server != null) finishLive(server, live, Status.CANCELLED, null);
        }
        ensureWritable();
        boolean removed = DRAFTS.remove(id) != null;
        removed |= TASKS.removeIf(task -> task.id.equals(id)
                && (task.status == Status.CONFIRMED || task.status == Status.DRAFT));
        if (removed) save();
        return removed;
    }

    public static synchronized Task retry(UUID id) throws IOException {
        ensureWritable();
        for (int i = 0; i < TASKS.size(); i++) {
            Task task = TASKS.get(i);
            if (task.id.equals(id) && task.status == Status.FAILED) {
                Task retried = task.withStatus(Status.PREPARED, null);
                TASKS.set(i, retried);
                save();
                return retried;
            }
        }
        throw new IllegalArgumentException("failed task not found / 未找到失败任务");
    }

    /** Progress of a live run: cleared/generated chunks, chunks in flight, chunks still in memory. */
    public static synchronized int[] liveProgress(UUID id) {
        LiveState state = LIVE.get(id);
        if (state == null) return null;
        int total = (int) state.task.chunks();
        return new int[]{total - state.remaining.size() - state.ticketed.size(), total,
                state.ticketed.size(), state.loadedNow};
    }

    public static synchronized List<Task> tasks() {
        List<Task> result = new ArrayList<>(DRAFTS.values());
        result.addAll(TASKS);
        return List.copyOf(result);
    }

    public static synchronized void clear() { clearMemory(); }

    private static void regenerate(ServerLevel level, Task task, Set<Long> processed) {
        forEachChunk(task, pos -> {
            if (processed.add(
                    //#if MC >= 26.1.2
                    //$$ pos.pack()
                    //#else
                    pos.toLong()
                    //#endif
            )) level.getChunk(
                    //#if MC >= 26.1.2
                    //$$ pos.x(), pos.z(),
                    //#else
                    pos.x, pos.z,
                    //#endif
                    ChunkStatus.FULL, true);
        });
        level.getChunkSource().save(true);
    }

    private static void clear(ServerLevel level, Task task, Set<Long> processed) {
        forEachChunk(task, pos -> {
            if (!processed.add(
                    //#if MC >= 26.1.2
                    //$$ pos.pack()
                    //#else
                    pos.toLong()
                    //#endif
            )) return;
            LevelChunk chunk = level.getChunk(
                    //#if MC >= 26.1.2
                    //$$ pos.x(), pos.z()
                    //#else
                    pos.x, pos.z
                    //#endif
            );
            clearEntireChunk(chunk, level);
        });
        clearBoundaryFluids(level, task);
        level.getChunkSource().save(true);
    }

    private static boolean spanExceedsLimit(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        long spanX = (long) maxChunkX - minChunkX + 1;
        long spanZ = (long) maxChunkZ - minChunkZ + 1;
        return spanX <= 0 || spanZ <= 0 || spanX * spanZ > MAX_TASK_CHUNKS;
    }

    private static void clearBoundaryFluids(ServerLevel level, Task task) {
        int minX = task.minBlockX();
        int maxX = task.maxBlockX();
        int minZ = task.minBlockZ();
        int maxZ = task.maxBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
            // Walk only the border ring: two full-width z strips plus two x strips
            // inside the task area, instead of scanning the whole padded rectangle.
            for (int z = minZ - FLUID_BORDER; z < minZ; z++) {
                for (int x = minX - FLUID_BORDER; x <= maxX + FLUID_BORDER; x++) clearFluidAt(level, pos.set(x, y, z));
            }
            for (int z = maxZ + 1; z <= maxZ + FLUID_BORDER; z++) {
                for (int x = minX - FLUID_BORDER; x <= maxX + FLUID_BORDER; x++) clearFluidAt(level, pos.set(x, y, z));
            }
            for (int x = minX - FLUID_BORDER; x < minX; x++) {
                for (int z = minZ; z <= maxZ; z++) clearFluidAt(level, pos.set(x, y, z));
            }
            for (int x = maxX + 1; x <= maxX + FLUID_BORDER; x++) {
                for (int z = minZ; z <= maxZ; z++) clearFluidAt(level, pos.set(x, y, z));
            }
        }
    }

    private static void clearFluidAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().isEmpty()) return;
        BlockState replacement = state.hasProperty(BlockStateProperties.WATERLOGGED)
                ? state.setValue(BlockStateProperties.WATERLOGGED, false)
                : Blocks.AIR.defaultBlockState();
        level.setBlock(pos, replacement, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
    }

    private static void clearEntireChunk(LevelChunk chunk, ServerLevel level) {
        for (LevelChunkSection section : chunk.getSections()) {
            forceClearContainer(section.getStates());
            section.recalcBlockCounts();
        }
        chunk.clearAllBlockEntities();
        if (chunk.getBlockTicks() instanceof net.minecraft.world.ticks.LevelChunkTicks<?> ticks) {
            ticks.removeIf(tick -> true);
        }
        if (chunk.getFluidTicks() instanceof net.minecraft.world.ticks.LevelChunkTicks<?> ticks) {
            ticks.removeIf(tick -> true);
        }
        for (var entry : chunk.getHeightmaps()) {
            entry.getValue().setRawData(chunk, entry.getKey(), new long[entry.getValue().getRawData().length]);
        }
        chunk.initializeLightSources();
        //#if MC >= 1.21.3
        //$$ chunk.markUnsaved();
        //#else
        chunk.setUnsaved(true);
        //#endif
        chunk.setLightCorrect(false);
        for (int sectionY = level.getMinSection(); sectionY < level.getMaxSection(); sectionY++) {
            level.getLightEngine().updateSectionStatus(SectionPos.of(chunk.getPos(), sectionY), true);
        }
        level.getLightEngine().setLightEnabled(chunk.getPos(), true);
        level.getLightEngine().propagateLightSources(chunk.getPos());
        syncChunkToPlayers(chunk, level);
    }

    private static void forceClearContainer(PalettedContainer<net.minecraft.world.level.block.state.BlockState> container) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(0);
        buf.writeVarInt(Block.BLOCK_STATE_REGISTRY.getId(Blocks.AIR.defaultBlockState()));
        buf.writeVarInt(0);
        try { container.read(buf); } finally { buf.release(); }
    }

    private static void syncChunkToPlayers(LevelChunk chunk, ServerLevel level) {
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
                chunk, level.getLightEngine(), null, null);
        level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false)
                .forEach(player -> player.connection.send(packet));
    }

    private static void prepareStorage(Task task, Path backup) throws IOException {
        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                //#if MC >= 1.21
                ResourceLocation.parse(task.dimension));
                //#else
                //$$ new ResourceLocation(task.dimension));
                //#endif
        Path dimensionPath = DimensionType.getStorageFolder(key, worldRoot);
        Files.createDirectories(backup);
        Set<String> copied = new HashSet<>();
        for (String type : List.of("region", "entities", "poi")) {
            Path folder = dimensionPath.resolve(type);
            if (!Files.isDirectory(folder)) continue;
            if (task.type == Type.CLEAR && type.equals("region")) {
                forEachClearAffectedChunk(task, pos -> copyRegionFile(folder, type, pos, backup, copied));
            }
            forEachChunk(task, pos -> {
                try {
                    Path region = regionFile(folder, pos);
                    if (!Files.isRegularFile(region)) return;
                    copyRegionFile(folder, type, pos, backup, copied);
                    if (task.type == Type.CLEAR && type.equals("region")) return;
                    try (net.minecraft.world.level.chunk.storage.RegionFile file =
                                 new net.minecraft.world.level.chunk.storage.RegionFile(
                                         //#if MC >= 1.21
                                         new net.minecraft.world.level.chunk.storage.RegionStorageInfo("fga", key, type),
                                         //#endif
                                         region, folder, false)) {
                        file.clear(pos);
                    }
                } catch (IOException exception) { throw new StorageException(exception); }
            });
        }
    }

    private static void copyRegionFile(Path folder, String type, ChunkPos pos, Path backup, Set<String> copied) {
        try {
            Path region = regionFile(folder, pos);
            if (!Files.isRegularFile(region)) return;
            String keyName = type + "/" + region.getFileName();
            if (!copied.add(keyName)) return;
            Path target = backup.resolve(keyName);
            Files.createDirectories(target.getParent());
            Files.copy(region, target, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException exception) {
            throw new StorageException(exception);
        }
    }

    private static Path regionFile(Path folder, ChunkPos pos) {
        int regionX = Math.floorDiv(
                //#if MC >= 26.1.2
                //$$ pos.x(),
                //#else
                pos.x,
                //#endif
                32);
        int regionZ = Math.floorDiv(
                //#if MC >= 26.1.2
                //$$ pos.z(),
                //#else
                pos.z,
                //#endif
                32);
        return folder.resolve("r." + regionX + "." + regionZ + ".mca");
    }

    private static void forEachClearAffectedChunk(Task task, java.util.function.Consumer<ChunkPos> action) {
        for (int x = task.minChunkX - 1; x <= task.maxChunkX + 1; x++) {
            for (int z = task.minChunkZ - 1; z <= task.maxChunkZ + 1; z++) {
                action.accept(new ChunkPos(x, z));
            }
        }
    }

    private static List<Task> mergedConfirmedTasks() {
        List<Task> pending = TASKS.stream()
                .filter(t -> t.status == Status.CONFIRMED || t.status == Status.PREPARED).toList();
        List<Task> result = new ArrayList<>();
        for (Task source : pending) {
            Task current = source.withSources(List.of(source.id));
            boolean changed;
            do {
                changed = false;
                for (int i = 0; i < result.size(); i++) {
                    Task other = result.get(i);
                    if (current.mergeable(other)) {
                        result.remove(i);
                        current = current.merge(other);
                        changed = true;
                        break;
                    }
                }
            } while (changed);
            result.add(current);
        }
        return result;
    }

    private static void markSources(List<UUID> sources, Status status, String error) {
        for (int i = 0; i < TASKS.size(); i++) {
            Task task = TASKS.get(i);
            if (sources.contains(task.id)) TASKS.set(i, task.withStatus(status, error));
        }
    }

    private static ServerLevel level(MinecraftServer server, String id) {
        return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                //#if MC >= 1.21
                ResourceLocation.parse(id)));
                //#else
                //$$ new ResourceLocation(id)));
                //#endif
    }

    private static void forEachChunk(Task task, java.util.function.Consumer<ChunkPos> action) {
        try {
            for (int x = task.minChunkX; x <= task.maxChunkX; x++)
                for (int z = task.minChunkZ; z <= task.maxChunkZ; z++) action.accept(new ChunkPos(x, z));
        } catch (StorageException exception) { throw exception; }
    }

    private static void load() {
        TASKS.clear(); DRAFTS.clear(); invalid = false;
        if (!Files.isRegularFile(configPath)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(configPath, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray entries = root.has("tasks") ? root.getAsJsonArray("tasks") : new JsonArray();
            for (JsonElement element : entries) {
                Task task = Task.fromJson(element.getAsJsonObject());
                if (task.exceedsLimit()) {
                    if (task.status == Status.DRAFT) continue;
                    task = task.withStatus(Status.FAILED, CHUNK_LIMIT_MESSAGE);
                    LOGGER.error("Terrain task {} covers {} chunks and will not run", task.id, task.chunks());
                }
                if (task.status == Status.DRAFT) DRAFTS.put(task.id, task); else TASKS.add(task);
            }
        } catch (Exception exception) {
            invalid = true;
            LOGGER.error("Invalid terrain regeneration configuration {}; preserving it", configPath, exception);
        }
    }

    /** Tasks are not persisted any more: a restart starts with an empty list. */
    private static void save() throws IOException {
    }

    /** Tasks are not persisted any more, so there is no configuration file to check. */
    private static void ensureWritable() throws IOException {
    }

    private static void clearMemory() {
        TASKS.clear(); DRAFTS.clear(); STARTUP_TASKS.clear(); configPath = null; worldRoot = null;
        invalid = false; forceNormal = false;
    }

    public enum Type { REGENERATE, CLEAR }
    public enum Status { DRAFT, CONFIRMED, PREPARED, RUNNING, COMPLETE, FAILED, CANCELLED }

    public record Task(UUID id, Type type, String dimension, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                       Status status, String creator, long createdAt, List<UUID> sources, String error) {
        long chunks() {
            long spanX = (long) maxChunkX - minChunkX + 1;
            long spanZ = (long) maxChunkZ - minChunkZ + 1;
            return spanX * spanZ;
        }
        boolean exceedsLimit() { return spanExceedsLimit(minChunkX, minChunkZ, maxChunkX, maxChunkZ); }
        int minBlockX() { return minChunkX << 4; } int minBlockZ() { return minChunkZ << 4; }
        int maxBlockX() { return (maxChunkX << 4) + 15; } int maxBlockZ() { return (maxChunkZ << 4) + 15; }
        Task withStatus(Status value, String failure) { return new Task(id,type,dimension,minChunkX,minChunkZ,maxChunkX,maxChunkZ,value,creator,createdAt,sources,failure); }
        Task withSources(List<UUID> value) { return new Task(id,type,dimension,minChunkX,minChunkZ,maxChunkX,maxChunkZ,status,creator,createdAt,value,error); }
        boolean mergeable(Task other) {
            if(type!=other.type||!dimension.equals(other.dimension)||minChunkX>other.maxChunkX+1||maxChunkX+1<other.minChunkX||minChunkZ>other.maxChunkZ+1||maxChunkZ+1<other.minChunkZ)return false;
            if(chunks()+other.chunks()>MAX_TASK_CHUNKS)return false;
            long overlapX=Math.max(0,Math.min(maxChunkX,other.maxChunkX)-Math.max(minChunkX,other.minChunkX)+1L);
            long overlapZ=Math.max(0,Math.min(maxChunkZ,other.maxChunkZ)-Math.max(minChunkZ,other.minChunkZ)+1L);
            long union=chunks()+other.chunks()-overlapX*overlapZ;
            long bound=(long)(Math.max(maxChunkX,other.maxChunkX)-Math.min(minChunkX,other.minChunkX)+1)*(Math.max(maxChunkZ,other.maxChunkZ)-Math.min(minChunkZ,other.minChunkZ)+1L);
            return union==bound;
        }
        Task merge(Task other) { List<UUID> ids=new ArrayList<>(sources);ids.addAll(other.sources);return new Task(id,type,dimension,Math.min(minChunkX,other.minChunkX),Math.min(minChunkZ,other.minChunkZ),Math.max(maxChunkX,other.maxChunkX),Math.max(maxChunkZ,other.maxChunkZ),status,creator,createdAt,List.copyOf(ids),null); }
        JsonObject toJson(){JsonObject o=new JsonObject();o.addProperty("id",id.toString());o.addProperty("type",type.name().toLowerCase());o.addProperty("dimension",dimension);o.addProperty("minChunkX",minChunkX);o.addProperty("minChunkZ",minChunkZ);o.addProperty("maxChunkX",maxChunkX);o.addProperty("maxChunkZ",maxChunkZ);o.addProperty("status",status.name().toLowerCase());o.addProperty("creator",creator);o.addProperty("createdAt",createdAt);if(error!=null)o.addProperty("error",error);return o;}
        static Task fromJson(JsonObject o){return new Task(UUID.fromString(o.get("id").getAsString()),Type.valueOf(o.get("type").getAsString().toUpperCase()),o.get("dimension").getAsString(),o.get("minChunkX").getAsInt(),o.get("minChunkZ").getAsInt(),o.get("maxChunkX").getAsInt(),o.get("maxChunkZ").getAsInt(),Status.valueOf(o.get("status").getAsString().toUpperCase()),o.has("creator")?o.get("creator").getAsString():"unknown",o.has("createdAt")?o.get("createdAt").getAsLong():0,List.of(),o.has("error")?o.get("error").getAsString():null);}
    }

    private static final class StorageException extends RuntimeException { StorageException(IOException cause){super(cause);} }

}
//#endif
