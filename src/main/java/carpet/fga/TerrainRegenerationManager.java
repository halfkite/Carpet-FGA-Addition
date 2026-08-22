//#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.2
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
import net.minecraft.server.level.ServerLevel;
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
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/terrain-regeneration");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile boolean forceNormal;
    private static final List<Task> TASKS = new ArrayList<>();
    private static final Map<UUID, Task> DRAFTS = new LinkedHashMap<>();
    private static final List<Task> STARTUP_TASKS = new ArrayList<>();
    private static Path configPath;
    private static Path worldRoot;
    private static boolean invalid;

    private TerrainRegenerationManager() {}

    public static boolean forceNormalGeneration() { return forceNormal; }

    public static synchronized void beforeWorldLoad(MinecraftServer server) {
        clearMemory();
        worldRoot = server.getWorldPath(LevelResource.ROOT);
        configPath = worldRoot.resolve("config/carpetfgaaddition/terrain-regeneration.json");
        load();
        if (invalid) return;
        List<Task> pending = TASKS.stream()
                .filter(t -> t.status == Status.CONFIRMED || t.status == Status.PREPARED).toList();
        if (pending.isEmpty()) return;
        try {
            List<Task> confirmed = pending.stream().filter(t -> t.status == Status.CONFIRMED).toList();
            if (!confirmed.isEmpty()) {
                Path backupRoot = worldRoot.resolve("config/carpetfgaaddition/terrain-regeneration-backups")
                        .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
                Files.createDirectories(backupRoot);
                for (Task task : confirmed) {
                    prepareStorage(task, backupRoot.resolve(task.id.toString()));
                    markSources(List.of(task.id), Status.PREPARED, null);
                    save();
                }
            }
            STARTUP_TASKS.addAll(mergedConfirmedTasks());
            // Spawn chunks can be generated before loadLevel reaches TAIL. Keep normal generation
            // active throughout startup when a regeneration task may include those chunks.
            forceNormal = STARTUP_TASKS.stream().anyMatch(task -> task.type == Type.REGENERATE);
        } catch (Exception exception) {
            forceNormal = false;
            invalid = true;
            LOGGER.error("Failed to prepare terrain regeneration; no further tasks will run", exception);
        }
    }

    public static synchronized void onServerLoaded(MinecraftServer server) {
        if (configPath == null) {
            worldRoot = server.getWorldPath(LevelResource.ROOT);
            configPath = FGAWorldConfigPaths.current(server, "terrain-regeneration.json");
            load();
        }
        if (invalid || STARTUP_TASKS.isEmpty()) {
            forceNormal = false;
            return;
        }
        try {
            Map<String, Set<Long>> processed = new HashMap<>();
            for (Task task : List.copyOf(STARTUP_TASKS)) {
                ServerLevel level = level(server, task.dimension);
                if (level == null) {
                    markSources(task.sources, Status.FAILED, "dimension is not loaded");
                    continue;
                }
                try {
                    Set<Long> seen = processed.computeIfAbsent(task.type + "@" + task.dimension, ignored -> new HashSet<>());
                    if (task.type == Type.REGENERATE) regenerate(level, task, seen);
                    else clear(level, task, seen);
                    markSources(task.sources, Status.COMPLETE, null);
                } catch (Exception exception) {
                    markSources(task.sources, Status.FAILED, exception.toString());
                    LOGGER.error("Terrain task {} failed", task.id, exception);
                }
            }
            try { save(); } catch (IOException exception) { LOGGER.error("Failed to save terrain task results", exception); }
        } finally {
            STARTUP_TASKS.clear();
            forceNormal = false;
        }
    }

    public static synchronized Task draft(Type type, ResourceLocation dimension, int minX, int minZ, int maxX, int maxZ,
                                          String creator) throws IOException {
        ensureWritable();
        int minChunkX = Math.floorDiv(Math.min(minX, maxX), 16);
        int maxChunkX = Math.floorDiv(Math.max(minX, maxX), 16);
        int minChunkZ = Math.floorDiv(Math.min(minZ, maxZ), 16);
        int maxChunkZ = Math.floorDiv(Math.max(minZ, maxZ), 16);
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
        Task confirmed = draft.withStatus(Status.CONFIRMED, null);
        TASKS.add(confirmed);
        save();
        return confirmed;
    }

    public static synchronized boolean cancel(UUID id) throws IOException {
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

    private static void clearBoundaryFluids(ServerLevel level, Task task) {
        int minX = task.minBlockX();
        int maxX = task.maxBlockX();
        int minZ = task.minBlockZ();
        int maxZ = task.maxBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
            for (int x = minX - FLUID_BORDER; x <= maxX + FLUID_BORDER; x++) {
                for (int z = minZ - FLUID_BORDER; z <= maxZ + FLUID_BORDER; z++) {
                    if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) continue;
                    clearFluidAt(level, pos.set(x, y, z));
                }
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
                if (task.status == Status.DRAFT) DRAFTS.put(task.id, task); else TASKS.add(task);
            }
        } catch (Exception exception) {
            invalid = true;
            LOGGER.error("Invalid terrain regeneration configuration {}; preserving it", configPath, exception);
        }
    }

    private static void save() throws IOException {
        ensureWritable();
        Files.createDirectories(configPath.getParent());
        JsonObject root = new JsonObject(); root.addProperty("version", 1);
        JsonArray array = new JsonArray(); DRAFTS.values().forEach(t -> array.add(t.toJson())); TASKS.forEach(t -> array.add(t.toJson()));
        root.add("tasks", array);
        Path temp = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
        try { Files.move(temp, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException exception) { Files.move(temp, configPath, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static void ensureWritable() throws IOException {
        if (invalid) throw new IOException("configuration is invalid / 配置文件损坏");
        if (configPath == null) throw new IOException("configuration is not loaded / 配置尚未加载");
    }

    private static void clearMemory() {
        TASKS.clear(); DRAFTS.clear(); STARTUP_TASKS.clear(); configPath = null; worldRoot = null;
        invalid = false; forceNormal = false;
    }

    public enum Type { REGENERATE, CLEAR }
    public enum Status { DRAFT, CONFIRMED, PREPARED, COMPLETE, FAILED }

    public record Task(UUID id, Type type, String dimension, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                       Status status, String creator, long createdAt, List<UUID> sources, String error) {
        long chunks() { return (long)(maxChunkX-minChunkX+1) * (maxChunkZ-minChunkZ+1); }
        int minBlockX() { return minChunkX << 4; } int minBlockZ() { return minChunkZ << 4; }
        int maxBlockX() { return (maxChunkX << 4) + 15; } int maxBlockZ() { return (maxChunkZ << 4) + 15; }
        Task withStatus(Status value, String failure) { return new Task(id,type,dimension,minChunkX,minChunkZ,maxChunkX,maxChunkZ,value,creator,createdAt,sources,failure); }
        Task withSources(List<UUID> value) { return new Task(id,type,dimension,minChunkX,minChunkZ,maxChunkX,maxChunkZ,status,creator,createdAt,value,error); }
        boolean mergeable(Task other) {
            if(type!=other.type||!dimension.equals(other.dimension)||minChunkX>other.maxChunkX+1||maxChunkX+1<other.minChunkX||minChunkZ>other.maxChunkZ+1||maxChunkZ+1<other.minChunkZ)return false;
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
