package carpet.fga;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
//#if MC >= 1.17
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//#else
//$$ import org.apache.logging.log4j.Logger;
//$$ import org.apache.logging.log4j.LogManager;
//#endif

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class VehicleStopConfig {
    //#if MC >= 1.17
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/vehicle-stop");
    //#else
    //$$ private static final Logger LOGGER = LogManager.getLogger("carpet-fga-addition/vehicle-stop");
    //#endif
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<UUID, Entry> entries = Map.of();
    private static Path path;
    private static boolean loadFailed;

    private VehicleStopConfig() {
    }

    public static synchronized void load(MinecraftServer server) {
        path = FGAWorldConfigPaths.current(server, "vehicle-stop.json");
        if (!Files.isRegularFile(path)) {
            entries = Map.of();
            loadFailed = false;
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            //#if MC >= 1.18
            entries = Map.copyOf(parse(JsonParser.parseReader(reader)));
            //#else
            //$$ entries = Map.copyOf(parse(new JsonParser().parse(reader)));
            //#endif
            loadFailed = false;
            LOGGER.info("Loaded vehicle stop preferences from {}", path);
        } catch (Exception exception) {
            entries = Map.of();
            loadFailed = true;
            LOGGER.error("Invalid vehicle stop configuration at {}; preserving it and rejecting writes", path,
                    exception);
        }
    }

    public static synchronized Entry preference(UUID player) {
        return entries.getOrDefault(player, Entry.DEFAULT);
    }

    public static synchronized boolean isConfigured(UUID player) {
        return entries.containsKey(player);
    }

    public static synchronized void set(UUID player, String name, Target target, boolean enabled) throws IOException {
        Entry current = preference(player);
        boolean minecart = target == Target.MINECART || target == Target.ALL ? enabled : current.minecart();
        boolean boat = target == Target.BOAT || target == Target.ALL ? enabled : current.boat();
        Map<UUID, Entry> next = new LinkedHashMap<>(entries);
        next.put(player, new Entry(name, minecart, boat));
        update(next);
    }

    public static synchronized void reset(UUID player) throws IOException {
        Map<UUID, Entry> next = new LinkedHashMap<>(entries);
        next.remove(player);
        update(next);
    }

    public static synchronized boolean isLoadFailed() {
        return loadFailed;
    }

    public static synchronized void clear() {
        entries = Map.of();
        path = null;
        loadFailed = false;
    }

    private static void update(Map<UUID, Entry> next) throws IOException {
        if (loadFailed) throw new IOException("configuration is invalid; repair or move " + path);
        if (path == null) throw new IOException("configuration is not loaded");
        save(next);
        entries = Map.copyOf(next);
    }

    private static void save(Map<UUID, Entry> values) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(toJson(values)) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<UUID, Entry> parse(JsonElement element) {
        if (!element.isJsonObject()) throw new IllegalArgumentException("root must be an object");
        JsonObject root = element.getAsJsonObject();
        if (!root.has("players")) return Map.of();
        JsonObject players = root.getAsJsonObject("players");
        Map<UUID, Entry> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> value : players.entrySet()) {
            UUID uuid = UUID.fromString(value.getKey());
            JsonObject entry = value.getValue().getAsJsonObject();
            String name = entry.has("name") ? entry.get("name").getAsString() : uuid.toString();
            boolean minecart = entry.has("minecart") && entry.get("minecart").getAsBoolean();
            boolean boat = entry.has("boat") && entry.get("boat").getAsBoolean();
            result.put(uuid, new Entry(name, minecart, boat));
        }
        return result;
    }

    private static JsonObject toJson(Map<UUID, Entry> values) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject players = new JsonObject();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(value -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", value.getValue().name());
            entry.addProperty("minecart", value.getValue().minecart());
            entry.addProperty("boat", value.getValue().boat());
            players.add(value.getKey().toString(), entry);
        });
        root.add("players", players);
        return root;
    }

    public enum Target {
        MINECART,
        BOAT,
        ALL
    }

    public record Entry(String name, boolean minecart, boolean boat) {
        private static final Entry DEFAULT = new Entry("", false, false);
    }
}
