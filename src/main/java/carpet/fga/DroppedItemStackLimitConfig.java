//#if MC <= 26.2
package carpet.fga;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
//#if MC >= 1.19.3
import net.minecraft.core.registries.BuiltInRegistries;
//#else
//$$ import net.minecraft.core.Registry;
//#endif
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.fabricmc.loader.api.FabricLoader;
//#if MC >= 1.17
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//#else
//$$ import org.apache.logging.log4j.LogManager;
//$$ import org.apache.logging.log4j.Logger;
//#endif

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

public final class DroppedItemStackLimitConfig {
    /** Keeps the sum of two maximum stacks below Integer.MAX_VALUE. */
    public static final int MAX_LIMIT = 1_000_000_000;
    private static final int DEFAULT_LIMIT = 64;
    //#if MC >= 1.17
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/dropped-item-stack-limit");
    //#else
    //$$ private static final Logger LOGGER = LogManager.getLogger("carpet-fga-addition/dropped-item-stack-limit");
    //#endif
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Path configPath;

    private static volatile State state = State.defaults();
    private static volatile boolean loadFailed;

    private DroppedItemStackLimitConfig() {
    }

    public static synchronized void load(MinecraftServer server) {
        Path current = FGAWorldConfigPaths.current(server, "dropped-item-stack-limit.json");
        Path legacy = FGAWorldConfigPaths.legacy(server, "dropped-item-stack-limit.json");
        try {
            configPath = FGAWorldConfigPaths.migrate(current, legacy, DroppedItemStackLimitConfig::validFile);
        } catch (IOException exception) {
            configPath = legacy;
            LOGGER.error("Could not migrate dropped item stack configuration to {}", current, exception);
        }
        if (!Files.exists(configPath)) {
            state = State.defaults();
            loadFailed = false;
            logStackSizeTweaksCompatibility();
            return;
        }
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            state = parse(
                    //#if MC >= 1.18
                    JsonParser.parseReader(reader)
                    //#else
                    //$$ new JsonParser().parse(reader)
                    //#endif
            );
            loadFailed = false;
            LOGGER.info("Loaded dropped item stack configuration from {}", configPath);
            logStackSizeTweaksCompatibility();
        } catch (Exception exception) {
            state = State.defaults();
            loadFailed = true;
            LOGGER.error("Invalid dropped item stack configuration at {}; using vanilla limits and preserving the file",
                    configPath, exception);
        }
    }

    private static boolean validFile(Path candidate) {
        try (Reader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
            parse(
                    //#if MC >= 1.18
                    JsonParser.parseReader(reader)
                    //#else
                    //$$ new JsonParser().parse(reader)
                    //#endif
            );
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public static boolean isStackSizeTweaksCompatibilityActive() {
        return FabricLoader.getInstance().isModLoaded("stacksizetweaks");
    }

    private static void logStackSizeTweaksCompatibility() {
        if (isStackSizeTweaksCompatibilityActive()) {
            LOGGER.info("Stack Size Tweaks detected; using the compatible ItemEntity merge injection");
        }
    }

    public static void warnLegacyRule(MinecraftServer server) {
        Path carpetConfig = server.getWorldPath(LevelResource.ROOT).resolve("carpet.conf");
        if (!Files.isRegularFile(carpetConfig)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(carpetConfig, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("droppedItemStackLimit ")) {
                    continue;
                }
                String value = trimmed.substring("droppedItemStackLimit ".length()).trim();
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    LOGGER.warn("Legacy Carpet rule value '{}' is no longer supported. The rule is disabled; use "
                            + "/carpet droppedItemStackLimit true and /droppedItemStackLimit mode ...", value);
                }
                return;
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not inspect {} for a legacy droppedItemStackLimit value", carpetConfig, exception);
        }
    }

    public static int effectiveLimit(ItemStack stack) {
        int vanillaLimit = stack.getMaxStackSize();
        if (!FGASettings.isDroppedItemStackLimitEnabled() || vanillaLimit <= 1 || loadFailed) {
            return vanillaLimit;
        }
        ResourceLocation itemId =
                //#if MC >= 1.19.3
                BuiltInRegistries.ITEM.getKey(stack.getItem());
                //#else
                //$$ Registry.ITEM.getKey(stack.getItem());
                //#endif
        State current = state;
        return switch (current.mode()) {
            case ALL -> current.allLimit();
            case BLACK -> current.blacklist().contains(itemId) ? vanillaLimit : current.blackLimit();
            case WHITELIST -> current.whitelist().getOrDefault(itemId, vanillaLimit);
        };
    }

    public static int effectiveInventoryLimit(ItemStack stack) {
        return scopedLimit(state.inventoryLimit(), stack);
    }

    public static int effectiveContainerLimit(ItemStack stack) {
        return scopedLimit(state.containerLimit(), stack);
    }

    private static int scopedLimit(int configured, ItemStack stack) {
        int vanilla = stack.getMaxStackSize();
        return configured > 0 && vanilla > 1 ? Math.max(vanilla, configured) : vanilla;
    }

    public static synchronized void setInventoryLimit(int limit) throws IOException {
        validateLimit(limit);
        update(current -> current.withInventoryLimit(limit));
    }

    public static synchronized void setContainerLimit(int limit) throws IOException {
        validateLimit(limit);
        update(current -> current.withContainerLimit(limit));
    }

    public static synchronized void resetInventoryLimit() throws IOException {
        update(current -> current.withInventoryLimit(0));
    }

    public static synchronized void resetContainerLimit() throws IOException {
        update(current -> current.withContainerLimit(0));
    }

    public static State snapshot() {
        return state;
    }

    public static boolean isLoadFailed() {
        return loadFailed;
    }

    public static boolean requiresModdedClient() {
        State current = state;
        return !loadFailed && (current.inventoryLimit() > 0 || current.containerLimit() > 0);
    }

    public static synchronized void setAllMode(int limit) throws IOException {
        validateLimit(limit);
        update(current -> new State(Mode.ALL, limit, current.blackLimit(), current.inventoryLimit(), current.containerLimit(),
                current.blacklist(), current.whitelist()));
    }

    public static synchronized void setBlackMode(int limit) throws IOException {
        validateLimit(limit);
        update(current -> new State(Mode.BLACK, current.allLimit(), limit, current.inventoryLimit(), current.containerLimit(),
                current.blacklist(), current.whitelist()));
    }

    public static synchronized void setWhitelistMode() throws IOException {
        update(current -> new State(Mode.WHITELIST, current.allLimit(), current.blackLimit(), current.inventoryLimit(), current.containerLimit(),
                current.blacklist(), current.whitelist()));
    }

    public static synchronized boolean addBlacklist(ResourceLocation itemId) throws IOException {
        if (state.blacklist().contains(itemId)) {
            return false;
        }
        update(current -> {
            Set<ResourceLocation> entries = new LinkedHashSet<>(current.blacklist());
            entries.add(itemId);
            return new State(current.mode(), current.allLimit(), current.blackLimit(), current.inventoryLimit(), current.containerLimit(), entries, current.whitelist());
        });
        return true;
    }

    public static synchronized boolean removeBlacklist(ResourceLocation itemId) throws IOException {
        if (!state.blacklist().contains(itemId)) {
            return false;
        }
        update(current -> {
            Set<ResourceLocation> entries = new LinkedHashSet<>(current.blacklist());
            entries.remove(itemId);
            return new State(current.mode(), current.allLimit(), current.blackLimit(), current.inventoryLimit(), current.containerLimit(), entries, current.whitelist());
        });
        return true;
    }

    public static synchronized void setWhitelistItem(ResourceLocation itemId, int limit) throws IOException {
        validateLimit(limit);
        update(current -> {
            Map<ResourceLocation, Integer> entries = new LinkedHashMap<>(current.whitelist());
            entries.put(itemId, limit);
            return new State(current.mode(), current.allLimit(), current.blackLimit(), current.inventoryLimit(), current.containerLimit(), current.blacklist(), entries);
        });
    }

    public static synchronized boolean removeWhitelistItem(ResourceLocation itemId) throws IOException {
        if (!state.whitelist().containsKey(itemId)) {
            return false;
        }
        update(current -> {
            Map<ResourceLocation, Integer> entries = new LinkedHashMap<>(current.whitelist());
            entries.remove(itemId);
            return new State(current.mode(), current.allLimit(), current.blackLimit(), current.inventoryLimit(), current.containerLimit(), current.blacklist(), entries);
        });
        return true;
    }

    public static synchronized int clearActiveList() throws IOException {
        State current = state;
        if (current.mode() == Mode.ALL) {
            return 0;
        }
        int removed = current.mode() == Mode.BLACK ? current.blacklist().size() : current.whitelist().size();
        if (removed == 0) {
            return 0;
        }
        if (current.mode() == Mode.BLACK) {
            update(value -> new State(value.mode(), value.allLimit(), value.blackLimit(), value.inventoryLimit(), value.containerLimit(), Set.of(), value.whitelist()));
        } else {
            update(value -> new State(value.mode(), value.allLimit(), value.blackLimit(), value.inventoryLimit(), value.containerLimit(), value.blacklist(), Map.of()));
        }
        return removed;
    }

    private static void update(UnaryOperator<State> operation) throws IOException {
        if (loadFailed) {
            throw new IOException("配置文件损坏；请先修复或移走 " + configPath);
        }
        State next = operation.apply(state);
        save(next);
        state = next;
    }

    private static void save(State value) throws IOException {
        Files.createDirectories(configPath.getParent());
        Path temporary = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(toJson(value)) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static State parse(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("root must be a JSON object");
        }
        JsonObject root = element.getAsJsonObject();
        Mode mode = Mode.valueOf(getString(root, "mode", "whitelist").toUpperCase(Locale.ROOT));
        int allLimit = getInt(root, "allLimit", DEFAULT_LIMIT);
        int blackLimit = getInt(root, "blackLimit", DEFAULT_LIMIT);
        int inventoryLimit = getInt(root, "inventoryLimit", 0);
        int containerLimit = getInt(root, "containerLimit", 0);
        validateLimit(allLimit);
        validateLimit(blackLimit);
        if (inventoryLimit < 0 || containerLimit < 0) throw new IllegalArgumentException("scoped limits cannot be negative");
        if (inventoryLimit > 0) validateLimit(inventoryLimit);
        if (containerLimit > 0) validateLimit(containerLimit);

        Set<ResourceLocation> blacklist = new LinkedHashSet<>();
        if (root.has("blacklist")) {
            for (JsonElement entry : root.getAsJsonArray("blacklist")) {
                blacklist.add(parseItemId(entry.getAsString()));
            }
        }

        Map<ResourceLocation, Integer> whitelist = new LinkedHashMap<>();
        if (root.has("whitelist")) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("whitelist").entrySet()) {
                int limit = entry.getValue().getAsInt();
                validateLimit(limit);
                whitelist.put(parseItemId(entry.getKey()), limit);
            }
        }
        return new State(mode, allLimit, blackLimit, inventoryLimit, containerLimit, blacklist, whitelist);
    }

    private static JsonObject toJson(State value) {
        JsonObject root = new JsonObject();
        root.addProperty("mode", value.mode().serializedName());
        root.addProperty("allLimit", value.allLimit());
        root.addProperty("blackLimit", value.blackLimit());
        root.addProperty("inventoryLimit", value.inventoryLimit());
        root.addProperty("containerLimit", value.containerLimit());

        JsonArray blacklist = new JsonArray();
        value.blacklist().stream().sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(itemId -> blacklist.add(itemId.toString()));
        root.add("blacklist", blacklist);

        JsonObject whitelist = new JsonObject();
        value.whitelist().entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> whitelist.addProperty(entry.getKey().toString(), entry.getValue()));
        root.add("whitelist", whitelist);
        return root;
    }

    private static ResourceLocation parseItemId(String value) {
        ResourceLocation itemId = ResourceLocation.tryParse(value);
        if (itemId == null || !
                //#if MC >= 1.19.3
                BuiltInRegistries.ITEM.containsKey(itemId)
                //#else
                //$$ Registry.ITEM.containsKey(itemId)
                //#endif
        ) {
            throw new IllegalArgumentException("unknown item id: " + value);
        }
        return itemId;
    }

    private static String getString(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ": " + limit);
        }
    }

    public enum Mode {
        ALL("all"),
        BLACK("black"),
        WHITELIST("whitelist");

        private final String serializedName;

        Mode(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }

    public record State(Mode mode, int allLimit, int blackLimit, int inventoryLimit, int containerLimit,
                        Set<ResourceLocation> blacklist, Map<ResourceLocation, Integer> whitelist) {
        public State {
            blacklist = Set.copyOf(blacklist);
            whitelist = Map.copyOf(whitelist);
        }

        private static State defaults() {
            return new State(Mode.WHITELIST, DEFAULT_LIMIT, DEFAULT_LIMIT, 0, 0, Set.of(), Map.of());
        }

        private State withInventoryLimit(int limit) {
            return new State(mode, allLimit, blackLimit, limit, containerLimit, blacklist, whitelist);
        }

        private State withContainerLimit(int limit) {
            return new State(mode, allLimit, blackLimit, inventoryLimit, limit, blacklist, whitelist);
        }

        public List<ResourceLocation> sortedBlacklist() {
            List<ResourceLocation> entries = new ArrayList<>(blacklist);
            entries.sort(Comparator.comparing(ResourceLocation::toString));
            return entries;
        }

        public List<Map.Entry<ResourceLocation, Integer>> sortedWhitelist() {
            List<Map.Entry<ResourceLocation, Integer>> entries = new ArrayList<>(whitelist.entrySet());
            entries.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));
            return entries;
        }
    }
}
//#endif
