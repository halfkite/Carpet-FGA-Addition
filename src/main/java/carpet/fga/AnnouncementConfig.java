//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Per-world persistent settings for server announcements. */
public final class AnnouncementConfig {
    public static final String DEFAULT_HEADER = "服务器公告如下";
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/announcements");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern DURATION = Pattern.compile("([1-9][0-9]{0,5})([hd])");
    private static final Pattern DIMENSION = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+");
    private static final int MAX_ANNOUNCEMENTS = 100;
    private static final int MAX_CONTENT_LENGTH = 8192;
    private static final int MAX_HEADER_LENGTH = 256;

    private static volatile State state = State.defaults();
    private static Path path;
    private static boolean loadFailed;

    private AnnouncementConfig() {
    }

    public static synchronized void load(MinecraftServer server) {
        path = FGAWorldConfigPaths.current(server, "announcements.json");
        loadFailed = false;
        state = State.defaults();
        readFile();
    }

    public static synchronized void reload() throws IOException {
        if (path == null) throw new IOException("公告配置尚未加载 / Configuration not loaded");
        State previous = state;
        loadFailed = false;
        state = State.defaults();
        readFile();
        if (loadFailed) {
            state = previous;
            throw new IOException("公告配置文件格式错误，已保留原文件并拒绝覆盖");
        }
    }

