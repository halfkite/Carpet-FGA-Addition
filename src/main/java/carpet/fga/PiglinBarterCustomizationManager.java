//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/** Runtime and persistent configuration for Minecraft 1.21+ piglin barter customization. */
public final class PiglinBarterCustomizationManager {
    public static final String COMMAND = "piglinBarterItemExclusions";
    private static final String TABLE_RESOURCE = "loot_table/gameplay/piglin_bartering.json";
    private static final int MAX_REROLLS = 4096;
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/piglin-barter");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile Path path;
    private static volatile Object resourceManager;
    private static volatile State state = State.empty();
    private static volatile boolean loadFailed;

    private PiglinBarterCustomizationManager() {
    }

    public static synchronized void load(MinecraftServer server) {
        loadFailed = false;
        path = FGAWorldConfigPaths.current(server, "piglin-barter-customization.json");
        state = readState(path);
        resourceManager = null;
        ensureTable(server);
        migrateLegacyRule();
    }

    public static synchronized void clear() {
        path = null;
        resourceManager = null;
        state = State.empty();
        loadFailed = false;
    }

    public static boolean enabled() {
        String value = FGASettings.piglinBarterItemExclusions;
        return "true".equalsIgnoreCase(value) || (value != null && value.trim().startsWith("["));
    }

    public static boolean canUseCommand(net.minecraft.commands.CommandSourceStack source) {
        return enabled() && carpet.utils.CommandHelper.canUseCommand(source, carpet.CarpetSettings.commandPlayer);
    }

    public static synchronized State snapshot(MinecraftServer server) {
        ensureTable(server);
        return state;
    }

    public static synchronized void onRuleChanged(MinecraftServer server) {
        if (server != null) {
            ensureTable(server);
            migrateLegacyRule();
        }
    }

    public static synchronized void onResourcesReloaded(MinecraftServer server) {
        resourceManager = null;
        ensureTable(server);
    }

    public static synchronized void add(String key, MinecraftServer server) throws IOException {
        ensureTable(server);
        Entry entry = requireEntry(key);
        if (!entry.present()) throw new IllegalArgumentException("交易条目当前不在战利品表中: " + key);
        if (entry.enabled()) throw new IllegalArgumentException("交易条目已经启用: " + key);
        update(entry.withEnabled(true));
    }

    public static synchronized void enable(String key, MinecraftServer server) throws IOException {
        add(key, server);
    }

    public static synchronized void disable(String key, MinecraftServer server) throws IOException {
        ensureTable(server);
        Entry entry = requireEntry(key);
        if (!entry.enabled()) throw new IllegalArgumentException("交易条目已经禁用: " + key);
        update(entry.withEnabled(false));
    }

    public static synchronized void set(String key, double probability, int min, int max,
                                        MinecraftServer server) throws IOException {
        ensureTable(server);
        if (!Double.isFinite(probability) || probability < 0.0D) {
            throw new IllegalArgumentException("交易概率必须是非负数字");
        }
        if (min < 1 || max < min) {
            throw new IllegalArgumentException("交易数量范围必须为 1 或更大的 min-max");
        }
        Entry entry = requireEntry(key);
        update(entry.withValues(probability, min, max));
    }

    public static synchronized void reset(String key, MinecraftServer server) throws IOException {
        ensureTable(server);
        Entry entry = requireEntry(key);
        update(entry.withValues(entry.defaultProbability(), entry.defaultMin(), entry.defaultMax()));
    }

    public static ObjectArrayList<ItemStack> customize(LootTable lootTable, LootParams params) {
        if (!enabled()) return lootTable.getRandomItems(params);
        MinecraftServer server = params.getLevel().getServer();
        synchronized (PiglinBarterCustomizationManager.class) {
            ensureTable(server);
            if (state.entries().isEmpty()) return lootTable.getRandomItems(params);
        }

        ObjectArrayList<ItemStack> vanilla = lootTable.getRandomItems(params);
        if (vanilla.isEmpty()) return vanilla;

        ObjectArrayList<ItemStack> result = new ObjectArrayList<>();
        RandomSource random = params.getLevel().getRandom();
        for (ItemStack original : vanilla) {
            Entry selected = choose(random);
            if (selected == null) continue;
            ItemStack matching = matches(original, selected) ? original : null;
            for (int attempt = 0; matching == null && attempt < MAX_REROLLS; attempt++) {
                ObjectArrayList<ItemStack> rerolled = lootTable.getRandomItems(params);
                if (rerolled.size() == 1 && matches(rerolled.get(0), selected)) {
                    matching = rerolled.get(0);
                }
            }
            if (matching == null) {
                LOGGER.warn("无法在 {} 次重抽内生成猪灵交易条目 {}，回退原版结果", MAX_REROLLS, selected.key());
                result.add(original);
            } else {
                result.add(matching.copyWithCount(randomBetween(random, selected.min(), selected.max())));
            }
        }
        return result;
    }

