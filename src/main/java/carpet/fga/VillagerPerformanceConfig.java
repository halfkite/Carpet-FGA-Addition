//#if MC >= 1.21.1
package carpet.fga;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
//#if MC >= 1.17
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//#else
//$$ import org.apache.logging.log4j.LogManager;
//$$ import org.apache.logging.log4j.Logger;
//#endif

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.UnaryOperator;

public final class VillagerPerformanceConfig {
    //#if MC >= 1.17
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/villager-performance");
    //#else
    //$$ private static final Logger LOGGER = LogManager.getLogger("carpet-fga-addition/villager-performance");
    //#endif
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile State state = State.defaults();
    private static volatile Path path;
    private static volatile boolean loadFailed;

    private VillagerPerformanceConfig() {}

    public static synchronized void load(MinecraftServer server) {
        path = server.getWorldPath(LevelResource.ROOT).resolve("carpet")
                .resolve("carpetfgaaddition").resolve("villager-performance.json");
        if (!Files.exists(path)) {
            state = migrateLegacy(server);
            loadFailed = false;
            if (!state.equals(State.defaults())) {
                try { save(state); } catch (IOException e) { LOGGER.error("Could not save migrated configuration", e); }
            }
            VillagerTradeOnlyManager.applyConfig(state);
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            state = parse(JsonParser.parseReader(reader));
            loadFailed = false;
            VillagerTradeOnlyManager.applyConfig(state);
            LOGGER.info("Loaded villager performance configuration from {}", path);
        } catch (Exception exception) {
            state = State.defaults();
            loadFailed = true;
            VillagerTradeOnlyManager.applyConfig(state);
            LOGGER.error("Invalid villager performance configuration at {}; preserving it and disabling features", path, exception);
        }
    }

    private static State migrateLegacy(MinecraftServer server) {
        Path carpet = server.getWorldPath(LevelResource.ROOT).resolve("carpet.conf");
        if (!Files.isRegularFile(carpet)) return State.defaults();
        String mode = "false", blocks = "false", names = "false";
        try {
            for (String line : Files.readAllLines(carpet, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("villagerTradeOnlyMode ")) mode = value(trimmed);
                else if (trimmed.startsWith("villagerTradeOnlyBlocks ")) blocks = value(trimmed);
                else if (trimmed.startsWith("villagerTradeOnlyNames ")) names = value(trimmed);
            }
            State migrated = new State(parseMode(mode), parseBlocks(blocks), parseNames(names), false, Set.of(), Set.of());
            if (!migrated.equals(State.defaults())) LOGGER.warn("Migrated legacy villagerTradeOnly rules; use /villagerPerformance");
            return migrated;
        } catch (RuntimeException | IOException e) {
            LOGGER.error("Could not migrate legacy villager rules from {}", carpet, e);
            return State.defaults();
        }
    }

    private static String value(String line) { int i = line.indexOf(' '); return i < 0 ? "" : line.substring(i + 1).trim(); }
    public static State snapshot() { return state; }
    public static boolean isLoadFailed() { return loadFailed; }

    public static synchronized void setTradeMode(TradeMode mode) throws IOException {
        update(s -> new State(mode, s.tradeBlocks(), s.tradeNames(), s.giftEnabled(), s.giftBlocks(), s.giftNames()));
    }
    public static synchronized void setGiftEnabled(boolean enabled) throws IOException {
        update(s -> new State(s.tradeMode(), s.tradeBlocks(), s.tradeNames(), enabled, s.giftBlocks(), s.giftNames()));
    }
    public static synchronized boolean add(Target target, Kind kind, String raw) throws IOException {
        if (kind == Kind.BLOCK) {
            ResourceLocation id = parseBlock(raw); Set<ResourceLocation> values = new LinkedHashSet<>(blocks(target));
            if (!values.add(id)) return false; replace(target, kind, values, null);
        } else {
            String name = parseName(raw); Set<String> values = new LinkedHashSet<>(names(target));
            if (!values.add(name)) return false; replace(target, kind, null, values);
        }
        return true;
    }
    public static synchronized boolean remove(Target target, Kind kind, String raw) throws IOException {
        if (kind == Kind.BLOCK) {
            ResourceLocation id = parseBlock(raw); Set<ResourceLocation> values = new LinkedHashSet<>(blocks(target));
            if (!values.remove(id)) return false; replace(target, kind, values, null);
        } else {
            String name = parseName(raw); Set<String> values = new LinkedHashSet<>(names(target));
            if (!values.remove(name)) return false; replace(target, kind, null, values);
        }
        return true;
    }
    private static void replace(Target target, Kind kind, Set<ResourceLocation> newBlocks, Set<String> newNames) throws IOException {
        update(s -> new State(s.tradeMode(),
                target == Target.TRADE && kind == Kind.BLOCK ? newBlocks : s.tradeBlocks(),
                target == Target.TRADE && kind == Kind.NAME ? newNames : s.tradeNames(), s.giftEnabled(),
                target == Target.GIFT && kind == Kind.BLOCK ? newBlocks : s.giftBlocks(),
                target == Target.GIFT && kind == Kind.NAME ? newNames : s.giftNames()));
    }
    public static Set<ResourceLocation> blocks(Target target) { return target == Target.TRADE ? state.tradeBlocks() : state.giftBlocks(); }
    public static Set<String> names(Target target) { return target == Target.TRADE ? state.tradeNames() : state.giftNames(); }

