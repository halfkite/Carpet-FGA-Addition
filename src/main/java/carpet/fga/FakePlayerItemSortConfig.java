//#if MC >= 1.21 && MC <= 26.2
package carpet.fga;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** World-local settings for the 1.21.1 fake-player sorter. */
public final class FakePlayerItemSortConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile State state = State.defaults();
    private static volatile Path path;
    private static volatile boolean invalid;

    private FakePlayerItemSortConfig() {}

    public static synchronized void load(MinecraftServer server) {
        Path current = FGAWorldConfigPaths.current(server, "fake-player-item-sort.json");
        Path legacy = FGAWorldConfigPaths.legacy(server, "fake-player-item-sort.json");
        try {
            path = FGAWorldConfigPaths.migrate(current, legacy, FakePlayerItemSortConfig::validFile);
        } catch (IOException exception) {
            path = legacy;
        }
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                state = read(JsonParser.parseReader(reader).getAsJsonObject());
                invalid = false;
                return;
            } catch (Exception exception) {
                state = State.defaults();
                invalid = true;
                return;
            }
        }

        Map<String, String> legacyRules = readLegacyCarpetRules(server);
        state = migrateLegacyRules(legacyRules);
        invalid = false;
        if (!legacyRules.isEmpty()) {
            try {
                save(state);
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
        if (!Set.of("0", "1", "2").contains(cpu)) cpu = defaults.cpuThreads();
        String speed = old.getOrDefault("fakePlayerItemSortSpeed", defaults.speed());
        if (!Set.of("4", "8", "16").contains(speed)) speed = defaults.speed();
        return new State(defaults.whitelist(), whitelistMode, mode, defaults.prefix(), defaults.suffix(), defaults.names(),
                bool(old, "fakePlayerItemSortQuickShulker", false), language,
                bool(old, "fakePlayerItemSortShulkerRestock", false),
                bool(old, "fakePlayerItemSortCleanOpenedTarget", false), rebuild,
                bool(old, "fakePlayerItemSortDashboard", false), cpu, speed,
                defaults.initialWorkers(), defaults.cachedWorkers(), defaults.dashboardPort());
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
            state = read(JsonParser.parseReader(reader).getAsJsonObject());
            invalid = false;
        } catch (Exception exception) {
            invalid = true;
            throw new IOException("invalid configuration: " + exception.getMessage(), exception);
        }
    }

    public static State snapshot() { return state; }
    public static boolean isInvalid() { return invalid; }

    public static String nameFormat() {
        State value = state;
        if (!value.prefix().isEmpty()) return "prefix";
        if (!value.suffix().isEmpty()) return "suffix";
        return "false";
    }

    public static synchronized void setMode(String mode) throws IOException {
        if (!Set.of("summon", "quickopen").contains(mode)) throw new IOException("mode must be summon or quickopen");
        set(state.withMode(mode));
    }

    public static synchronized void setOption(String key, String value) throws IOException {
        State next = switch (key) {
            case "whitelistMode" -> state.withWhitelistMode(allowed(value, Set.of("false", "vanillaWhitelist", "modWhitelist"), key));
            case "quickShulker" -> state.withQuickShulker(booleanValue(value, key));
            case "targetLanguage" -> state.withTargetLanguage(allowed(value, Set.of("english", "chinese", "custom"), key));
            case "shulkerRestock" -> state.withShulkerRestock(booleanValue(value, key));
            case "cleanOpenedTarget" -> state.withCleanOpenedTarget(booleanValue(value, key));
            case "inventoryRebuild" -> state.withInventoryRebuild(allowed(value, Set.of("false", "true", "opall"), key));
            case "dashboard" -> state.withDashboard(booleanValue(value, key));
            case "cpuThreads" -> state.withCpuThreads(allowed(value, Set.of("0", "1", "2"), key));
            case "speed" -> state.withSpeed(allowed(value, Set.of("4", "8", "16"), key));
            default -> throw new IOException("unknown sorter setting: " + key);
        };
        set(next);
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
        set(prefix ? state.withPrefix(value) : state.withSuffix(value));
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

    private static void set(State next) throws IOException {
        if (invalid) throw new IOException("repair or move invalid configuration: " + path);
        save(next);
        state = next;
    }

    private static void save(State value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(write(value)) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static State read(JsonObject o) {
        int cpu = Math.max(1, Runtime.getRuntime().availableProcessors());
        Set<String> whitelist = strings(o, "whitelist");
        Map<String, String> names = new LinkedHashMap<>();
        if (o.has("names")) for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("names").entrySet()) names.put(e.getKey(), e.getValue().getAsString());
        String mode = enumValue(o, "mode", "quickopen", Set.of("summon", "quickopen"));
        String whitelistMode = enumValue(o, "whitelistMode", "false", Set.of("false", "vanillaWhitelist", "modWhitelist"));
        String language = enumValue(o, "targetLanguage", "english", Set.of("english", "chinese", "custom"));
        String rebuild = enumValue(o, "inventoryRebuild", "false", Set.of("false", "true", "opall"));
        String threads = enumValue(o, "cpuThreads", "0", Set.of("0", "1", "2"));
        String speed = enumValue(o, "speed", "8", Set.of("4", "8", "16"));
        return new State(whitelist, whitelistMode, mode, string(o, "prefix", ""), string(o, "suffix", ""), names,
                bool(o, "quickShulker", false), language, bool(o, "shulkerRestock", false),
                bool(o, "cleanOpenedTarget", false), rebuild, bool(o, "dashboard", false), threads, speed,
                integer(o, "initialWorkers", Math.max(1, cpu / 2)), integer(o, "cachedWorkers", Math.min(2, cpu)),
                integer(o, "dashboardPort", 8766));
    }

    private static JsonObject write(State s) {
        JsonObject o = new JsonObject();
        JsonArray a = new JsonArray(); s.whitelist().stream().sorted().forEach(a::add); o.add("whitelist", a);
        o.addProperty("whitelistMode", s.whitelistMode()); o.addProperty("mode", s.mode());
        o.addProperty("prefix", s.prefix()); o.addProperty("suffix", s.suffix());
        JsonObject n = new JsonObject(); s.names().forEach(n::addProperty); o.add("names", n);
        o.addProperty("quickShulker", s.quickShulker()); o.addProperty("targetLanguage", s.targetLanguage());
        o.addProperty("shulkerRestock", s.shulkerRestock()); o.addProperty("cleanOpenedTarget", s.cleanOpenedTarget());
        o.addProperty("inventoryRebuild", s.inventoryRebuild()); o.addProperty("dashboard", s.dashboard());
        o.addProperty("cpuThreads", s.cpuThreads()); o.addProperty("speed", s.speed());
        o.addProperty("initialWorkers", s.initialWorkers()); o.addProperty("cachedWorkers", s.cachedWorkers());
        o.addProperty("dashboardPort", s.dashboardPort());
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
                        int cachedWorkers, int dashboardPort) {
        public State {
            whitelist = Set.copyOf(whitelist);
            names = Map.copyOf(names);
            int cpu = Math.max(1, Runtime.getRuntime().availableProcessors());
            if (initialWorkers < 1 || initialWorkers > cpu || cachedWorkers < 1 || cachedWorkers > cpu) throw new IllegalArgumentException("worker count must be 1-" + cpu);
        }
        State withMode(String value) { return new State(whitelist, whitelistMode, value, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort); }
        State withWhitelistMode(String value) { return new State(whitelist, value, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort); }
        State withQuickShulker(boolean value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, value, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort); }
        State withTargetLanguage(String value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, value, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort); }
        State withShulkerRestock(boolean value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, value, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort); }
        State withCleanOpenedTarget(boolean value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, value, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort); }
        State withInventoryRebuild(String value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, value, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort); }
        State withDashboard(boolean value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, value, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort); }
        State withCpuThreads(String value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, value, speed, initialWorkers, cachedWorkers, dashboardPort); }
        State withSpeed(String value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, value, initialWorkers, cachedWorkers, dashboardPort); }
        State withWhitelist(Set<String> value) { return new State(value, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort); }
        State withPrefix(String value) { return new State(whitelist, whitelistMode, mode, value, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort); }
        State withSuffix(String value) { return new State(whitelist, whitelistMode, mode, prefix, value, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort); }
        State withNames(Map<String, String> value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, value, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, dashboardPort); }
        State withWorkers(int initial, int cached) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initial, cached, dashboardPort); }
        State withDashboardPort(int value) { return new State(whitelist, whitelistMode, mode, prefix, suffix, names, quickShulker, targetLanguage, shulkerRestock, cleanOpenedTarget, inventoryRebuild, dashboard, cpuThreads, speed, initialWorkers, cachedWorkers, value); }
        String nameFormat() { return !prefix.isEmpty() ? "prefix" : (!suffix.isEmpty() ? "suffix" : "false"); }
        static State defaults() { int cpu = Math.max(1, Runtime.getRuntime().availableProcessors()); return new State(Set.of(), "false", "quickopen", "", "", Map.of(), false, "english", false, false, "false", false, "0", "8", Math.max(1, cpu / 2), Math.min(2, cpu), 8766); }
    }
}
//#endif