    private static void readFile() {
        if (!Files.isRegularFile(path)) return;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            state = parse(JsonParser.parseReader(reader));
        } catch (Exception exception) {
            loadFailed = true;
            LOGGER.error("Invalid announcement configuration at {}; preserving it and rejecting writes", path, exception);
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

    public static synchronized void setHeader(String header) throws IOException {
        validateHeader(header);
        update(new State(header, state.announcements()));
    }

    public static synchronized Announcement create(int id, String content, String publisher) throws IOException {
        if (id <= 0) throw new IllegalArgumentException("公告编号必须为正整数");
        validateContent(content);
        if (state.announcements().containsKey(id)) throw new IllegalArgumentException("公告编号已存在：" + id);
        if (state.announcements().size() >= MAX_ANNOUNCEMENTS) throw new IllegalArgumentException("最多保存 " + MAX_ANNOUNCEMENTS + " 条公告");
        Announcement announcement = new Announcement(id, content, Instant.now().toEpochMilli(), normalizePublisher(publisher), null, true, false, null);
        TreeMap<Integer, Announcement> entries = new TreeMap<>(state.announcements());
        entries.put(id, announcement);
        update(new State(state.header(), immutable(entries)));
        return announcement;
    }

    public static synchronized Announcement create(String content, String publisher) throws IOException {
        int id = 1;
        for (int existing : state.announcements().keySet()) {
            if (existing == id) id++;
            else if (existing > id) break;
        }
        return create(id, content, publisher);
    }

    public static synchronized void delete(int id) throws IOException {
        Announcement current = require(id);
        TreeMap<Integer, Announcement> entries = new TreeMap<>(state.announcements());
        entries.remove(current.id());
        update(new State(state.header(), immutable(entries)));
    }

    public static synchronized void setEnabled(int id, boolean enabled) throws IOException {
        replace(id, current -> new Announcement(current.id(), current.content(), current.publishedAtMillis(),
                current.publisher(), current.expiresAtMillis(), enabled, current.hidden(), current.trigger()));
    }

    public static synchronized void setHidden(int id, boolean hidden) throws IOException {
        replace(id, current -> new Announcement(current.id(), current.content(), current.publishedAtMillis(),
                current.publisher(), current.expiresAtMillis(), current.enabled(), hidden, current.trigger()));
    }

    public static synchronized void setContent(int id, String content, String publisher) throws IOException {
        validateContent(content);
        replace(id, current -> new Announcement(current.id(), content, Instant.now().toEpochMilli(),
                normalizePublisher(publisher), current.expiresAtMillis(), current.enabled(), current.hidden(), current.trigger()));
    }

    public static synchronized void setExpiry(int id, String value) throws IOException {
        Long expiresAt = null;
        if (!"forever".equalsIgnoreCase(value)) {
            long lifetime = parseDurationMillis(value);
            expiresAt = Math.addExact(Instant.now().toEpochMilli(), lifetime);
        }
        final Long nextExpiry = expiresAt;
        replace(id, current -> new Announcement(current.id(), current.content(), current.publishedAtMillis(),
                current.publisher(), nextExpiry, current.enabled(), current.hidden(), current.trigger()));
    }

    public static synchronized void setJoinTrigger(int id) throws IOException {
        replace(id, current -> new Announcement(current.id(), current.content(), current.publishedAtMillis(),
                current.publisher(), current.expiresAtMillis(), current.enabled(), current.hidden(), null));
    }

    public static synchronized void setRegionTrigger(int id, String dimension, int x1, int y1, int z1,
                                                       int x2, int y2, int z2) throws IOException {
        Trigger trigger = Trigger.region(dimension, x1, y1, z1, x2, y2, z2);
        replace(id, current -> new Announcement(current.id(), current.content(), current.publishedAtMillis(),
                current.publisher(), current.expiresAtMillis(), current.enabled(), current.hidden(), trigger));
    }

    private static void replace(int id, java.util.function.UnaryOperator<Announcement> change) throws IOException {
        Announcement current = require(id);
        TreeMap<Integer, Announcement> entries = new TreeMap<>(state.announcements());
        entries.put(id, change.apply(current));
        update(new State(state.header(), immutable(entries)));
    }

    private static Announcement require(int id) {
        Announcement value = state.announcements().get(id);
        if (value == null) throw new IllegalArgumentException("找不到公告编号：" + id);
        return value;
    }

    public static long parseDurationMillis(String value) {
        Matcher matcher = DURATION.matcher(value == null ? "" : value);
        if (!matcher.matches()) throw new IllegalArgumentException("时效格式应为 forever、数字h 或 数字d，例如 12h、3d");
        long count = Long.parseLong(matcher.group(1));
        Duration duration = matcher.group(2).equals("h") ? Duration.ofHours(count) : Duration.ofDays(count);
        if (duration.compareTo(Duration.ofDays(3650)) > 0) throw new IllegalArgumentException("公告时效不能超过 3650 天");
        return duration.toMillis();
    }

    private static void update(State next) throws IOException {
        if (loadFailed) throw new IOException("公告配置文件损坏，请先修复或备份移走：" + path);
        if (path == null) throw new IOException("公告配置尚未加载 / Configuration not loaded");
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "announcements-", ".tmp");
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
        String header = root.has("header") ? root.get("header").getAsString() : DEFAULT_HEADER;
        validateHeader(header);
        if (!root.has("announcements") || !root.get("announcements").isJsonArray()) {
            throw new IllegalArgumentException("announcements must be an array");
        }
        JsonArray array = root.getAsJsonArray("announcements");
        if (array.size() > MAX_ANNOUNCEMENTS) throw new IllegalArgumentException("too many announcements");
        TreeMap<Integer, Announcement> entries = new TreeMap<>();
        for (JsonElement item : array) {
            Announcement announcement = parseAnnouncement(item);
            if (entries.put(announcement.id(), announcement) != null) throw new IllegalArgumentException("duplicate announcement id " + announcement.id());
        }
        return new State(header, immutable(entries));
    }

    private static Announcement parseAnnouncement(JsonElement element) {
        if (!element.isJsonObject()) throw new IllegalArgumentException("announcement must be an object");
        JsonObject object = element.getAsJsonObject();
        int id = object.get("id").getAsInt();
        if (id <= 0) throw new IllegalArgumentException("announcement id must be positive");
        String content = object.get("content").getAsString();
        validateContent(content);
        long publishedAt = object.get("publishedAtMillis").getAsLong();
        String publisher = normalizePublisher(object.get("publisher").getAsString());
        Long expiresAt = object.has("expiresAtMillis") && !object.get("expiresAtMillis").isJsonNull()
                ? object.get("expiresAtMillis").getAsLong() : null;
        boolean enabled = !object.has("enabled") || object.get("enabled").getAsBoolean();
        boolean hidden = object.has("hidden") && object.get("hidden").getAsBoolean();
        Trigger trigger = null;
        if (object.has("trigger") && !object.get("trigger").isJsonNull()) {
            if (!object.get("trigger").isJsonObject()) throw new IllegalArgumentException("trigger must be an object");
            trigger = parseTrigger(object.getAsJsonObject("trigger"));
        }
        return new Announcement(id, content, publishedAt, publisher, expiresAt, enabled, hidden, trigger);
    }

