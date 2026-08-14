//#if MC == 1.21.1
package carpet.fga;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerLoadDistanceConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/player-load-distance");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Entry> PERSISTENT = new LinkedHashMap<>();
    private static Path path;
    private static boolean loadFailed;

    private PlayerLoadDistanceConfig() {}

    public static synchronized void load(MinecraftServer server) {
        PERSISTENT.clear();
        path = FGAWorldConfigPaths.current(server, "player-load-distance.json");
        loadFailed = false;
        if (!Files.isRegularFile(path)) return;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject players = root.getAsJsonObject("players");
            if (players != null) {
                for (Map.Entry<String, com.google.gson.JsonElement> raw : players.entrySet()) {
                    JsonObject value = raw.getValue().getAsJsonObject();
                    int distance = PlayerLoadDistanceManager.parseDistance(value.get("distance").getAsString());
                    String name = value.has("name") ? value.get("name").getAsString() : raw.getKey();
                    PERSISTENT.put(UUID.fromString(raw.getKey()), new Entry(name, distance));
                }
            }
            LOGGER.info("Loaded player load-distance preferences from {}", path);
        } catch (Exception exception) {
            PERSISTENT.clear();
            loadFailed = true;
            LOGGER.error("Invalid player load-distance configuration at {}; preserving it and disabling persistent settings", path, exception);
        }
    }

    public static synchronized Entry get(UUID uuid) { return PERSISTENT.get(uuid); }
    public static synchronized Map<UUID, Entry> snapshot() { return Map.copyOf(PERSISTENT); }
    public static synchronized boolean isLoadFailed() { return loadFailed; }

    public static synchronized void set(UUID uuid, String name, int distance) throws IOException {
        ensureWritable();
        PERSISTENT.put(uuid, new Entry(name, distance));
        save();
    }

    public static synchronized void remove(UUID uuid) throws IOException {
        ensureWritable();
        PERSISTENT.remove(uuid);
        save();
    }

    public static synchronized void clear() {
        PERSISTENT.clear();
        path = null;
        loadFailed = false;
    }

    private static void ensureWritable() throws IOException {
        if (loadFailed) throw new IOException("configuration is invalid; repair " + path);
        if (path == null) throw new IOException("configuration is not loaded");
    }

    private static void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject players = new JsonObject();
        PERSISTENT.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            JsonObject value = new JsonObject();
            value.addProperty("name", entry.getValue().name());
            value.addProperty("distance", PlayerLoadDistanceManager.formatDistance(entry.getValue().distance()));
            players.add(entry.getKey().toString(), value);
        });
        root.add("players", players);
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record Entry(String name, int distance) {}
}
//#endif
