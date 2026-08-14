//#if MC == 1.21.1
package carpet.fga;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class MinecartFeatureConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/minecart-features");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final State DEFAULTS = new State(1.2D, 10, 0.02D, 1.0D);

    private static volatile State state = DEFAULTS;
    private static volatile Path path;
    private static volatile boolean loadFailed;

    private MinecartFeatureConfig() {
    }

    public static synchronized void load(MinecraftServer server) {
        path = FGAWorldConfigPaths.current(server, "minecart-features.json");
        if (!Files.isRegularFile(path)) {
            state = DEFAULTS;
            loadFailed = false;
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            state = parse(JsonParser.parseReader(reader));
            loadFailed = false;
            LOGGER.info("Loaded minecart feature configuration from {}", path);
        } catch (Exception exception) {
            state = DEFAULTS;
            loadFailed = true;
            LOGGER.error("Invalid minecart feature configuration at {}; preserving it and rejecting writes", path, exception);
        }
    }

    public static State snapshot() {
        return state;
    }

    public static boolean isLoadFailed() {
        return loadFailed;
    }

    public static synchronized void setFirework(double maxSpeed, int durationPerFlight, double deceleration)
            throws IOException {
        validateFirework(maxSpeed, durationPerFlight, deceleration);
        update(new State(maxSpeed, durationPerFlight, deceleration, state.chainDistance()));
    }

    public static synchronized void resetFirework() throws IOException {
        update(new State(DEFAULTS.maxSpeed(), DEFAULTS.durationPerFlight(), DEFAULTS.deceleration(),
                state.chainDistance()));
    }

    public static synchronized void setChainDistance(double distance) throws IOException {
        validateChainDistance(distance);
        update(new State(state.maxSpeed(), state.durationPerFlight(), state.deceleration(), distance));
    }

    public static synchronized void resetChain() throws IOException {
        update(new State(state.maxSpeed(), state.durationPerFlight(), state.deceleration(),
                DEFAULTS.chainDistance()));
    }

    public static synchronized void clear() {
        state = DEFAULTS;
        path = null;
        loadFailed = false;
    }

    private static synchronized void update(State next) throws IOException {
        if (loadFailed) throw new IOException("configuration is invalid; repair or move " + path);
        if (path == null) throw new IOException("configuration is not loaded");
        save(next);
        state = next;
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
        JsonObject firework = root.has("firework") ? root.getAsJsonObject("firework") : new JsonObject();
        JsonObject chain = root.has("chain") ? root.getAsJsonObject("chain") : new JsonObject();
        double maxSpeed = value(firework, "max_speed", DEFAULTS.maxSpeed());
        int duration = intValue(firework, "duration_per_flight_gt", DEFAULTS.durationPerFlight());
        double deceleration = value(firework, "deceleration", DEFAULTS.deceleration());
        double distance = value(chain, "max_distance", DEFAULTS.chainDistance());
        validateFirework(maxSpeed, duration, deceleration);
        validateChainDistance(distance);
        return new State(maxSpeed, duration, deceleration, distance);
    }

    private static JsonObject toJson(State value) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject firework = new JsonObject();
        firework.addProperty("max_speed", value.maxSpeed());
        firework.addProperty("duration_per_flight_gt", value.durationPerFlight());
        firework.addProperty("deceleration", value.deceleration());
        root.add("firework", firework);
        JsonObject chain = new JsonObject();
        chain.addProperty("max_distance", value.chainDistance());
        root.add("chain", chain);
        return root;
    }

    private static double value(JsonObject object, String key, double fallback) {
        return object.has(key) ? object.get(key).getAsDouble() : fallback;
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static void validateFirework(double maxSpeed, int duration, double deceleration) {
        if (!Double.isFinite(maxSpeed) || maxSpeed < 0.1D || maxSpeed > 4.0D) {
            throw new IllegalArgumentException("max speed must be between 0.1 and 4.0");
        }
        if (duration < 1 || duration > 24000) {
            throw new IllegalArgumentException("duration per flight must be between 1 and 24000 gt");
        }
        if (!Double.isFinite(deceleration) || deceleration < 0.001D || deceleration > 1.0D) {
            throw new IllegalArgumentException("deceleration must be between 0.001 and 1.0");
        }
    }

    private static void validateChainDistance(double distance) {
        if (!Double.isFinite(distance) || distance < 1.0D || distance > 8.0D) {
            throw new IllegalArgumentException("chain distance must be between 1.0 and 8.0");
        }
    }

    public record State(double maxSpeed, int durationPerFlight, double deceleration, double chainDistance) {
    }
}
//#endif