    private static void update(UnaryOperator<State> operation) throws IOException {
        if (loadFailed) throw new IOException("configuration is invalid; repair or move " + path);
        State next = operation.apply(state); save(next); state = next; VillagerTradeOnlyManager.applyConfig(next);
    }
    private static void save(State value) throws IOException {
        Files.createDirectories(path.getParent()); Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(toJson(value)) + System.lineSeparator(), StandardCharsets.UTF_8);
        try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static State parse(JsonElement element) {
        JsonObject o = element.getAsJsonObject();
        return new State(parseMode(get(o, "tradeMode", "false")), readBlocks(o, "tradeBlocks"), readNames(o, "tradeNames"),
                o.has("giftEnabled") && o.get("giftEnabled").getAsBoolean(), readBlocks(o, "giftBlocks"), readNames(o, "giftNames"));
    }
    private static JsonObject toJson(State s) {
        JsonObject o = new JsonObject(); o.addProperty("tradeMode", s.tradeMode().serialized()); o.addProperty("giftEnabled", s.giftEnabled());
        write(o, "tradeBlocks", s.tradeBlocks()); write(o, "tradeNames", s.tradeNames());
        write(o, "giftBlocks", s.giftBlocks()); write(o, "giftNames", s.giftNames()); return o;
    }
    private static void write(JsonObject o, String key, Collection<?> values) { JsonArray a = new JsonArray(); values.stream().map(Object::toString).sorted().forEach(a::add); o.add(key, a); }
    private static Set<ResourceLocation> readBlocks(JsonObject o, String key) { Set<ResourceLocation> r=new LinkedHashSet<>();if(o.has(key))for(JsonElement e:o.getAsJsonArray(key)){ResourceLocation id=parseBlock(e.getAsString());if(!r.add(id))throw new IllegalArgumentException("duplicate block: "+id);}return Set.copyOf(r); }
    private static Set<String> readNames(JsonObject o, String key) { Set<String> r=new LinkedHashSet<>();if(o.has(key))for(JsonElement e:o.getAsJsonArray(key)){String n=parseName(e.getAsString());if(!r.add(n))throw new IllegalArgumentException("duplicate name: "+n);}return Set.copyOf(r); }
    private static String get(JsonObject o,String key,String fallback){return o.has(key)?o.get(key).getAsString():fallback;}
    private static Set<ResourceLocation> parseBlocks(String v){Set<ResourceLocation> r=new LinkedHashSet<>();for(String x:entries(v))if(!r.add(parseBlock(x)))throw new IllegalArgumentException("duplicate block");return Set.copyOf(r);}
    private static Set<String> parseNames(String v){Set<String> r=new LinkedHashSet<>();for(String x:entries(v))if(!r.add(parseName(x)))throw new IllegalArgumentException("duplicate name");return Set.copyOf(r);}
    private static List<String> entries(String v){if(v.equalsIgnoreCase("false"))return List.of();if(!v.startsWith("[")||!v.endsWith("]"))throw new IllegalArgumentException("invalid list");String b=v.substring(1,v.length()-1);if(b.isBlank())return List.of();return Arrays.stream(b.split(",",-1)).map(String::trim).toList();}
    public static ResourceLocation parseBlock(String raw){String v=raw.trim();ResourceLocation id=ResourceLocation.tryParse(v.contains(":")?v:"minecraft:"+v);if(id==null||!BuiltInRegistries.BLOCK.containsKey(id))throw new IllegalArgumentException("unknown block id: "+raw);return id;}
    public static String parseName(String raw){String n=raw.trim();if(n.isEmpty())throw new IllegalArgumentException("name cannot be empty");return n;}
    public static TradeMode parseMode(String v){return switch(v.toLowerCase(Locale.ROOT)){case "false"->TradeMode.FALSE;case "ai"->TradeMode.AI;case "static"->TradeMode.STATIC;default->throw new IllegalArgumentException("mode must be false, ai, or static");};}
    public enum TradeMode { FALSE, AI, STATIC; public String serialized(){return name().toLowerCase(Locale.ROOT);} }
    public enum Target { TRADE, GIFT }
    public enum Kind { BLOCK, NAME }
    public record State(TradeMode tradeMode, Set<ResourceLocation> tradeBlocks, Set<String> tradeNames,
                        boolean giftEnabled, Set<ResourceLocation> giftBlocks, Set<String> giftNames) {
        public State { tradeBlocks=Set.copyOf(tradeBlocks);tradeNames=Set.copyOf(tradeNames);giftBlocks=Set.copyOf(giftBlocks);giftNames=Set.copyOf(giftNames); }
        public static State defaults(){return new State(TradeMode.FALSE,Set.of(),Set.of(),false,Set.of(),Set.of());}
    }
}
//#endif
