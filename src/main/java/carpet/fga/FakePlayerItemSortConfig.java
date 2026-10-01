//#if MC >= 1.20.1 && MC <= 26.3
package carpet.fga;

import com.google.gson.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** World-local settings and access policy for the fake-player sorter. */
public final class FakePlayerItemSortConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> SETUP_FIELDS = Set.of("language", "mode", "summonNotices", "prefix", "quickShulker",
            "autoCraft", "whitelistMode", "cleanOpenedTarget", "speed", "cpu", "dashboard");
    private static final Set<String> PERMISSION_COMMANDS = Set.of("all", "help", "setup", "status", "mode",
            "settings", "prefix", "summonNotices", "quickShulker", "autoCraft", "whitelist", "cleanOpenedTarget", "speed",
            "cpu", "stock", "permission", "format", "name", "workers", "dashboard",
            "restart", "sort"
            //#if MC == 26.3
            //$$ , "inventoryTake"
            //#endif
    );
    private static volatile State state = State.defaults();
    private static volatile Path path;
    private static volatile boolean invalid;
    private static volatile boolean dashboardLogin;
    private static volatile String dashboardPasswordHash = "";
    private static volatile Set<String> configured = Set.of();
    private static volatile Map<String, Map<String, Boolean>> permissions = Map.of();

    private FakePlayerItemSortConfig() {}

    public static synchronized void load(MinecraftServer server) {
        dashboardLogin = false;
        dashboardPasswordHash = "";
        Path current = FGAWorldConfigPaths.current(server, "fake-player-item-sort.json");
        Path legacy = FGAWorldConfigPaths.legacy(server, "fake-player-item-sort.json");
        try {
            path = FGAWorldConfigPaths.migrate(current, legacy, FakePlayerItemSortConfig::validFile);
        } catch (IOException exception) {
            path = legacy;
        }
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
                state = read(object);
                //#if MC == 26.3
                dashboardLogin = bool(object, "dashboardLogin", false);
                dashboardPasswordHash = string(object, "dashboardPasswordHash", "");
                if (dashboardLogin && !FakePlayerItemSortWebPassword.validHash(dashboardPasswordHash))
                    throw new IllegalArgumentException("invalid dashboard login password hash");
                //#endif
                configured = readConfigured(object);
                permissions = readPermissions(object);
                invalid = false;
                return;
            } catch (Exception exception) {
                state = State.defaults();
                configured = Set.of();
                permissions = Map.of();
                invalid = true;
                dashboardLogin = false;
                dashboardPasswordHash = "";
                return;
            }
        }

        Map<String, String> legacyRules = readLegacyCarpetRules(server);
        state = migrateLegacyRules(legacyRules);
        configured = Set.of();
        permissions = Map.of();
        invalid = false;
        if (!legacyRules.isEmpty()) {
            try {
                save(state, configured, permissions);
                if (!"false".equals(legacyRules.getOrDefault("fakePlayerItemSortMode", "false"))) {
                    FakePlayerItemSortManager.enableFromLegacyMigration();
                }
                System.out.println("[FGA] Migrated fake-player sorter rules from "
                        + server.getWorldPath(LevelResource.ROOT).resolve("carpet.conf") + " to " + path);
            } catch (IOException exception) {
                System.err.println("[FGA] Failed to migrate fake-player sorter rules: " + exception.getMessage());
            }
        }
    }

    private static Map<String, String> readLegacyCarpetRules(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("carpet.conf");
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) return values;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                String[] parts = trimmed.split("\\s+", 2);
                if (parts.length != 2 || !parts[0].startsWith("fakePlayerItemSort")) continue;
                values.put(parts[0], parts[1].trim());
            }
        } catch (IOException ignored) {
            // A missing or unreadable legacy file must never prevent the server from starting.
        }
        return values;
    }

    private static State migrateLegacyRules(Map<String, String> old) {
        State defaults = State.defaults();
        String mode = old.getOrDefault("fakePlayerItemSortMode", "false");
        if (!"summon".equals(mode) && !"quickopen".equals(mode)) mode = defaults.mode();
        String whitelistMode = old.getOrDefault("fakePlayerItemSortWhitelist", defaults.whitelistMode());
        if (!Set.of("false", "vanillaWhitelist", "modWhitelist").contains(whitelistMode)) whitelistMode = defaults.whitelistMode();
        String language = old.getOrDefault("fakePlayerItemSortTargetLanguage", defaults.targetLanguage());
        if (!Set.of("english", "chinese", "custom").contains(language)) language = defaults.targetLanguage();
        String rebuild = old.getOrDefault("fakePlayerItemSortInventoryRebuild", defaults.inventoryRebuild());
        if (!Set.of("false", "true", "opall").contains(rebuild)) rebuild = defaults.inventoryRebuild();
        String cpu = old.getOrDefault("fakePlayerItemSortCpuThreads", defaults.cpuThreads());
        //#if MC == 26.3
        if (!cpuThreadValues().contains(cpu)) cpu = defaults.cpuThreads();
        //#else
        //$$ if (!Set.of("0", "1", "2").contains(cpu)) cpu = defaults.cpuThreads();
        //#endif
        String speed = old.getOrDefault("fakePlayerItemSortSpeed", defaults.speed());
        if (!Set.of("4", "8", "16").contains(speed)) speed = defaults.speed();
        boolean quickShulker = bool(old, "fakePlayerItemSortQuickShulker", false);
        String prefix = defaults.prefix();
        //#if MC == 26.3
        prefix = quickShulker ? "" : "bulk_";
        //#endif
        return new State(defaults.whitelist(), whitelistMode, mode, prefix, defaults.suffix(), defaults.names(),
                quickShulker, language,
                bool(old, "fakePlayerItemSortShulkerRestock", false),
                bool(old, "fakePlayerItemSortCleanOpenedTarget", false), rebuild,
                bool(old, "fakePlayerItemSortDashboard", false), cpu, speed,
                defaults.initialWorkers(), defaults.cachedWorkers(), defaults.dashboardPort(), false);
    }

    private static boolean bool(Map<String, String> values, String key, boolean fallback) {
        String value = values.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static boolean validFile(Path candidate) {
        try (Reader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
            read(JsonParser.parseReader(reader).getAsJsonObject());
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public static synchronized void reload() throws IOException {
        if (path == null) throw new IOException("world is not loaded");
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
            state = read(object);
            configured = readConfigured(object);
            permissions = readPermissions(object);
            invalid = false;
        } catch (Exception exception) {
            invalid = true;
            throw new IOException("invalid configuration: " + exception.getMessage(), exception);
        }
    }

    public static State snapshot() { return state; }
    public static boolean isInvalid() { return invalid; }
    public static boolean isConfigured(String key) { return configured.contains(key); }
    public static boolean isSetupComplete() { return configured.containsAll(SETUP_FIELDS); }
    static Set<String> setupFields() { return SETUP_FIELDS; }
    public static Set<String> permissionCommands() { return PERMISSION_COMMANDS; }

    public static boolean canUse(CommandSourceStack source, String command) {
        if (!PERMISSION_COMMANDS.contains(command)) return false;
        if (source.getEntity() == null) return true;
        ServerPlayer player = source.getPlayer();
        if (player == null) return false;
        String playerName = player.getGameProfile().getName().toLowerCase(Locale.ROOT);
        int level = -1;
        for (int candidate = 4; candidate >= 0; candidate--) {
            if (FGACompat.hasPermission(source, candidate)) {
                level = candidate;
                break;
            }
        }
        Map<String, Boolean> commandRules = permissions.getOrDefault(command, Map.of());
        Map<String, Boolean> allRules = permissions.getOrDefault("all", Map.of());
        Boolean explicit = commandRules.get(playerName);
        if (explicit != null) return explicit;
        explicit = allRules.get(playerName);
        if (explicit != null) return explicit;
        for (int candidate = level; candidate >= 0; candidate--) {
            explicit = commandRules.get(Integer.toString(candidate));
            if (explicit != null) return explicit;
            explicit = allRules.get(Integer.toString(candidate));
            if (explicit != null) return explicit;
        }
        if (level >= 2) {
            explicit = commandRules.get("ops");
            if (explicit != null) return explicit;
            explicit = allRules.get("ops");
            if (explicit != null) return explicit;
        }
        return level >= 2;
    }

    public static synchronized void setPermission(String command, String principal, boolean allowed) throws IOException {
        if (!PERMISSION_COMMANDS.contains(command)) throw new IOException("unknown permission command");
        String normalized = normalizePrincipal(principal);
        Map<String, Map<String, Boolean>> next = new LinkedHashMap<>(permissions);
        Map<String, Boolean> rules = new LinkedHashMap<>(next.getOrDefault(command, Map.of()));
        rules.put(normalized, allowed);
        next.put(command, Map.copyOf(rules));
        save(state, configured, Map.copyOf(next));
        permissions = Map.copyOf(next);
    }

    public static Map<String, Map<String, Boolean>> permissions() { return permissions; }

    private static String normalizePrincipal(String principal) throws IOException {
        String value = principal.trim().toLowerCase(Locale.ROOT);
        if (value.equals("ops") || value.matches("[0-4]")) return value;
        if (value.isBlank() || value.length() > 64 || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IOException("permission principal must be ops, 0-4, or a player name");
        }
        return value;
    }

    public static String nameFormat() {
        State value = state;
        if (!value.prefix().isEmpty()) return "prefix";
        if (!value.suffix().isEmpty()) return "suffix";
        return "false";
    }

    public static synchronized void setMode(String mode) throws IOException {
        if (!Set.of("summon", "quickopen").contains(mode)) throw new IOException("mode must be summon or quickopen");
        set(state.withMode(mode), "mode");
    }

    public static synchronized void setOption(String key, String value) throws IOException {
        //#if MC == 26.3
        if (key.equals("dashboard")) {
            allowed(value, Set.of("false", "true", "login"), key);
            if (value.equals("login") && !FakePlayerItemSortWebPassword.validHash(dashboardPasswordHash))
                throw new IllegalArgumentException("set dashboard password first");
            boolean previous = dashboardLogin;
            dashboardLogin = value.equals("login");
            try { set(state.withDashboard(!value.equals("false")), "dashboard"); }
            catch (IOException | RuntimeException exception) { dashboardLogin = previous; throw exception; }
            return;
        }
        //#endif
        State next = switch (key) {
            case "whitelistMode" -> state.withWhitelistMode(allowed(value, Set.of("false", "vanillaWhitelist", "modWhitelist"), key));
            case "quickShulker" -> state.withQuickShulker(booleanValue(value, key));
            case "summonNotices" -> state.withSummonNotices(booleanValue(value, key));
            case "targetLanguage" -> state.withTargetLanguage(allowed(value, Set.of("english", "chinese", "custom"), key));
            case "shulkerRestock" -> state.withShulkerRestock(booleanValue(value, key));
            case "cleanOpenedTarget" -> state.withCleanOpenedTarget(booleanValue(value, key));
            case "inventoryRebuild" -> state.withInventoryRebuild(allowed(value, Set.of("false", "true", "opall"), key));
            case "dashboard" -> state.withDashboard(booleanValue(value, key));
            case "cpuThreads" -> state.withCpuThreads(allowed(value,
                    //#if MC == 26.3
                    cpuThreadValues()
                    //#else
                    //$$ Set.of("0", "1", "2")
                    //#endif
                    , key));
            case "speed" -> state.withSpeed(
                    //#if MC == 26.3
                    speedValue(value)
                    //#else
                    //$$ allowed(value, Set.of("4", "8", "16"), key)
                    //#endif
            );
            default -> throw new IOException("unknown sorter setting: " + key);
        };
        String setupField = switch (key) {
            case "targetLanguage" -> "language";
            case "shulkerRestock" -> "autoCraft";
            case "cpuThreads" -> "cpu";
            default -> key;
        };
        set(next, SETUP_FIELDS.contains(setupField) ? setupField : null);
    }

    private static String speedValue(String value) {
        try {
            int ticks = Integer.parseInt(value);
            if (ticks < 1 || ticks > 120) throw new NumberFormatException();
            return Integer.toString(ticks);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("speed must be 1-120 ticks", exception);
        }
    }

    private static Set<String> cpuThreadValues() {
        Set<String> values = new HashSet<>(Set.of("0", "1", "2"));
        for (int threads = 3; threads <= 256; threads++) values.add(Integer.toString(threads));
        return values;
    }

    private static String allowed(String value, Set<String> allowed, String key) throws IOException {
        if (!allowed.contains(value)) throw new IOException(key + " must be one of " + allowed);
        return value;
    }

    private static boolean booleanValue(String value, String key) throws IOException {
        if (!"true".equals(value) && !"false".equals(value)) throw new IOException(key + " must be true or false");
        return Boolean.parseBoolean(value);
    }

    public static synchronized boolean addWhitelist(String name) throws IOException {
        String value = name.trim();
        if (value.isEmpty()) throw new IOException("player name cannot be empty");
        Set<String> values = new LinkedHashSet<>(state.whitelist());
        if (!values.add(value)) return false;
        set(state.withWhitelist(values));
        return true;
    }

    public static synchronized boolean removeWhitelist(String name) throws IOException {
        Set<String> values = new LinkedHashSet<>(state.whitelist());
        if (!values.remove(name.trim())) return false;
        set(state.withWhitelist(values));
        return true;
    }

    public static synchronized void setFormat(boolean prefix, String value) throws IOException {
        if (value.isBlank()) throw new IOException("format cannot be empty");
        if (prefix) set(state.withPrefix(value), "prefix");
        else set(state.withSuffix(value));
    }

    public static synchronized void setPrefix(String value) throws IOException {
        if (value.length() > 48 || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IOException("prefix must be at most 48 characters without spaces");
        }
        set(state.withPrefix(value), "prefix");
    }

    public static synchronized void setName(String item, String name) throws IOException {
        if (name.isBlank()) throw new IOException("name cannot be empty");
        Map<String, String> values = new LinkedHashMap<>(state.names());
        values.put(item, name.trim());
        set(state.withNames(values));
    }

    public static synchronized boolean removeName(String item) throws IOException {
        Map<String, String> values = new LinkedHashMap<>(state.names());
        if (values.remove(item) == null) return false;
        set(state.withNames(values));
        return true;
    }

    public static synchronized void setWorkers(int initial, int cached) throws IOException {
        set(state.withWorkers(initial, cached));
    }

    public static synchronized void setDashboardPort(int port) throws IOException {
        if (port < 1024 || port > 65535) throw new IOException("port must be 1024-65535");
        set(state.withDashboardPort(port));
    }

    //#if MC == 26.3
    public static String dashboardMode() { return !state.dashboard() ? "false" : dashboardLogin ? "login" : "true"; }
    public static String dashboardPasswordHash() { return dashboardPasswordHash; }
    public static synchronized void setDashboardPassword(String password) throws IOException {
        String previous = dashboardPasswordHash;
        dashboardPasswordHash = FakePlayerItemSortWebPassword.hash(password);
        try { set(state); }
        catch (IOException | RuntimeException exception) { dashboardPasswordHash = previous; throw exception; }
    }
    //#endif

    private static void set(State next) throws IOException {
        set(next, null);
    }

    private static void set(State next, String configuredField) throws IOException {
        if (invalid) throw new IOException("repair or move invalid configuration: " + path);
        Set<String> nextConfigured = configured;
        if (configuredField != null && SETUP_FIELDS.contains(configuredField) && !configured.contains(configuredField)) {
            nextConfigured = new LinkedHashSet<>(configured);
            nextConfigured.add(configuredField);
            nextConfigured = Set.copyOf(nextConfigured);
        }
        save(next, nextConfigured, permissions);
        state = next;
        configured = nextConfigured;
    }

    private static void save(State value, Set<String> configuredValues,
                             Map<String, Map<String, Boolean>> permissionValues) throws IOException {
        if (path == null) throw new IOException("world is not loaded");
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(write(value, configuredValues, permissionValues)) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static State read(JsonObject o) {
        int cpu = Math.max(1, Runtime.getRuntime().availableProcessors());
        int schema = integer(o, "schemaVersion", 1);
        Set<String> whitelist = strings(o, "whitelist");
        Map<String, String> names = new LinkedHashMap<>();
        if (o.has("names")) for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("names").entrySet()) names.put(e.getKey(), e.getValue().getAsString());
        String mode = enumValue(o, "mode", "quickopen", Set.of("summon", "quickopen"));
        String whitelistMode = enumValue(o, "whitelistMode", "false", Set.of("false", "vanillaWhitelist", "modWhitelist"));
        String language = enumValue(o, "targetLanguage", "english", Set.of("english", "chinese", "custom"));
        String rebuild = enumValue(o, "inventoryRebuild", "false", Set.of("false", "true", "opall"));
        String threads = enumValue(o, "cpuThreads", "0",
                //#if MC == 26.3
                cpuThreadValues()
                //#else
                //$$ Set.of("0", "1", "2")
                //#endif
        );
        String speed =
                //#if MC == 26.3
                speedValue(string(o, "speed", "8"));
                //#else
                //$$ enumValue(o, "speed", "8", Set.of("4", "8", "16"));
                //#endif
        boolean quickShulker = bool(o, "quickShulker", false);
        String prefix = string(o, "prefix", "");
        //#if MC == 26.3
        if (schema < 2) prefix = prefix + (quickShulker ? "" : "bulk_");
        //#endif
        return new State(whitelist, whitelistMode, mode, prefix, string(o, "suffix", ""), names,
                quickShulker, language, bool(o, "shulkerRestock", false),
                bool(o, "cleanOpenedTarget", false), rebuild, bool(o, "dashboard", false), threads, speed,
                integer(o, "initialWorkers", Math.max(1, cpu / 2)), integer(o, "cachedWorkers", Math.min(2, cpu)),
                integer(o, "dashboardPort", 8766), bool(o, "summonNotices", false));
    }

    private static Set<String> readConfigured(JsonObject o) {
        Set<String> result = new LinkedHashSet<>();
        if (!o.has("setupConfigured")) return Set.of();
        for (JsonElement value : o.getAsJsonArray("setupConfigured")) {
            String key = value.getAsString();
            if (!SETUP_FIELDS.contains(key)) throw new IllegalArgumentException("unknown setup field: " + key);
            result.add(key);
        }
        return Set.copyOf(result);
    }

    private static Map<String, Map<String, Boolean>> readPermissions(JsonObject o) {
        if (!o.has("permissions")) return Map.of();
        JsonObject configuredRules = o.getAsJsonObject("permissions");
        Map<String, Map<String, Boolean>> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> commandEntry : configuredRules.entrySet()) {
            String command = commandEntry.getKey();
            if (!PERMISSION_COMMANDS.contains(command) || !commandEntry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("invalid permission command: " + command);
            }
            Map<String, Boolean> rules = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> rule : commandEntry.getValue().getAsJsonObject().entrySet()) {
                String principal;
                try {
                    principal = normalizePrincipal(rule.getKey());
                } catch (IOException exception) {
                    throw new IllegalArgumentException(exception.getMessage(), exception);
                }
                if (!rule.getValue().isJsonPrimitive() || !rule.getValue().getAsJsonPrimitive().isBoolean()) {
                    throw new IllegalArgumentException("permission value must be boolean");
                }
                rules.put(principal, rule.getValue().getAsBoolean());
            }
            result.put(command, Map.copyOf(rules));
        }
        return Map.copyOf(result);
    }

    private static JsonObject write(State s, Set<String> configuredValues,
                                    Map<String, Map<String, Boolean>> permissionValues) {
        JsonObject o = new JsonObject();
        //#if MC == 26.3
        o.addProperty("schemaVersion", 3);
        o.addProperty("dashboardLogin", dashboardLogin);
        o.addProperty("dashboardPasswordHash", dashboardPasswordHash);
        //#endif
        JsonArray a = new JsonArray(); s.whitelist().stream().sorted().forEach(a::add); o.add("whitelist", a);
        o.addProperty("whitelistMode", s.whitelistMode()); o.addProperty("mode", s.mode());
        o.addProperty("prefix", s.prefix()); o.addProperty("suffix", s.suffix());
        JsonObject n = new JsonObject(); s.names().forEach(n::addProperty); o.add("names", n);
        o.addProperty("quickShulker", s.quickShulker()); o.addProperty("targetLanguage", s.targetLanguage());
        //#if MC == 26.3
        o.addProperty("summonNotices", s.summonNotices());
        //#endif
        o.addProperty("shulkerRestock", s.shulkerRestock()); o.addProperty("cleanOpenedTarget", s.cleanOpenedTarget());
        o.addProperty("inventoryRebuild", s.inventoryRebuild()); o.addProperty("dashboard", s.dashboard());
        o.addProperty("cpuThreads", s.cpuThreads()); o.addProperty("speed", s.speed());
        o.addProperty("initialWorkers", s.initialWorkers()); o.addProperty("cachedWorkers", s.cachedWorkers());
        o.addProperty("dashboardPort", s.dashboardPort());
        //#if MC == 26.3
        JsonArray setup = new JsonArray();
        SETUP_FIELDS.stream().filter(configuredValues::contains).sorted().forEach(setup::add);
        o.add("setupConfigured", setup);
        JsonObject access = new JsonObject();
        permissionValues.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(command -> {
            JsonObject principals = new JsonObject();
            command.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(rule -> principals.addProperty(rule.getKey(), rule.getValue()));
            access.add(command.getKey(), principals);
        });
        o.add("permissions", access);
        //#endif
        return o;
    }

    private static Set<String> strings(JsonObject o, String key) {
        Set<String> result = new LinkedHashSet<>();
        if (o.has(key)) for (JsonElement value : o.getAsJsonArray(key)) if (!result.add(value.getAsString())) throw new IllegalArgumentException("duplicate " + key);
        return Set.copyOf(result);
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) { return o.has(key) ? o.get(key).getAsBoolean() : fallback; }
    private static String string(JsonObject o, String key, String fallback) { return o.has(key) ? o.get(key).getAsString() : fallback; }
    private static int integer(JsonObject o, String key, int fallback) { return o.has(key) ? o.get(key).getAsInt() : fallback; }
    private static String enumValue(JsonObject o, String key, String fallback, Set<String> allowed) {
        String value = string(o, key, fallback);
        if (!allowed.contains(value)) throw new IllegalArgumentException(key + " must be one of " + allowed);
        return value;
    }

    public record State(Set<String> whitelist, String whitelistMode, String mode, String prefix, String suffix,
                        Map<String, String> names, boolean quickShulker, String targetLanguage,
                        boolean shulkerRestock, boolean cleanOpenedTarget, String inventoryRebuild,
                        boolean dashboard, String cpuThreads, String speed, int initialWorkers,
                        int cachedWorkers, int dashboardPort, boolean summonNotices) {
        public State {
            whitelist = Set.copyOf(whitelist);
            names = Map.copyOf(names);
            int cpu = Math.max(1, Runtime.getRuntime().availableProcessors());
            if (initialWorkers < 1 || initialWorkers > cpu || cachedWorkers < 1 || cachedWorkers > cpu) throw new IllegalArgumentException("worker count must be 1-" + cpu);
        }
        State withMode(String value) { return new State(whitelist, whitelistMode, value, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort, summonNotices); }
        State withWhitelistMode(String value) { return new State(whitelist, value, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort, summonNotices); }
        State withQuickShulker(boolean value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, value, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort, summonNotices); }
        State withTargetLanguage(String value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, value, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort, summonNotices); }
        State withShulkerRestock(boolean value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, value, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort, summonNotices); }
        State withSummonNotices(boolean value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort, value); }
        State withCleanOpenedTarget(boolean value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, value, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort, summonNotices); }
        State withInventoryRebuild(String value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, value, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort, summonNotices); }
        State withDashboard(boolean value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, value, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort, summonNotices); }
        State withCpuThreads(String value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, value, speed, initialWorkers, cachedWorkers, dashboardPort, summonNotices); }
        State withSpeed(String value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, value, initialWorkers, cachedWorkers, dashboardPort, summonNotices); }
        State withWhitelist(Set<String> value) { return new State(value, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort, summonNotices); }
        State withPrefix(String value) { return new State(whitelist, whitelistMode, mode, value, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort, summonNotices); }
        State withSuffix(String value) { return new State(whitelist, whitelistMode, mode, prefix, value, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort, summonNotices); }
        State withNames(Map<String, String> value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, value, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort, summonNotices); }
        State withWorkers(int initial, int cached) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initial, cached, dashboardPort, summonNotices); }
        State withDashboardPort(int value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, value, summonNotices); }
        String nameFormat() { return !prefix.isEmpty() ? "prefix" : (!suffix.isEmpty() ? "suffix" : "false"); }
        static State defaults() {
            int cpu = Math.max(1, Runtime.getRuntime().availableProcessors());
            String defaultPrefix = "";
            //#if MC == 26.3
            defaultPrefix = "bulk_";
            //#endif
            return new State(Set.of(), "false", "quickopen", defaultPrefix, "", Map.of(), false, "english",
                    false, false, "false", false, "0", "8", Math.max(1, cpu / 2), Math.min(2, cpu), 8766, false);
        }
    }
}
//#endif
