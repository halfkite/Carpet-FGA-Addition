//#if MC >= 1.21.1 && MC < 26.2
package carpet.fga;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent per-target ranges for the unified item-drop pre-stacker. */
public final class DropPreStackConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/drop-pre-stack");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final double DEFAULT_RANGE = 1.0D;
    private static final double MIN_RANGE = 0.0D;
    private static final double MAX_RANGE = 16.0D;

    private static volatile State state = State.defaults();
    private static volatile Path path;
    private static volatile boolean loadFailed;

    private DropPreStackConfig() {
    }

    public static synchronized void load(MinecraftServer server) {
        path = server.getWorldPath(LevelResource.ROOT).resolve("carpet")
                .resolve("carpetfgaaddition").resolve("drop-pre-stack.json");
        if (!Files.exists(path)) {
            state = State.defaults();
            loadFailed = false;
            warnLegacyConfiguration(server);
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            state = parse(JsonParser.parseReader(reader));
            loadFailed = false;
            warnLegacyConfiguration(server);
            LOGGER.info("Loaded drop pre-stack configuration from {}", path);
        } catch (Exception exception) {
            state = State.defaults();
            loadFailed = true;
            LOGGER.error("Invalid drop pre-stack configuration at {}; preserving it and disabling new configuration", path, exception);
        }
    }

    public static State snapshot() {
        return state;
    }

    public static boolean isLoadFailed() {
        return loadFailed;
    }

    public static synchronized void clear() {
        state = State.defaults();
        path = null;
        loadFailed = false;
    }

    public static double defaultRange() {
        return DEFAULT_RANGE;
    }

    public static synchronized void addEntity(ResourceLocation id, double range) throws IOException {
        validateRange(range);
        if (state.entities().containsKey(id)) {
            throw new IllegalArgumentException("entity already configured: " + id);
        }
        Map<ResourceLocation, Double> next = new LinkedHashMap<>(state.entities());
        next.put(id, range);
        update(new State(next, state.blocks(), state.containerEntities(), state.containerBlocks()));
    }

    public static synchronized void setEntity(ResourceLocation id, double range) throws IOException {
        validateRange(range);
        Map<ResourceLocation, Double> next = new LinkedHashMap<>(state.entities());
        next.put(id, range);
        update(new State(next, state.blocks(), state.containerEntities(), state.containerBlocks()));
    }

    public static synchronized boolean removeEntity(ResourceLocation id) throws IOException {
        if (!state.entities().containsKey(id)) return false;
        Map<ResourceLocation, Double> next = new LinkedHashMap<>(state.entities());
        next.remove(id);
        update(new State(next, state.blocks(), state.containerEntities(), state.containerBlocks()));
        return true;
    }

    public static synchronized void addBlock(ResourceLocation id, double range) throws IOException {
        validateRange(range);
        if (state.blocks().containsKey(id)) {
            throw new IllegalArgumentException("item already configured: " + id);
        }
        Map<ResourceLocation, Double> next = new LinkedHashMap<>(state.blocks());
        next.put(id, range);
        update(new State(state.entities(), next, state.containerEntities(), state.containerBlocks()));
    }

    public static synchronized void setBlock(ResourceLocation id, double range) throws IOException {
        validateRange(range);
        Map<ResourceLocation, Double> next = new LinkedHashMap<>(state.blocks());
        next.put(id, range);
        update(new State(state.entities(), next, state.containerEntities(), state.containerBlocks()));
    }

    public static synchronized boolean removeBlock(ResourceLocation id) throws IOException {
        if (!state.blocks().containsKey(id)) return false;
        Map<ResourceLocation, Double> next = new LinkedHashMap<>(state.blocks());
        next.remove(id);
        update(new State(state.entities(), next, state.containerEntities(), state.containerBlocks()));
        return true;
    }

    public static Double entityRange(ResourceLocation id) {
        return state.entities().get(id);
    }

    public static Double blockRange(ResourceLocation id) {
        return state.blocks().get(id);
    }

    public static synchronized void addContainerEntity(ResourceLocation id, double range) throws IOException {
        validateRange(range);
        if (state.containerEntities().containsKey(id)) {
            throw new IllegalArgumentException("container entity already configured: " + id);
        }
        Map<ResourceLocation, Double> next = new LinkedHashMap<>(state.containerEntities());
        next.put(id, range);
        update(new State(state.entities(), state.blocks(), next, state.containerBlocks()));
    }

    public static synchronized void setContainerEntity(ResourceLocation id, double range) throws IOException {
        validateRange(range);
        Map<ResourceLocation, Double> next = new LinkedHashMap<>(state.containerEntities());
        next.put(id, range);
        update(new State(state.entities(), state.blocks(), next, state.containerBlocks()));
    }

    public static synchronized boolean removeContainerEntity(ResourceLocation id) throws IOException {
        if (!state.containerEntities().containsKey(id)) return false;
        Map<ResourceLocation, Double> next = new LinkedHashMap<>(state.containerEntities());
        next.remove(id);
        update(new State(state.entities(), state.blocks(), next, state.containerBlocks()));
        return true;
    }

    public static synchronized void addContainerBlock(ResourceLocation id, double range) throws IOException {
        validateRange(range);
        if (state.containerBlocks().containsKey(id)) {
            throw new IllegalArgumentException("container block already configured: " + id);
        }
        Map<ResourceLocation, Double> next = new LinkedHashMap<>(state.containerBlocks());
        next.put(id, range);
        update(new State(state.entities(), state.blocks(), state.containerEntities(), next));
    }

    public static synchronized void setContainerBlock(ResourceLocation id, double range) throws IOException {
        validateRange(range);
        Map<ResourceLocation, Double> next = new LinkedHashMap<>(state.containerBlocks());
        next.put(id, range);
        update(new State(state.entities(), state.blocks(), state.containerEntities(), next));
    }

    public static synchronized boolean removeContainerBlock(ResourceLocation id) throws IOException {
        if (!state.containerBlocks().containsKey(id)) return false;
        Map<ResourceLocation, Double> next = new LinkedHashMap<>(state.containerBlocks());
        next.remove(id);
        update(new State(state.entities(), state.blocks(), state.containerEntities(), next));
        return true;
    }

    public static Double containerEntityRange(ResourceLocation id) {
        return state.containerEntities().get(id);
    }

    public static Double containerBlockRange(ResourceLocation id) {
        return state.containerBlocks().get(id);
    }

    public static boolean hasLegacyConfiguration() {
        return FGASettings.hasLegacyPreStackConfiguration();
    }

    public static void warnLegacyConfiguration(MinecraftServer server) {
        if (!hasLegacyConfiguration()) return;
        String message = "FGA: legacy preStackMobDeathDrops is configured; migrate to /dropPreStack entity ... / "
                + "旧版生物掉落预堆叠规则已配置，请迁移到 /dropPreStack entity ...";
        LOGGER.warn(message);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().isOp(
                    //#if MC >= 1.21.10
                    //$$ player.nameAndId()
                    //#else
                    player.getGameProfile()
                    //#endif
            )) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }

    private static synchronized void update(State next) throws IOException {
        if (loadFailed) throw new IOException("configuration is invalid; repair or move " + path);
        if (path == null) throw new IOException("configuration is not loaded");
        save(next);
        state = next;
        DeathDropPreStackManager.clear();
    }

    private static void save(State value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(toJson(value)) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static State parse(JsonElement element) {
        if (!element.isJsonObject()) throw new IllegalArgumentException("root must be an object");
        JsonObject root = element.getAsJsonObject();
        return new State(readRanges(root, "entities", true), readRanges(root, "blocks", false),
                readRanges(root, "container_entities", true), readRangesBlocks(root, "container_blocks"));
    }

    private static Map<ResourceLocation, Double> readRanges(JsonObject root, String key, boolean entity) {
        Map<ResourceLocation, Double> result = new LinkedHashMap<>();
        if (!root.has(key)) return result;
        JsonObject values = root.getAsJsonObject(key);
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            ResourceLocation id = parseId(entry.getKey());
            if (entity && !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                throw new IllegalArgumentException("unknown entity id: " + id);
            }
            if (!entity && !BuiltInRegistries.ITEM.containsKey(id)) {
                throw new IllegalArgumentException("unknown item id: " + id);
            }
            double range = entry.getValue().getAsDouble();
            validateRange(range);
            result.put(id, range);
        }
        return result;
    }

    private static Map<ResourceLocation, Double> readRangesBlocks(JsonObject root, String key) {
        Map<ResourceLocation, Double> result = new LinkedHashMap<>();
        if (!root.has(key)) return result;
        JsonObject values = root.getAsJsonObject(key);
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            ResourceLocation id = parseId(entry.getKey());
            if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                throw new IllegalArgumentException("unknown block id: " + id);
            }
            double range = entry.getValue().getAsDouble();
            validateRange(range);
            result.put(id, range);
        }
        return result;
    }

    private static JsonObject toJson(State value) {
        JsonObject root = new JsonObject();
        root.add("entities", ranges(value.entities()));
        root.add("blocks", ranges(value.blocks()));
        root.add("container_entities", ranges(value.containerEntities()));
        root.add("container_blocks", ranges(value.containerBlocks()));
        return root;
    }

    private static JsonObject ranges(Map<ResourceLocation, Double> values) {
        JsonObject object = new JsonObject();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> object.addProperty(entry.getKey().toString(), entry.getValue()));
        return object;
    }

    public static ResourceLocation parseId(String raw) {
        String value = raw.trim();
        ResourceLocation id = ResourceLocation.tryParse(value.contains(":") ? value : "minecraft:" + value);
        if (id == null) throw new IllegalArgumentException("invalid resource id: " + raw);
        return id;
    }

    public static void validateRange(double range) {
        if (!Double.isFinite(range) || range < MIN_RANGE || range > MAX_RANGE) {
            throw new IllegalArgumentException("range must be between 0 and 16");
        }
    }

    public record State(Map<ResourceLocation, Double> entities, Map<ResourceLocation, Double> blocks,
                        Map<ResourceLocation, Double> containerEntities,
                        Map<ResourceLocation, Double> containerBlocks) {
        public State {
            entities = Map.copyOf(entities);
            blocks = Map.copyOf(blocks);
            containerEntities = Map.copyOf(containerEntities);
            containerBlocks = Map.copyOf(containerBlocks);
        }

        private static State defaults() {
            return new State(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
//#endif