    private static Entry choose(RandomSource random) {
        List<Entry> candidates;
        synchronized (PiglinBarterCustomizationManager.class) {
            candidates = state.entries().values().stream()
                    .filter(entry -> entry.present() && entry.enabled() && entry.probability() > 0.0D)
                    .toList();
        }
        double total = candidates.stream().mapToDouble(Entry::probability).sum();
        if (total <= 0.0D) return null;
        double roll = random.nextDouble() * 100.0D;
        if (total <= 100.0D && roll >= total) return null;
        double scale = total > 100.0D ? 100.0D / total : 1.0D;
        for (Entry entry : candidates) {
            roll -= entry.probability() * scale;
            if (roll < 0.0D) return entry;
        }
        return candidates.get(candidates.size() - 1);
    }

    private static int randomBetween(RandomSource random, int min, int max) {
        if (min == max) return min;
        return min + random.nextInt(max - min + 1);
    }

    private static boolean matches(ItemStack stack, Entry entry) {
        if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(entry.itemId())) return false;
        return entry.variant().equals(variant(stack));
    }

    public static String variant(ItemStack stack) {
        PotionContents potion = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (potion != null && potion.potion().isPresent()) {
            return "potion=" + potion.potion().get().unwrapKey().map(key -> key.location().toString()).orElse("unknown");
        }
        return "";
    }

    public static Component displayName(Entry entry) {
        //#if MC < 1.21.3
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(entry.itemId()));
        //#else
        //$$ ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(entry.itemId()).orElseThrow());
        //#endif
        MutableComponent name = stack.getHoverName().copy();
        if (!entry.variant().isEmpty()) {
            name.append(FGACompat.literal(" (" + entry.variant() + ")").withStyle(ChatFormatting.GRAY));
        }
        return name;
    }

    private static Entry requireEntry(String key) {
        Entry entry = state.entries().get(key);
        if (entry == null) throw new IllegalArgumentException("未知交易条目: " + key);
        return entry;
    }

    private static void update(Entry entry) throws IOException {
        requireLoaded();
        Map<String, Entry> next = new LinkedHashMap<>(state.entries());
        next.put(entry.key(), entry);
        save(new State(next, state.legacyMigrated()));
        state = new State(next, state.legacyMigrated());
    }

    private static void requireLoaded() throws IOException {
        if (path == null) throw new IOException("配置尚未加载");
        if (loadFailed) throw new IOException("配置文件损坏，已拒绝写入: " + path);
    }

    private static void ensureTable(MinecraftServer server) {
        if (server == null || resourceManager == server.getResourceManager()) return;
        resourceManager = server.getResourceManager();
        List<RawEntry> discovered = discover(server);
        Map<String, Entry> merged = new LinkedHashMap<>();
        for (RawEntry raw : discovered) {
            Entry old = state.entries().get(raw.key());
            merged.put(raw.key(), old == null
                    ? raw.toEntry()
                    : old.withMetadata(raw.itemId(), raw.variant(), raw.probability(), raw.min(), raw.max(), true));
        }
        for (Entry old : state.entries().values()) {
            if (!merged.containsKey(old.key())) merged.put(old.key(), old.withPresent(false));
        }
        state = new State(merged, state.legacyMigrated());
    }

    private static List<RawEntry> discover(MinecraftServer server) {
        ResourceLocation resource = FGACompat.vanillaId(TABLE_RESOURCE);
        try {
            var optional = server.getResourceManager().getResource(resource);
            if (optional.isEmpty()) return List.of();
            try (Reader reader = optional.get().openAsReader()) {
                JsonElement root = JsonParser.parseReader(reader);
                List<RawEntry> values = new ArrayList<>();
                collect(root, values);
                double total = values.stream().mapToDouble(RawEntry::weight).sum();
                if (total <= 0.0D) return List.of();
                Map<String, RawEntry> merged = new LinkedHashMap<>();
                for (RawEntry raw : values) {
                    RawEntry previous = merged.get(raw.key());
                    merged.put(raw.key(), previous == null ? raw : previous.withWeight(previous.weight() + raw.weight()));
                }
                return merged.values().stream()
                        .map(raw -> raw.withProbability(raw.weight() / total * 100.0D))
                        .toList();
            }
        } catch (Exception exception) {
            LOGGER.warn("无法解析猪灵交易战利品表", exception);
            return List.of();
        }
    }

    private static void collect(JsonElement element, List<RawEntry> output) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if ("minecraft:item".equals(string(object, "type")) && object.has("name")) {
                ResourceLocation itemId = parseItemId(object.get("name").getAsString());
                if (itemId != null) {
                    int weight = integer(object, "weight", 1);
                    int[] count = count(object);
                    String variant = variant(object);
                    output.add(new RawEntry(entryKey(itemId, variant), itemId, variant,
                            weight, 0.0D, count[0], count[1]));
                }
            }
            for (Map.Entry<String, JsonElement> child : object.entrySet()) collect(child.getValue(), output);
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collect(child, output);
        }
    }

    private static String variant(JsonObject object) {
        if (!object.has("functions") || !object.get("functions").isJsonArray()) return "";
        for (JsonElement element : object.getAsJsonArray("functions")) {
            if (!element.isJsonObject()) continue;
            JsonObject function = element.getAsJsonObject();
            String type = string(function, "function");
            if (type.endsWith("set_potion") && function.has("id")) return "potion=" + function.get("id").getAsString();
        }
        return "";
    }

    private static int[] count(JsonObject object) {
        int min = 1;
        int max = 1;
        if (!object.has("functions") || !object.get("functions").isJsonArray()) return new int[]{min, max};
        for (JsonElement element : object.getAsJsonArray("functions")) {
            if (!element.isJsonObject()) continue;
            JsonObject function = element.getAsJsonObject();
            if (!string(function, "function").endsWith("set_count") || !function.has("count")) continue;
            JsonElement value = function.get("count");
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                min = max = value.getAsInt();
            } else if (value.isJsonObject()) {
                JsonObject range = value.getAsJsonObject();
                min = number(range, "min", min);
                max = number(range, "max", number(range, "min", max));
            }
        }
        return new int[]{Math.max(1, min), Math.max(min, max)};
    }

    private static ResourceLocation parseItemId(String value) {
        if (value.startsWith("#")) return null;
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id != null && BuiltInRegistries.ITEM.containsKey(id) ? id : null;
    }

    private static String entryKey(ResourceLocation itemId, String variant) {
        if (variant.isEmpty()) return itemId.toString();
        return itemId + "__" + variant.replace("potion=", "potion_").replace(':', '_');
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try { return object.has(key) ? object.get(key).getAsInt() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static int number(JsonObject object, String key, int fallback) {
        return integer(object, key, fallback);
    }

    private static State readState(Path file) {
        if (file == null || !Files.isRegularFile(file)) return State.empty();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("version") && root.get("version").getAsInt() != 1) throw new IOException("unsupported version");
            Map<String, Entry> entries = new LinkedHashMap<>();
            if (root.has("entries")) {
                for (Map.Entry<String, JsonElement> raw : root.getAsJsonObject("entries").entrySet()) {
                    JsonObject object = raw.getValue().getAsJsonObject();
                    ResourceLocation itemId = ResourceLocation.tryParse(string(object, "item"));
                    if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) continue;
                    String variant = string(object, "variant");
                    String key = raw.getKey().contains("|") ? entryKey(itemId, variant) : raw.getKey();
                    entries.put(key, new Entry(key, itemId, variant,
                            object.get("probability").getAsDouble(), object.get("min").getAsInt(),
                            object.get("max").getAsInt(), object.get("defaultProbability").getAsDouble(),
                            object.get("defaultMin").getAsInt(), object.get("defaultMax").getAsInt(),
                            object.has("enabled") && object.get("enabled").getAsBoolean(),
                            object.has("present") && object.get("present").getAsBoolean()));
                }
            }
            return new State(entries, root.has("legacyMigrated") && root.get("legacyMigrated").getAsBoolean());
        } catch (Exception exception) {
            loadFailed = true;
            LOGGER.error("无法读取猪灵交易自定义配置，保留原文件并使用空配置: {}", file, exception);
            return State.empty();
        }
    }

    private static void save(State next) throws IOException {
        if (loadFailed) throw new IOException("配置文件损坏，已拒绝写入: " + path);
        if (path == null) throw new IOException("configuration is not loaded");
        Files.createDirectories(path.getParent());
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("legacyMigrated", next.legacyMigrated());
        JsonObject values = new JsonObject();
        for (Entry entry : next.entries().values()) {
            JsonObject value = new JsonObject();
            value.addProperty("item", entry.itemId().toString());
            value.addProperty("variant", entry.variant());
            value.addProperty("enabled", entry.enabled());
            value.addProperty("present", entry.present());
            value.addProperty("probability", entry.probability());
            value.addProperty("min", entry.min());
            value.addProperty("max", entry.max());
            value.addProperty("defaultProbability", entry.defaultProbability());
            value.addProperty("defaultMin", entry.defaultMin());
            value.addProperty("defaultMax", entry.defaultMax());
            values.add(entry.key(), value);
        }
        root.add("entries", values);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void migrateLegacyRule() {
        if (state.legacyMigrated() || path == null || loadFailed) return;
        String value = FGASettings.piglinBarterItemExclusions;
        if (value == null || !value.trim().startsWith("[")) return;
        Set<ResourceLocation> exclusions;
        try {
            exclusions = FGASettings.parsePiglinBarterItemExclusions(value);
        } catch (RuntimeException exception) {
            LOGGER.warn("旧猪灵交易排除配置无法迁移", exception);
            return;
        }
        Map<String, Entry> migrated = new LinkedHashMap<>(state.entries());
        for (Entry entry : migrated.values()) {
            if (exclusions.contains(entry.itemId())) migrated.put(entry.key(), entry.withEnabled(false));
        }
        State next = new State(migrated, true);
        try {
            save(next);
            state = next;
            LOGGER.info("已将旧猪灵交易排除配置迁移到 {}", path);
        } catch (IOException exception) {
            LOGGER.warn("旧猪灵交易排除配置迁移写入失败", exception);
        }
    }

    public record State(Map<String, Entry> entries, boolean legacyMigrated) {
        public State {
            entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }

        public static State empty() {
            return new State(Map.of(), false);
        }
    }

    public record Entry(String key, ResourceLocation itemId, String variant, double probability, int min, int max,
                        double defaultProbability, int defaultMin, int defaultMax, boolean enabled, boolean present) {
        public Entry withEnabled(boolean value) {
            return new Entry(key, itemId, variant, probability, min, max, defaultProbability, defaultMin, defaultMax, value, present);
        }

        public Entry withValues(double newProbability, int newMin, int newMax) {
            return new Entry(key, itemId, variant, newProbability, newMin, newMax, defaultProbability, defaultMin, defaultMax, enabled, present);
        }

        public Entry withPresent(boolean value) {
            return new Entry(key, itemId, variant, probability, min, max, defaultProbability, defaultMin, defaultMax, enabled, value);
        }

        public Entry withMetadata(ResourceLocation newItem, String newVariant, double newDefaultProbability,
                                  int newDefaultMin, int newDefaultMax, boolean newPresent) {
            return new Entry(key, newItem, newVariant, probability, min, max, newDefaultProbability,
                    newDefaultMin, newDefaultMax, enabled, newPresent);
        }
    }

    private record RawEntry(String key, ResourceLocation itemId, String variant, int weight,
                            double probability, int min, int max) {
        Entry toEntry() {
            return new Entry(key, itemId, variant, probability, min, max, probability, min, max, true, true);
        }

        RawEntry withWeight(int value) {
            return new RawEntry(key, itemId, variant, value, probability, min, max);
        }

        RawEntry withProbability(double value) {
            return new RawEntry(key, itemId, variant, weight, value, min, max);
        }
    }
}
//#endif
