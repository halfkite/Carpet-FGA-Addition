//#if MC >= 1.20.1 && MC <= 1.21.5
package carpet.fga;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Stores per-player End portal preferences. Missing entries always mean allow. */
public final class PlayerTpEndControlManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/player-tp-end-control");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Entry> PREFERENCES = new LinkedHashMap<>();
    private static final Map<UUID, EnumMap<PortalType, Long>> LAST_NOTICE = new LinkedHashMap<>();
    private static Path path;
    private static boolean loadFailed;

    private PlayerTpEndControlManager() {}

    public enum PortalType {
        ENTER("enter", "进入末地门 / enter End portal"),
        EXIT("exit", "末地主岛出口 / End exit portal"),
        GATEWAY("gateway", "末地折跃门 / End gateway");

        private final String id;
        private final String label;

        PortalType(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() { return id; }
        public String label() { return label; }

        public static PortalType parse(String value) {
            for (PortalType type : values()) if (type.id.equals(value)) return type;
            throw new IllegalArgumentException("unknown portal type / 未知门类型: " + value);
        }
    }

    public static synchronized void load(MinecraftServer server) {
        PREFERENCES.clear();
        LAST_NOTICE.clear();
        path = FGAWorldConfigPaths.current(server, "player-tp-end-control.json");
        loadFailed = false;
        if (!Files.isRegularFile(path)) return;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject players = root.getAsJsonObject("players");
            if (players == null) return;
            for (Map.Entry<String, JsonElement> item : players.entrySet()) {
                UUID uuid = UUID.fromString(item.getKey());
                JsonObject value = item.getValue().getAsJsonObject();
                String name = value.get("name").getAsString();
                EnumMap<PortalType, Boolean> settings = new EnumMap<>(PortalType.class);
                for (PortalType type : PortalType.values()) {
                    if (!value.has(type.id())) continue;
                    String permission = value.get(type.id()).getAsString();
                    if (!"allow".equals(permission) && !"deny".equals(permission)) {
                        throw new IllegalArgumentException("invalid " + type.id() + " value for " + uuid);
                    }
                    settings.put(type, "allow".equals(permission));
                }
                if (!settings.isEmpty()) PREFERENCES.put(uuid, new Entry(name, settings));
            }
            LOGGER.info("Loaded player End portal preferences from {}", path);
        } catch (Exception exception) {
            PREFERENCES.clear();
            loadFailed = true;
            LOGGER.error("Invalid player End portal configuration at {}; preserving it and using allow defaults", path, exception);
        }
    }

    public static synchronized boolean canTeleport(ServerPlayer player, PortalType type) {
        String rule = FGASettings.PlayerTpEndControl;
        if ("false".equals(rule)) return true;
        boolean allowed = !"true".equals(rule) && PREFERENCES.getOrDefault(player.getUUID(), Entry.EMPTY)
                .settings().getOrDefault(type, true);
        if (!allowed) notifyDenied(player, type);
        return allowed;
    }

    public static synchronized boolean isControlEnabled() {
        return "control".equals(FGASettings.PlayerTpEndControl);
    }

    public static synchronized boolean configured(ServerPlayer player, PortalType type) {
        return PREFERENCES.getOrDefault(player.getUUID(), Entry.EMPTY).settings().containsKey(type);
    }

    public static synchronized boolean preference(ServerPlayer player, PortalType type) {
        return PREFERENCES.getOrDefault(player.getUUID(), Entry.EMPTY).settings().getOrDefault(type, true);
    }

    public static synchronized void set(ServerPlayer player, PortalType type, boolean allow) throws IOException {
        ensureWritable();
        Entry old = PREFERENCES.getOrDefault(player.getUUID(), Entry.EMPTY);
        EnumMap<PortalType, Boolean> settings = new EnumMap<>(old.settings());
        settings.put(type, allow);
        PREFERENCES.put(player.getUUID(), new Entry(player.getGameProfile().getName(), settings));
        save();
    }

    public static synchronized void reset(ServerPlayer player, PortalType type) throws IOException {
        ensureWritable();
        Entry old = PREFERENCES.get(player.getUUID());
        if (old == null) return;
        EnumMap<PortalType, Boolean> settings = new EnumMap<>(old.settings());
        if (type == null) settings.clear(); else settings.remove(type);
        if (settings.isEmpty()) PREFERENCES.remove(player.getUUID());
        else PREFERENCES.put(player.getUUID(), new Entry(player.getGameProfile().getName(), settings));
        save();
    }

    public static synchronized boolean isLoadFailed() { return loadFailed; }

    public static synchronized void clear() {
        PREFERENCES.clear();
        LAST_NOTICE.clear();
        path = null;
        loadFailed = false;
    }

    private static void notifyDenied(ServerPlayer player, PortalType type) {
        long now = player.serverLevel().getGameTime();
        EnumMap<PortalType, Long> times = LAST_NOTICE.computeIfAbsent(player.getUUID(), ignored -> new EnumMap<>(PortalType.class));
        long previous = times.getOrDefault(type, Long.MIN_VALUE / 2);
        if (now - previous < 40L) return;
        times.put(type, now);
        player.sendSystemMessage(Component.literal("末地传送已阻止 / End portal teleport blocked: " + type.label())
                .withStyle(ChatFormatting.RED));
    }

    private static void ensureWritable() throws IOException {
        if (loadFailed) throw new IOException("configuration is invalid; repair " + path);
        if (path == null) throw new IOException("configuration is not loaded");
    }

    private static void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject players = new JsonObject();
        PREFERENCES.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(item -> {
            JsonObject value = new JsonObject();
            value.addProperty("name", item.getValue().name());
            for (Map.Entry<PortalType, Boolean> setting : item.getValue().settings().entrySet()) {
                value.addProperty(setting.getKey().id(), setting.getValue() ? "allow" : "deny");
            }
            players.add(item.getKey().toString(), value);
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

    private record Entry(String name, EnumMap<PortalType, Boolean> settings) {
        private static final Entry EMPTY = new Entry("", new EnumMap<>(PortalType.class));
    }
}
//#endif
