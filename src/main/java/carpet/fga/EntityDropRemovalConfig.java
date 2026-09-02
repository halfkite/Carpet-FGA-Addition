package carpet.fga;

//#if MC == 1.21.1
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Persistent per-entity item and equipment drop filters for Minecraft 1.21.1. */
public final class EntityDropRemovalConfig {
    public static final String ALL_EQUIPMENT = "allEquipment";
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/entity-drop-removal");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile State state = State.defaults();
    private static volatile Path path;
    private static volatile boolean loadFailed;

    private EntityDropRemovalConfig() {
    }

    public static synchronized void load(MinecraftServer server) {
        path = FGAWorldConfigPaths.current(server, "entity-drop-removal.json");
        if (!Files.isRegularFile(path)) {
            state = State.defaults();
            loadFailed = false;
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            state = parse(JsonParser.parseReader(reader));
            loadFailed = false;
            LOGGER.info("Loaded entity drop removal configuration from {}", path);
        } catch (Exception exception) {
            state = State.defaults();
            loadFailed = true;
            LOGGER.error("Invalid entity drop removal configuration at {}; preserving it and rejecting writes",
                    path, exception);
        }
    }

    public static synchronized void clear() {
        state = State.defaults();
        path = null;
        loadFailed = false;
    }

    public static State snapshot() {
        return state;
    }

    public static boolean isLoadFailed() {
        return loadFailed;
    }

    public static Entry entry(ResourceLocation entityId) {
        return state.entities().getOrDefault(entityId, Entry.EMPTY);
    }

    public static boolean shouldRemoveLoot(LivingEntity entity, ItemStack stack) {
        if (!enabled() || stack.isEmpty()) return false;
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return entry(entityId).items().contains(itemId);
    }

    public static boolean shouldRemoveEquipment(LivingEntity entity, ItemStack stack) {
        if (!enabled() || stack.isEmpty()) return false;
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Entry configured = entry(entityId);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return configured.allEquipment() || configured.items().contains(itemId);
    }

    public static boolean enabled() {
        return !"false".equalsIgnoreCase(FGASettings.entityDropRemoval);
    }

