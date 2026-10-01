//#if MC >= 1.21 && MC <= 26.3
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Per-world settings for the configurable join notice. */
public final class JoinNoticeConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/join-notice");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_WELCOME_LENGTH = 1024;

    private static volatile State state = State.defaults();
    private static Path path;
    private static boolean loadFailed;

    private JoinNoticeConfig() {
    }

    public static synchronized void load(MinecraftServer server) {
        path = FGAWorldConfigPaths.current(server, "join-notice.json");
        state = State.defaults();
        loadFailed = false;
        if (!Files.isRegularFile(path)) return;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            state = parse(JsonParser.parseReader(reader));
        } catch (Exception exception) {
            loadFailed = true;
            LOGGER.error("Invalid join notice configuration at {}; preserving it and rejecting writes", path, exception);
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

    public static synchronized void setWelcome(String welcome) throws IOException {
        validateWelcome(welcome);
        update(new State(welcome, state.dateEnabled(), state.serverDate()));
    }

    public static synchronized void setDateEnabled(boolean enabled) throws IOException {
        if (enabled && state.serverDate() == null) {
            throw new IllegalArgumentException("请先设置开服日期 / Set the opening date first");
        }
        update(new State(state.welcome(), enabled, state.serverDate()));
    }

    public static synchronized void setServerDate(LocalDate date) throws IOException {
        update(new State(state.welcome(), state.dateEnabled(), date));
    }

    public static synchronized void clearServerDate() throws IOException {
        update(new State(state.welcome(), false, null));
    }

    private static void update(State next) throws IOException {
        if (loadFailed) throw new IOException("配置文件损坏，请先修复或备份移走：" + path);
        if (path == null) throw new IOException("配置尚未加载 / Configuration not loaded");
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "join-notice-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(toJson(next)) + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            state = next;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static State parse(JsonElement element) {
        if (!element.isJsonObject()) throw new IllegalArgumentException("root must be an object");
        JsonObject root = element.getAsJsonObject();
        String welcome = root.has("welcome") ? root.get("welcome").getAsString() : "";
        validateWelcome(welcome);
        LocalDate date = null;
        if (root.has("serverDate") && !root.get("serverDate").isJsonNull()) {
            try {
                date = LocalDate.parse(root.get("serverDate").getAsString());
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("invalid serverDate", exception);
            }
        }
        boolean enabled = root.has("dateEnabled") && root.get("dateEnabled").getAsBoolean();
        if (enabled && date == null) throw new IllegalArgumentException("dateEnabled requires serverDate");
        return new State(welcome, enabled, date);
    }

    private static JsonObject toJson(State value) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("welcome", value.welcome());
        root.addProperty("dateEnabled", value.dateEnabled());
        if (value.serverDate() != null) root.addProperty("serverDate", value.serverDate().toString());
        return root;
    }

    private static void validateWelcome(String welcome) {
        if (welcome == null || welcome.length() > MAX_WELCOME_LENGTH || welcome.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("欢迎语最长 1024 字符且不能包含回车 / Invalid welcome text");
        }
    }

    public record State(String welcome, boolean dateEnabled, LocalDate serverDate) {
        private static State defaults() {
            return new State("欢迎来到服务器，{player}！", false, null);
        }
    }
}
//#endif
