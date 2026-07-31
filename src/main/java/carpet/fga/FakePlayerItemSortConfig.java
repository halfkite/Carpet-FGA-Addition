//#if MC == 1.21.1
package carpet.fga;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Persistent, world-local settings for the sorter. Cache contents are deliberately separate. */
public final class FakePlayerItemSortConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile State state = State.defaults();
    private static volatile Path path;
    private static volatile boolean invalid;

    private FakePlayerItemSortConfig() {}

    public static synchronized void load(MinecraftServer server) {
        path = server.getWorldPath(LevelResource.ROOT).resolve("carpet").resolve("carpetfgaaddition")
                .resolve("fake-player-item-sort.json");
        if (!Files.exists(path)) { state = State.defaults(); invalid = false; return; }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            state = read(JsonParser.parseReader(reader).getAsJsonObject());
            invalid = false;
        } catch (Exception exception) {
            state = State.defaults(); invalid = true;
        }
    }

    public static synchronized void reload() throws IOException {
        if (path == null) throw new IOException("world is not loaded");
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            state = read(JsonParser.parseReader(reader).getAsJsonObject()); invalid = false;
        } catch (RuntimeException exception) { invalid = true; throw new IOException("invalid configuration: " + exception.getMessage(), exception); }
    }
    public static State snapshot() { return state; }
    public static boolean isInvalid() { return invalid; }

    public static synchronized boolean addWhitelist(String name) throws IOException {
        String value = name.trim(); if (value.isEmpty()) throw new IOException("player name cannot be empty");
        Set<String> values = new LinkedHashSet<>(state.whitelist()); if (!values.add(value)) return false;
        set(new State(values, state.prefix(), state.suffix(), state.names(), state.initialWorkers(), state.cachedWorkers(), state.dashboardPort())); return true;
    }
    public static synchronized boolean removeWhitelist(String name) throws IOException {
        Set<String> values = new LinkedHashSet<>(state.whitelist()); if (!values.remove(name.trim())) return false;
        set(new State(values, state.prefix(), state.suffix(), state.names(), state.initialWorkers(), state.cachedWorkers(), state.dashboardPort())); return true;
    }
    public static synchronized void setFormat(boolean prefix, String value) throws IOException {
        if (value.isBlank()) throw new IOException("format cannot be empty");
        set(new State(state.whitelist(), prefix ? value : state.prefix(), prefix ? state.suffix() : value, state.names(), state.initialWorkers(), state.cachedWorkers(), state.dashboardPort()));
    }
    public static synchronized void setName(String item, String name) throws IOException {
        if (name.isBlank()) throw new IOException("name cannot be empty");
        Map<String,String> values = new LinkedHashMap<>(state.names()); values.put(item, name.trim());
        set(new State(state.whitelist(), state.prefix(), state.suffix(), values, state.initialWorkers(), state.cachedWorkers(), state.dashboardPort()));
    }
    public static synchronized boolean removeName(String item) throws IOException {
        Map<String,String> values = new LinkedHashMap<>(state.names()); if (values.remove(item) == null) return false;
        set(new State(state.whitelist(), state.prefix(), state.suffix(), values, state.initialWorkers(), state.cachedWorkers(), state.dashboardPort())); return true;
    }
    public static synchronized void setWorkers(int initial, int cached) throws IOException {
        set(new State(state.whitelist(), state.prefix(), state.suffix(), state.names(), initial, cached, state.dashboardPort()));
    }
    public static synchronized void setDashboardPort(int port) throws IOException {
        if (port < 1024 || port > 65535) throw new IOException("port must be 1024-65535");
        set(new State(state.whitelist(), state.prefix(), state.suffix(), state.names(), state.initialWorkers(), state.cachedWorkers(), port));
    }
    private static void set(State next) throws IOException { if (invalid) throw new IOException("repair or move invalid configuration: " + path); save(next); state = next; }
    private static void save(State value) throws IOException {
        Files.createDirectories(path.getParent()); Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(write(value)) + System.lineSeparator(), StandardCharsets.UTF_8);
        try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static State read(JsonObject o) {
        Set<String> whitelist = strings(o, "whitelist"); Map<String,String> names = new LinkedHashMap<>();
        if (o.has("names")) for (Map.Entry<String,JsonElement> e : o.getAsJsonObject("names").entrySet()) names.put(e.getKey(), e.getValue().getAsString());
        int cpu = Math.max(1, Runtime.getRuntime().availableProcessors());
        return new State(whitelist, string(o,"prefix","sort_"), string(o,"suffix","_sort"), names,
                integer(o,"initialWorkers", Math.max(1, cpu / 2)), integer(o,"cachedWorkers", Math.min(2,cpu)), integer(o,"dashboardPort",8766));
    }
    private static JsonObject write(State s) { JsonObject o = new JsonObject(); JsonArray a = new JsonArray(); s.whitelist().stream().sorted().forEach(a::add); o.add("whitelist",a); o.addProperty("prefix",s.prefix());o.addProperty("suffix",s.suffix());JsonObject n=new JsonObject();s.names().forEach(n::addProperty);o.add("names",n);o.addProperty("initialWorkers",s.initialWorkers());o.addProperty("cachedWorkers",s.cachedWorkers());o.addProperty("dashboardPort",s.dashboardPort());return o; }
    private static Set<String> strings(JsonObject o,String key){Set<String> r=new LinkedHashSet<>();if(o.has(key))for(JsonElement e:o.getAsJsonArray(key)){if(!r.add(e.getAsString()))throw new IllegalArgumentException("duplicate "+key);}return Set.copyOf(r);}
    private static String string(JsonObject o,String key,String fallback){return o.has(key)?o.get(key).getAsString():fallback;}
    private static int integer(JsonObject o,String key,int fallback){return o.has(key)?o.get(key).getAsInt():fallback;}
    public record State(Set<String> whitelist, String prefix, String suffix, Map<String,String> names, int initialWorkers, int cachedWorkers, int dashboardPort) {
        public State { whitelist=Set.copyOf(whitelist); names=Map.copyOf(names); int cpu=Math.max(1,Runtime.getRuntime().availableProcessors());if(initialWorkers<1||initialWorkers>cpu||cachedWorkers<1||cachedWorkers>cpu)throw new IllegalArgumentException("worker count must be 1-"+cpu); }
        static State defaults(){int cpu=Math.max(1,Runtime.getRuntime().availableProcessors());return new State(Set.of(),"sort_","_sort",Map.of(),Math.max(1,cpu/2),Math.min(2,cpu),8766);}
    }
}
//#endif