    public static boolean canUseCommand(net.minecraft.commands.CommandSourceStack source) {
        String value = FGASettings.entityDropRemoval;
        if ("false".equalsIgnoreCase(value)) return false;
        if ("true".equalsIgnoreCase(value)) return true;
        if ("ops".equalsIgnoreCase(value)) return FGACompat.hasPermission(source, 2);
        try {
            return FGACompat.hasPermission(source, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static synchronized void setItem(ResourceLocation entityId, ResourceLocation itemId) throws IOException {
        requireLoaded();
        Map<ResourceLocation, Entry> next = new LinkedHashMap<>(state.entities());
        Entry current = next.getOrDefault(entityId, Entry.EMPTY);
        Set<ResourceLocation> items = new LinkedHashSet<>(current.items());
        if (!items.add(itemId)) throw new IllegalArgumentException("item already configured: " + itemId);
        next.put(entityId, new Entry(items, current.allEquipment()));
        update(next);
    }

    public static synchronized void setAllEquipment(ResourceLocation entityId) throws IOException {
        requireLoaded();
        Map<ResourceLocation, Entry> next = new LinkedHashMap<>(state.entities());
        Entry current = next.getOrDefault(entityId, Entry.EMPTY);
        if (current.allEquipment()) throw new IllegalArgumentException("allEquipment already configured: " + entityId);
        next.put(entityId, new Entry(current.items(), true));
        update(next);
    }

    public static synchronized boolean removeItem(ResourceLocation entityId, ResourceLocation itemId) throws IOException {
        requireLoaded();
        Entry current = state.entities().get(entityId);
        if (current == null || !current.items().contains(itemId)) return false;
        Map<ResourceLocation, Entry> next = new LinkedHashMap<>(state.entities());
        Set<ResourceLocation> items = new LinkedHashSet<>(current.items());
        items.remove(itemId);
        if (items.isEmpty() && !current.allEquipment()) next.remove(entityId);
        else next.put(entityId, new Entry(items, current.allEquipment()));
        update(next);
        return true;
    }

    public static synchronized boolean removeAllEquipment(ResourceLocation entityId) throws IOException {
        requireLoaded();
        Entry current = state.entities().get(entityId);
        if (current == null || !current.allEquipment()) return false;
        Map<ResourceLocation, Entry> next = new LinkedHashMap<>(state.entities());
        if (current.items().isEmpty()) next.remove(entityId);
        else next.put(entityId, new Entry(current.items(), false));
        update(next);
        return true;
    }

    private static void requireLoaded() throws IOException {
        if (loadFailed) throw new IOException("configuration is invalid; repair or move " + path);
        if (path == null) throw new IOException("configuration is not loaded");
    }

    private static synchronized void update(Map<ResourceLocation, Entry> next) throws IOException {
        save(next);
        state = new State(next);
    }

    private static void save(Map<ResourceLocation, Entry> values) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(toJson(values)) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static State parse(JsonElement element) {
        if (!element.isJsonObject()) throw new IllegalArgumentException("root must be an object");
        JsonObject root = element.getAsJsonObject();
        if (root.has("version") && root.get("version").getAsInt() != 1) {
            throw new IllegalArgumentException("unsupported configuration version: " + root.get("version"));
        }
        Map<ResourceLocation, Entry> result = new LinkedHashMap<>();
        if (!root.has("entities")) return new State(result);
        JsonObject entities = root.getAsJsonObject("entities");
        for (Map.Entry<String, JsonElement> raw : entities.entrySet()) {
            ResourceLocation entityId = parseEntityId(raw.getKey());
            JsonObject value = raw.getValue().getAsJsonObject();
            Set<ResourceLocation> items = new LinkedHashSet<>();
            if (value.has("items")) {
                JsonArray array = value.getAsJsonArray("items");
                for (JsonElement item : array) {
                    ResourceLocation itemId = parseItemId(item.getAsString());
                    if (!items.add(itemId)) throw new IllegalArgumentException("duplicate item: " + itemId);
                }
            }
            boolean allEquipment = value.has("allEquipment") && value.get("allEquipment").getAsBoolean();
            if (items.isEmpty() && !allEquipment) throw new IllegalArgumentException("empty entity configuration: " + entityId);
            result.put(entityId, new Entry(items, allEquipment));
        }
        return new State(result);
    }

    private static JsonObject toJson(Map<ResourceLocation, Entry> values) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject entities = new JsonObject();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    JsonObject value = new JsonObject();
                    JsonArray items = new JsonArray();
                    entry.getValue().items().stream().sorted(Comparator.comparing(ResourceLocation::toString))
                            .forEach(item -> items.add(item.toString()));
                    value.add("items", items);
                    value.addProperty("allEquipment", entry.getValue().allEquipment());
                    entities.add(entry.getKey().toString(), value);
                });
        root.add("entities", entities);
        return root;
    }

    public static ResourceLocation parseEntityId(String raw) {
        ResourceLocation id = parseId(raw);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) throw new IllegalArgumentException("unknown entity id: " + id);
        return id;
    }

    public static ResourceLocation parseItemId(String raw) {
        ResourceLocation id = parseId(raw);
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("unknown item id: " + id);
        return id;
    }

    public static ResourceLocation parseItemIdQuiet(String raw) {
        try {
            ResourceLocation id = parseId(raw);
            return BuiltInRegistries.ITEM.containsKey(id) ? id : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static ResourceLocation parseId(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.contains(":")) value = "minecraft:" + value;
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalArgumentException("invalid resource id: " + raw);
        return id;
    }

    public record State(Map<ResourceLocation, Entry> entities) {
        public State {
            entities = Map.copyOf(entities);
        }

        private static State defaults() {
            return new State(Map.of());
        }
    }

    public record Entry(Set<ResourceLocation> items, boolean allEquipment) {
        private static final Entry EMPTY = new Entry(Set.of(), false);

        public Entry {
            items = Set.copyOf(items);
        }
    }
}
//#endif