    private static Trigger parseTrigger(JsonObject object) {
        String type = object.get("type").getAsString();
        if ("join".equals(type)) return null;
        if (!"region".equals(type)) throw new IllegalArgumentException("unknown trigger type " + type);
        return Trigger.region(object.get("dimension").getAsString(), object.get("minX").getAsInt(),
                object.get("minY").getAsInt(), object.get("minZ").getAsInt(), object.get("maxX").getAsInt(),
                object.get("maxY").getAsInt(), object.get("maxZ").getAsInt());
    }

    private static JsonObject toJson(State value) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("header", value.header());
        JsonArray announcements = new JsonArray();
        for (Announcement announcement : value.announcements().values()) {
            JsonObject object = new JsonObject();
            object.addProperty("id", announcement.id());
            object.addProperty("content", announcement.content());
            object.addProperty("publishedAtMillis", announcement.publishedAtMillis());
            object.addProperty("publisher", announcement.publisher());
            if (announcement.expiresAtMillis() != null) object.addProperty("expiresAtMillis", announcement.expiresAtMillis());
            object.addProperty("enabled", announcement.enabled());
            object.addProperty("hidden", announcement.hidden());
            if (announcement.trigger() == null) {
                JsonObject trigger = new JsonObject();
                trigger.addProperty("type", "join");
                object.add("trigger", trigger);
            } else {
                object.add("trigger", announcement.trigger().toJson());
            }
            announcements.add(object);
        }
        root.add("announcements", announcements);
        return root;
    }

    private static void validateHeader(String header) {
        if (header == null || header.isBlank() || header.length() > MAX_HEADER_LENGTH || header.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("公告题头必须为 1 到 " + MAX_HEADER_LENGTH + " 个字符");
        }
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH || content.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("公告内容必须为 1 到 " + MAX_CONTENT_LENGTH + " 个字符且不能包含回车");
        }
    }

    private static String normalizePublisher(String publisher) {
        String value = publisher == null || publisher.isBlank() ? "服务器" : publisher;
        if (value.length() > 128 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("发布者名称最长 128 字符且不能换行");
        }
        return value;
    }

    private static Map<Integer, Announcement> immutable(Map<Integer, Announcement> source) {
        return Collections.unmodifiableMap(new TreeMap<>(source));
    }

    public record State(String header, Map<Integer, Announcement> announcements) {
        private static State defaults() {
            return new State(DEFAULT_HEADER, Collections.emptyMap());
        }
    }

    public record Announcement(int id, String content, long publishedAtMillis, String publisher,
                               Long expiresAtMillis, boolean enabled, boolean hidden, Trigger trigger) {
    }

    public record Trigger(String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public static Trigger region(String dimension, int x1, int y1, int z1, int x2, int y2, int z2) {
            String normalized = dimension == null ? "" : dimension.toLowerCase(java.util.Locale.ROOT);
            if (!DIMENSION.matcher(normalized).matches()) throw new IllegalArgumentException("维度格式无效，例如 minecraft:overworld");
            int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
            int maxX = Math.max(x1, x2), maxY = Math.max(y1, y2), maxZ = Math.max(z1, z2);
            if (Math.abs((long) minX) > 30_000_000L || Math.abs((long) maxX) > 30_000_000L
                    || Math.abs((long) minZ) > 30_000_000L || Math.abs((long) maxZ) > 30_000_000L
                    || Math.abs((long) minY) > 4096L || Math.abs((long) maxY) > 4096L) {
                throw new IllegalArgumentException("公告范围坐标超出允许界限");
            }
            return new Trigger(normalized.contains(":") ? normalized : "minecraft:" + normalized,
                    minX, minY, minZ, maxX, maxY, maxZ);
        }

        private JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("type", "region");
            object.addProperty("dimension", dimension);
            object.addProperty("minX", minX);
            object.addProperty("minY", minY);
            object.addProperty("minZ", minZ);
            object.addProperty("maxX", maxX);
            object.addProperty("maxY", maxY);
            object.addProperty("maxZ", maxZ);
            return object;
        }
    }
}
//#endif
