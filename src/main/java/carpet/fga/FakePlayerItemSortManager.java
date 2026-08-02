//#if MC == 1.21.1
package carpet.fga;

import carpet.patches.EntityPlayerMPFake;
import com.google.gson.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 1.21.1 fake-player sorter. Worker threads only compute stable target names; all inventory and
 * playerdata mutation stays on the server thread.
 */
public final class FakePlayerItemSortManager {
    // Inventory access is not thread-safe. Keep only one short commit on the server thread per tick.
    private static final long ALL_REBUILD_INTERVAL_TICKS = 100L;
    private static final int MAIN_SIZE = 36;
    private static final int SOURCE_ARMOR_START = MAIN_SIZE;
    private static final int SOURCE_OFFHAND_SLOT = SOURCE_ARMOR_START + 4;
    private static final int SOURCE_SLOT_COUNT = SOURCE_OFFHAND_SLOT + 1;
    private static final int PRIMARY_HOTBAR_END = 8;
    private static final int PRIMARY_LOOSE_START = 9;
    private static final int SHULKER_SIZE = 27;
    private static final int DEPOT_RESTOCK_AT = 10;
    private static final int DEPOT_TARGET_BOXES = 20;
    private static final int DEPOT_MATERIAL_TARGET = 128;
    private static final int DEPOT_MATERIAL_RESTOCK_AT = 64;
    private static final int OFFHAND_NBT_SLOT = -106;
    private static final long RESTOCK_RETRY_MS = 30_000L;
    private static final long NOTICE_THROTTLE_MS = 10_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Job> JOBS = new HashMap<>();
    private static final Queue<PlannedMove> READY = new ConcurrentLinkedQueue<>();
    private static final Queue<RebuildRequest> REBUILD_QUEUE = new ArrayDeque<>();
    private static final Map<String, String> ROUTES = new ConcurrentHashMap<>();
    // Never call Minecraft's profile cache lookup from a server tick: it may synchronously query Mojang.
    private static final Map<String, UUID> LOCAL_PROFILE_UUIDS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> AUTO_SPAWNED_BY_BATCH = new HashMap<>();
    private static final Set<UUID> AUTO_SPAWNED_DEPOTS = new HashSet<>();
    private static final Map<String, Long> NOTICE_TIMES = new HashMap<>();
    private static final Set<String> OPEN_TARGET_CLEANUPS = new HashSet<>();
    private static final Map<String, String> CHINESE_TRANSLATIONS = loadChineseTranslations();
    private static final AtomicInteger MISS = new AtomicInteger(), HIT = new AtomicInteger();
    private static volatile ExecutorService workers;
    private static volatile MinecraftServer server;
    private static volatile Path cachePath;
    private static volatile String lastError = "";
    private static volatile long nextDepotRestockAttemptMs = 0L;
    private static volatile String dashboardSnapshot = "{\"items\":[]}";
    private static boolean dashboardRunning;
    private static long dashboardRefreshTicks;
    private static long nextDashboardRefreshTick;
    private static String activeCpuPreset = "";
    private static long nextAllRebuildTick;
    private static boolean dashboardDirty = true;

    private FakePlayerItemSortManager() {}

    private enum MoveKind {
        NORMAL,
        WHOLE_SHULKER,
        SPLIT_SHULKER
    }

    public static void load(MinecraftServer value) {
        server = value;
        loadLocalProfileUuids(value);
        Path currentCache = FGAWorldConfigPaths.current(value, "fake-player-item-sort-cache.json");
        Path legacyCache = FGAWorldConfigPaths.legacy(value, "fake-player-item-sort-cache.json");
        try {
            cachePath = FGAWorldConfigPaths.migrate(currentCache, legacyCache, FakePlayerItemSortManager::validCacheFile);
        } catch (IOException exception) {
            cachePath = legacyCache;
        }
        try {
            if (Files.exists(cachePath)) {
                @SuppressWarnings("unchecked")
                Map<String, String> map = GSON.fromJson(Files.readString(cachePath, StandardCharsets.UTF_8), Map.class);
                if (map != null) map.forEach((k, v) -> ROUTES.put(k, v));
            }
        } catch (Exception e) {
            lastError = "cache ignored: " + e.getMessage();
        }
        recreateWorkers();
        refreshDashboardSnapshot(value);
        syncDashboard(value);
    }

    private static boolean validCacheFile(Path candidate) {
        try {
            Map<?, ?> map = GSON.fromJson(Files.readString(candidate, StandardCharsets.UTF_8), Map.class);
            return map == null || map.keySet().stream().allMatch(key -> key instanceof String);
        } catch (Exception exception) {
            return false;
        }
    }

    public static void close() {
        JOBS.clear();
        READY.clear();
        REBUILD_QUEUE.clear();
        AUTO_SPAWNED_BY_BATCH.clear();
        AUTO_SPAWNED_DEPOTS.clear();
        LOCAL_PROFILE_UUIDS.clear();
        dashboardDirty = true;
        if (workers != null) workers.shutdownNow();
        workers = null;
        FakePlayerItemSortDashboard.stop();
        dashboardRunning = false;
        persistCache();
        server = null;
    }

    public static void recreateWorkers() {
        ExecutorService old = workers;
        int cpu = Math.max(1, Runtime.getRuntime().availableProcessors());
        int size = switch (FGASettings.fakePlayerItemSortCpuThreads) {
            case "1" -> 1;
            case "2" -> cpu;
            default -> Math.max(1, cpu / 2);
        };
        workers = Executors.newFixedThreadPool(size, r -> {
            Thread t = new Thread(r, "carpet-fga-item-sort");
            t.setDaemon(true);
            return t;
        });
        activeCpuPreset = FGASettings.fakePlayerItemSortCpuThreads;
        if (old != null) old.shutdownNow();
    }

    public static boolean start(ServerPlayer player, boolean continuous, UUID initiator, StringBuilder error) {
        if (!preconditions(error)) return false;
        JOBS.put(player.getUUID(), new Job(player.getUUID(), continuous, initiator));
        return true;
    }

    public static boolean stop(ServerPlayer player) {
        return JOBS.remove(player.getUUID()) != null;
    }

    private static boolean preconditions(StringBuilder error) {
        if (!FGASettings.isFakePlayerItemSortEnabled()) {
            error.append("enable /carpet fakePlayerItemSortMode summon or quickopen");
            return false;
        }
        if (FGASettings.fakePlayerNameLength < 64) {
            error.append("set /carpet fakePlayerNameLength 64 or higher");
            return false;
        }
        if (!"quickopen".equals(FGASettings.fakePlayerItemSortMode)
                && !"always".equals(FGASettings.fakePlayerProfilePreload)
                && !"adaptive".equals(FGASettings.fakePlayerProfilePreload)) {
            error.append("set /carpet fakePlayerProfilePreload always or adaptive");
            return false;
        }
        if (("chinese".equals(FGASettings.fakePlayerItemSortTargetLanguage)
                || "custom".equals(FGASettings.fakePlayerItemSortTargetLanguage))
                && !FGASettings.fgaUnicodeArgumentsSupport) {
            error.append("set /carpet fgaUnicodeArgumentsSupport true for Chinese/custom target names");
            return false;
        }
        return true;
    }

    public static void tick(MinecraftServer value) {
        if (!Objects.equals(activeCpuPreset, FGASettings.fakePlayerItemSortCpuThreads)) recreateWorkers();
        syncDashboard(value);
        dashboardRefreshTicks++;
        if (!FGASettings.isFakePlayerItemSortEnabled()) {
            JOBS.clear();
            READY.clear();
            REBUILD_QUEUE.clear();
            AUTO_SPAWNED_BY_BATCH.clear();
            refreshDashboardIfDue(value);
            return;
        }
        int budget = 1;
        while (budget-- > 0) {
            PlannedMove move = READY.poll();
            if (move == null) break;
            apply(move);
        }
        RebuildRequest request = REBUILD_QUEUE.peek();
        if (request != null && (!request.all() || dashboardRefreshTicks >= nextAllRebuildTick)) {
            REBUILD_QUEUE.poll();
            rebuildRoute(value, request);
            if (request.all()) nextAllRebuildTick = dashboardRefreshTicks + ALL_REBUILD_INTERVAL_TICKS;
        }
        for (Iterator<Job> it = JOBS.values().iterator(); it.hasNext();) {
            Job job = it.next();
            ServerPlayer player = value.getPlayerList().getPlayer(job.player());
            if (!(player instanceof EntityPlayerMPFake)) {
                it.remove();
                continue;
            }
            if (!job.pending() && !job.coolingDown(dashboardRefreshTicks)) plan(player, job);
            if (!job.continuous() && !job.pending() && !hasSortableSourceItems(player, job)) {
                if (job.depotCleanup()) closeDepotFake(value, player);
                it.remove();
            }
        }
        refreshDashboardIfDue(value);
    }

    private static void syncDashboard(MinecraftServer minecraftServer) {
        boolean enabled = FGASettings.fakePlayerItemSortDashboard;
        if (enabled == dashboardRunning) return;
        if (enabled) FakePlayerItemSortDashboard.start(minecraftServer, FakePlayerItemSortConfig.snapshot().dashboardPort());
        else FakePlayerItemSortDashboard.stop();
        dashboardRunning = enabled;
    }

    public static int queueRebuild(ServerPlayer source, String displayedItem, UUID initiator, StringBuilder error) {
        if (!isInventoryRebuildEnabled()) {
            error.append("enable /carpet fakePlayerItemSortInventoryRebuild true");
            return 0;
        }
        List<Map.Entry<String, String>> matches = new ArrayList<>();
        String needle = normalizeLookup(displayedItem);
        for (Map.Entry<String, String> route : ROUTES.entrySet()) {
            if (normalizeLookup(serverDisplayName(route.getKey())).equals(needle)) matches.add(route);
        }
        if (matches.isEmpty()) { error.append("no cached sorter item named ").append(displayedItem); return 0; }
        if (matches.size() > 1) {
            error.append("ambiguous item: ");
            matches.forEach(match -> error.append(serverDisplayName(match.getKey())).append(" [").append(match.getValue()).append("] "));
            return 0;
        }
        Map.Entry<String, String> route = matches.get(0);
        REBUILD_QUEUE.add(new RebuildRequest(route.getKey(), route.getValue(), source.getUUID(), initiator, false));
        return 1;
    }

    public static int queueRebuildAll(ServerPlayer source, UUID initiator, StringBuilder error) {
        if (!isInventoryRebuildEnabled()) {
            error.append("enable /carpet fakePlayerItemSortInventoryRebuild true");
            return 0;
        }
        List<Map.Entry<String, String>> routes = new ArrayList<>(ROUTES.entrySet());
        routes.sort(Map.Entry.comparingByValue());
        for (Map.Entry<String, String> route : routes) {
            REBUILD_QUEUE.add(new RebuildRequest(route.getKey(), route.getValue(), source.getUUID(), initiator, true));
        }
        return routes.size();
    }

    public static boolean isInventoryRebuildEnabled() {
        return !"false".equals(FGASettings.fakePlayerItemSortInventoryRebuild);
    }

    public static boolean canRebuildAll(boolean isOp) {
        return "true".equals(FGASettings.fakePlayerItemSortInventoryRebuild)
                || ("opall".equals(FGASettings.fakePlayerItemSortInventoryRebuild) && isOp);
    }

    /** Called by explicit lifecycle and sorter inventory-access paths; never scans inventories in tick(). */
    public static void markDashboardDirty() {
        dashboardDirty = true;
    }

    private static void refreshDashboardIfDue(MinecraftServer minecraftServer) {
        if (!dashboardRunning || !dashboardDirty || dashboardRefreshTicks < nextDashboardRefreshTick) return;
        refreshDashboardSnapshot(minecraftServer);
        nextDashboardRefreshTick = dashboardRefreshTicks + 400L;
    }

    private static void loadLocalProfileUuids(MinecraftServer minecraftServer) {
        Path userCache = minecraftServer.getWorldPath(LevelResource.ROOT).getParent().resolve("usercache.json");
        try (Reader reader = Files.newBufferedReader(userCache, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonArray()) return;
            for (JsonElement element : parsed.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                if (!entry.has("name") || !entry.has("uuid")) continue;
                try { LOCAL_PROFILE_UUIDS.put(entry.get("name").getAsString().toLowerCase(Locale.ROOT), UUID.fromString(entry.get("uuid").getAsString())); }
                catch (IllegalArgumentException ignored) { }
            }
        } catch (IOException | RuntimeException ignored) {
            // Unknown names fall back to Minecraft's deterministic offline UUID; no network lookup is allowed here.
        }
    }

    private static boolean hasSortableSourceItems(ServerPlayer player, Job job) {
        for (int i = 0; i < SOURCE_SLOT_COUNT; i++) {
            if (job.depotCleanup() && !isDepotCleanupSlot(i)) continue;
            ItemStack stack = sourceStack(player.getInventory(), i);
            if (isSortableSourceStack(stack) && (!job.depotCleanup() || !isDepotAllowed(stack))) return true;
        }
        return false;
    }

    private static void plan(ServerPlayer player, Job job) {
        for (int slot = 0; slot < SOURCE_SLOT_COUNT; slot++) {
            if (job.depotCleanup() && !isDepotCleanupSlot(slot)) continue;
            ItemStack stack = sourceStack(player.getInventory(), slot);
            // Do not deserialize shulker contents on the server thread. The worker performs the
            // expensive eligibility check and routing from this immutable snapshot.
            if (stack.isEmpty()) continue;
            // Depot cleanup retains only its workbench, logs, shells, and plain empty boxes.
            // Skip those slots here so a retained item cannot starve foreign-item cleanup.
            if (job.depotCleanup() && isDepotAllowed(stack)) continue;
            ItemStack copy = stack.copy();
            job.pending(true);
            int sourceSlot = slot;
            workers.submit(() -> {
                try {
                    if (!isSortableSourceStack(copy)) return;
                    if (job.depotCleanup() && isDepotAllowed(copy)) return;
                    SourceMove sourceMove = sourceMove(copy);
                    String itemKey = sourceMove.itemKey();
                    if (ROUTES.containsKey(itemKey)) HIT.incrementAndGet(); else MISS.incrementAndGet();
                    String target = route(sourceMove.routeStack());
                    String batch = batchKey(job.player(), itemKey, target);
                    READY.add(new PlannedMove(job.player(), job.initiator(), sourceSlot, target, sourceMove.sourceKey(), itemKey, batch, sourceMove.kind()));
                } catch (Exception e) {
                    lastError = e.getMessage();
                } finally {
                    job.pending(false);
                }
            });
            break;
        }
    }

    private static String route(ItemStack stack) {
        String itemKey = itemKey(stack);
        String cached = ROUTES.get(itemKey);
        if (cached != null) return cached;
        String name = computedRoute(stack);
        ROUTES.put(itemKey, name);
        dashboardDirty = true;
        return name;
    }

    private static String computedRoute(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String base = displayName(stack, id.toString());
        FakePlayerItemSortConfig.State cfg = FakePlayerItemSortConfig.snapshot();
        String logical = FGASettings.fakePlayerItemSortQuickShulker ? base : "bulk_" + base;
        return switch (FGASettings.fakePlayerItemSortNameFormat) {
            case "prefix" -> cfg.prefix() + logical;
            case "suffix" -> logical + cfg.suffix();
            default -> logical;
        };
    }

    private static String displayName(ItemStack stack, String id) {
        FakePlayerItemSortConfig.State cfg = FakePlayerItemSortConfig.snapshot();
        String custom = cfg.names().get(id);
        if ("custom".equals(FGASettings.fakePlayerItemSortTargetLanguage) && custom != null) return normalize(custom);
        if ("chinese".equals(FGASettings.fakePlayerItemSortTargetLanguage)
                || "custom".equals(FGASettings.fakePlayerItemSortTargetLanguage)) {
            String localized = CHINESE_TRANSLATIONS.get(stack.getItem().getDescriptionId());
            return normalize(localized == null ? stack.getHoverName().getString() : localized);
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return normalize("minecraft".equals(key.getNamespace()) ? key.getPath() : key.getNamespace() + "_" + key.getPath());
    }

    private static String serverDisplayName(String key) {
        ItemStack stack = stackForKey(key);
        return stack.isEmpty() ? cleanItemKey(key) : cleanDisplayName(stack, FGASettings.fakePlayerItemSortTargetLanguage);
    }

    private static String cleanDisplayName(ItemStack stack, String mode) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String custom = FakePlayerItemSortConfig.snapshot().names().get(id.toString());
        if ("custom".equals(mode) && custom != null) return custom;
        if ("chinese".equals(mode) || "custom".equals(mode)) {
            String translated = CHINESE_TRANSLATIONS.get(stack.getItem().getDescriptionId());
            if (translated != null) return translated;
        }
        return id.getPath().replace('_', ' ');
    }

    private static ItemStack stackForKey(String key) {
        int separator = key.indexOf('|');
        ResourceLocation id = ResourceLocation.tryParse(separator < 0 ? key : key.substring(0, separator));
        return id == null || !BuiltInRegistries.ITEM.containsKey(id) ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    private static String cleanItemKey(String key) {
        int separator = key.indexOf('|');
        String id = separator < 0 ? key : key.substring(0, separator);
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()).replace('_', ' ') : id;
    }

    private static String normalizeLookup(String value) {
        return value.trim().replace('_', ' ').replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", "_").replaceAll("[^\\p{L}\\p{N}_-]", "_");
    }

    private static String itemKey(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id + "|" + stack.getComponentsPatch();
    }

    private static void rebuildRoute(MinecraftServer minecraftServer, RebuildRequest request) {
        try {
            String desiredTarget = computedRoute(stackForKey(request.itemKey()));
            if (!request.target().equals(desiredTarget)) {
                migrateRebuildRoute(minecraftServer, request, desiredTarget);
                ROUTES.put(request.itemKey(), desiredTarget);
            }
            List<ManagedInventory> targets = openRebuildTargets(minecraftServer, desiredTarget);
            if (targets.isEmpty()) return;
            TargetInventory primary = targets.get(0).inventory();
            normalizePrimaryTarget(desiredTarget, primary, request.itemKey(), request.source(), request.initiator(), new OverflowContext(null, null));
            compactLoosePrimary(primary, request.itemKey());
            if (!primary.save()) return;
            if (isShulkerTarget(request.itemKey())) compactRawShulkerOverflow(targets, request.itemKey());
            else compactBoxOverflow(targets, request.itemKey(), request.source(), request.initiator());
            dashboardDirty = true;
        } catch (IOException exception) {
            notice(request.source(), request.initiator(), "sorter rebuild skipped for " + request.target() + ": " + exception.getMessage());
        }
    }

    private static void migrateRebuildRoute(MinecraftServer minecraftServer, RebuildRequest request, String destinationName) throws IOException {
        List<ManagedInventory> sources = openRebuildTargets(minecraftServer, request.target());
        if (sources.isEmpty()) return;
        TargetInventory destination = quickopenPrimaryTarget(minecraftServer, destinationName);
        if (destination == null) throw new IOException("new language target is protected or occupied by a real player");
        for (ManagedInventory source : sources) {
            TargetInventory inventory = source.inventory();
            for (int slot = 0; slot < MAIN_SIZE; slot++) {
                ItemStack stack = inventory.main(slot);
                if (migrateRebuildStack(stack, destinationName, destination, request)) inventory.setMain(slot, ItemStack.EMPTY);
            }
            ItemStack offhand = inventory.offhand();
            if (migrateRebuildStack(offhand, destinationName, destination, request)) inventory.setOffhand(ItemStack.EMPTY);
            if (!inventory.save()) throw new IOException("could not save old language target " + source.name());
        }
        if (!destination.save()) throw new IOException("could not save new language target " + destinationName);
    }

    private static boolean migrateRebuildStack(ItemStack stack, String destinationName, TargetInventory destination, RebuildRequest request) {
        if (stack.isEmpty()) return false;
        if (itemKey(stack).equals(request.itemKey())) {
            int moved = moveLooseIntoPrimary(stack, destinationName, destination, request.itemKey(), request.source(), request.initiator(), new OverflowContext(null, null));
            stack.shrink(moved);
            return stack.isEmpty();
        }
        if (!isShulkerTarget(request.itemKey()) && isShulkerBox(stack) && shulkerContainsItem(stack, request.itemKey())) {
            return storeBoxInOverflow(stack, destinationName, request.itemKey(), request.source(), request.initiator(), new OverflowContext(null, null));
        }
        return false;
    }

    private static List<ManagedInventory> openRebuildTargets(MinecraftServer minecraftServer, String base) throws IOException {
        List<ManagedInventory> result = new ArrayList<>();
        for (int index = 0; index < 1000; index++) {
            String name = index == 0 ? base : base + "_" + index;
            if (skipped(minecraftServer, name)) break;
            ServerPlayer online = minecraftServer.getPlayerList().getPlayerByName(name);
            if (online != null) {
                if (!(online instanceof EntityPlayerMPFake)) break;
                result.add(new ManagedInventory(name, new OnlineInventory(online)));
                continue;
            }
            OfflineInventory offline = OfflineInventory.openExisting(minecraftServer, name);
            if (offline == null) break;
            result.add(new ManagedInventory(name, offline));
        }
        return result;
    }

    private static void compactLoosePrimary(TargetInventory primary, String itemKey) {
        ItemStack template = ItemStack.EMPTY;
        long total = 0;
        for (int slot = PRIMARY_LOOSE_START; slot < MAIN_SIZE; slot++) {
            ItemStack stack = primary.main(slot);
            if (!stack.isEmpty() && itemKey(stack).equals(itemKey)) {
                if (template.isEmpty()) template = stack.copyWithCount(1);
                total += stack.getCount();
                primary.setMain(slot, ItemStack.EMPTY);
            }
        }
        if (template.isEmpty()) return;
        int slot = PRIMARY_LOOSE_START;
        while (total > 0 && slot < MAIN_SIZE) {
            int count = (int) Math.min(total, template.getMaxStackSize());
            primary.setMain(slot++, template.copyWithCount(count));
            total -= count;
        }
    }

    private static void compactRawShulkerOverflow(List<ManagedInventory> targets, String itemKey) {
        List<ItemStack> boxes = new ArrayList<>();
        for (int target = 1; target < targets.size(); target++) {
            TargetInventory inventory = targets.get(target).inventory();
            for (int slot = 0; slot < MAIN_SIZE; slot++) {
                ItemStack stack = inventory.main(slot);
                if (!stack.isEmpty() && itemKey(stack).equals(itemKey)) { boxes.add(stack.copy()); inventory.setMain(slot, ItemStack.EMPTY); }
            }
        }
        int cursor = 0;
        for (int target = 1; target < targets.size() && cursor < boxes.size(); target++) {
            TargetInventory inventory = targets.get(target).inventory();
            for (int slot = 0; slot < MAIN_SIZE && cursor < boxes.size(); slot++) {
                if (inventory.main(slot).isEmpty()) inventory.setMain(slot, boxes.get(cursor++));
            }
            inventory.save();
        }
    }

    private static void compactBoxOverflow(List<ManagedInventory> targets, String itemKey, UUID source, UUID initiator) {
        List<ItemStack> completed = new ArrayList<>();
        for (int target = 1; target < targets.size(); target++) {
            TargetInventory inventory = targets.get(target).inventory();
            normalizeOverflowTarget(inventory, itemKey, source, initiator);
        }
        TargetInventory receiver = null;
        for (int target = 1; target < targets.size(); target++) {
            TargetInventory inventory = targets.get(target).inventory();
            ItemStack partial = inventory.offhand();
            if (!isUsableShulkerFor(partial, itemKey)) continue;
            if (receiver == null) { receiver = inventory; continue; }
            if (receiver == inventory) continue;
            ItemStack destination = receiver.offhand();
            int moved = mergeShulkerContents(destination, partial, itemKey);
            if (moved > 0) {
                receiver.setOffhand(destination);
                inventory.setOffhand(partial);
                if (isOrdinaryEmptyShulker(partial) && returnEmptyShulkerToDepot(partial)) inventory.setOffhand(ItemStack.EMPTY);
            }
            if (isCompletedBoxFor(destination, itemKey)) {
                for (int earlier = 1; earlier < targets.size(); earlier++) {
                    int empty = firstEmptyMainSlot(targets.get(earlier).inventory());
                    if (empty >= 0) {
                        targets.get(earlier).inventory().setMain(empty, destination);
                        receiver.setOffhand(ItemStack.EMPTY);
                        break;
                    }
                }
                receiver = null;
            }
        }
        for (int target = 1; target < targets.size(); target++) {
            TargetInventory inventory = targets.get(target).inventory();
            for (int slot = 0; slot < MAIN_SIZE; slot++) {
                ItemStack stack = inventory.main(slot);
                if (isCompletedBoxFor(stack, itemKey)) { completed.add(stack.copyWithCount(1)); inventory.setMain(slot, ItemStack.EMPTY); }
            }
        }
        int cursor = 0;
        for (int target = 1; target < targets.size(); target++) {
            TargetInventory inventory = targets.get(target).inventory();
            for (int slot = 0; slot < MAIN_SIZE && cursor < completed.size(); slot++) {
                if (inventory.main(slot).isEmpty()) inventory.setMain(slot, completed.get(cursor++));
            }
            inventory.save();
        }
    }

    private static ItemStack sourceStack(Inventory inventory, int slot) {
        //#if MC >= 1.21.10
        //$$ return inventory.getStack(slot);
        //#else
        if (slot < MAIN_SIZE) return inventory.getItem(slot);
        if (slot < SOURCE_OFFHAND_SLOT) return inventory.armor.get(slot - SOURCE_ARMOR_START);
        return inventory.offhand.get(0);
        //#endif
    }

    private static void setSourceStack(Inventory inventory, int slot, ItemStack stack) {
        //#if MC >= 1.21.10
        //$$ inventory.setStack(slot, stack);
        //#else
        if (slot < MAIN_SIZE) inventory.setItem(slot, stack);
        else if (slot < SOURCE_OFFHAND_SLOT) inventory.armor.set(slot - SOURCE_ARMOR_START, stack);
        else inventory.offhand.set(0, stack);
        //#endif
    }

    private static boolean isSortableSourceStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!isShulkerBox(stack) || !hasShulkerContents(stack)) return true;
        return isFullSingleItemShulker(stack) || FGASettings.fakePlayerItemSortQuickShulker;
    }

    private static SourceMove sourceMove(ItemStack stack) {
        String sourceKey = itemKey(stack);
        if (isShulkerBox(stack) && hasShulkerContents(stack)) {
            ItemStack first = firstShulkerContent(stack);
            if (!first.isEmpty() && isFullSingleItemShulker(stack)) {
                return new SourceMove(sourceKey, itemKey(first), first.copyWithCount(1), MoveKind.WHOLE_SHULKER);
            }
            if (!first.isEmpty() && FGASettings.fakePlayerItemSortQuickShulker) {
                return new SourceMove(sourceKey, itemKey(first), first.copyWithCount(1), MoveKind.SPLIT_SHULKER);
            }
        }
        return new SourceMove(sourceKey, sourceKey, stack, MoveKind.NORMAL);
    }

    private static String batchKey(UUID source, String itemKey, String target) {
        return source + "|" + itemKey + "|" + target;
    }

    private static void apply(PlannedMove move) {
        MinecraftServer minecraftServer = server;
        if (minecraftServer == null) return;
        ServerPlayer source = minecraftServer.getPlayerList().getPlayer(move.source());
        if (!(source instanceof EntityPlayerMPFake)) return;
        ItemStack current = sourceStack(source.getInventory(), move.slot());
        if (current.isEmpty() || !itemKey(current).equals(move.sourceKey())) return;

        int moved;
        if ("quickopen".equals(FGASettings.fakePlayerItemSortMode)) {
            moved = moveQuickopen(current, move.target(), move.itemKey(), move.kind(), move.source(), move.initiator());
        } else {
            moved = moveSummon(current, source, move.target(), move.batch(), move.itemKey(), move.kind(), move.initiator());
        }
        if (moved <= 0) return;

        markDashboardDirty();

        // Spread work smoothly across ticks instead of processing a burst in a single tick.
        Job job = JOBS.get(move.source());
        if (job != null) job.pauseUntil(dashboardRefreshTicks + sorterMoveIntervalTicks());

        if (move.kind() == MoveKind.SPLIT_SHULKER) {
            source.getInventory().setChanged();
        } else {
            current.shrink(moved);
            if (current.isEmpty()) setSourceStack(source.getInventory(), move.slot(), ItemStack.EMPTY);
        }
        finishAutoSpawnedBatchIfComplete(source, move.batch(), move.itemKey());
    }

    private static int moveQuickopen(ItemStack current, String baseTarget, String itemKey, MoveKind kind, UUID sourceId, UUID initiator) {
        MinecraftServer minecraftServer = server;
        if (minecraftServer == null) return 0;
        if (kind == MoveKind.WHOLE_SHULKER) {
            try {
                TargetInventory primary = quickopenPrimaryTarget(minecraftServer, baseTarget);
                if (primary == null) return 0;
                cleanOpenedTarget(primary, baseTarget, itemKey, sourceId, initiator, new OverflowContext(null, null), true);
                normalizePrimaryTarget(baseTarget, primary, itemKey, sourceId, initiator, new OverflowContext(null, null));
                if (!primary.save()) return 0;
                int moved = moveFullBoxIntoOfflineOverflow(current, baseTarget, itemKey);
                if (moved <= 0) lastError = "no quickopen overflow room for " + baseTarget;
                return moved;
            } catch (IOException e) {
                lastError = "quickopen overflow failed for " + baseTarget + ": " + e.getMessage();
                return 0;
            }
        }
        try {
            TargetInventory primary = quickopenPrimaryTarget(minecraftServer, baseTarget);
            if (primary == null) return 0;
            OverflowContext overflow = new OverflowContext(null, null);
            cleanOpenedTarget(primary, baseTarget, itemKey, sourceId, initiator, overflow, true);
            normalizePrimaryTarget(baseTarget, primary, itemKey, sourceId, initiator, overflow);
            ItemStack sourceForMove = kind == MoveKind.SPLIT_SHULKER ? current.copy() : current;
            int moved = kind == MoveKind.SPLIT_SHULKER
                    ? moveShulkerContentsIntoPrimary(sourceForMove, baseTarget, primary, itemKey, sourceId, initiator, overflow)
                    : moveLooseIntoPrimary(current, baseTarget, primary, itemKey, sourceId, initiator, overflow);
            if (moved > 0) {
                if (!primary.save()) return 0;
                if (kind == MoveKind.SPLIT_SHULKER) copyShulkerContents(current, sourceForMove);
                return moved;
            }
            if (primary.save()) return 0;
            return 0;
        } catch (IOException e) {
            lastError = "quickopen failed for " + baseTarget + ": " + e.getMessage();
            return 0;
        }
    }

    private static TargetInventory quickopenPrimaryTarget(MinecraftServer minecraftServer, String name) throws IOException {
        markDashboardDirty();
        ServerPlayer online = minecraftServer.getPlayerList().getPlayerByName(name);
        if (online != null) {
            if (online instanceof EntityPlayerMPFake) return new OnlineInventory(online);
            lastError = "quickopen target is a real online player, skipped: " + name;
            return null;
        }
        return OfflineInventory.open(minecraftServer, name);
    }

    /** Runs only for an inventory opened by an active sort. Target armor is never read or changed. */
    private static void cleanOpenedTarget(TargetInventory target, String currentTarget, String expectedItemKey,
                                          UUID sourceId, UUID initiator, OverflowContext context, boolean primary) {
        if (!FGASettings.fakePlayerItemSortCleanOpenedTarget || !OPEN_TARGET_CLEANUPS.add(currentTarget)) return;
        try {
            for (int slot = 0; slot <= MAIN_SIZE; slot++) {
                ItemStack stack = slot == MAIN_SIZE ? target.offhand() : target.main(slot);
                if (stack.isEmpty() || belongsToOpenedTarget(stack, expectedItemKey, primary, slot == MAIN_SIZE)) continue;
                SourceMove move = sourceMove(stack.copy());
                String destination = route(move.routeStack());
                if (destination.equals(currentTarget)) continue;
                int moved = 0;
                if (context.source() == null) {
                    moved = moveQuickopen(stack, destination, move.itemKey(), move.kind(), sourceId, initiator);
                } else if (target instanceof OnlineInventory online) {
                    moved = moveSummon(stack, online.player(), destination, context.batch(), move.itemKey(), move.kind(), initiator);
                }
                if (moved <= 0) continue;
                if (move.kind() != MoveKind.SPLIT_SHULKER) {
                    stack.shrink(moved);
                    if (stack.isEmpty()) {
                        if (slot == MAIN_SIZE) target.setOffhand(ItemStack.EMPTY); else target.setMain(slot, ItemStack.EMPTY);
                    }
                }
            }
        } finally {
            OPEN_TARGET_CLEANUPS.remove(currentTarget);
        }
    }

    private static boolean belongsToOpenedTarget(ItemStack stack, String expectedItemKey, boolean primary, boolean offhand) {
        if (primary) {
            return itemKey(stack).equals(expectedItemKey)
                    || (isShulkerBox(stack) && itemKey(firstShulkerContent(stack)).equals(expectedItemKey));
        }
        if (isShulkerTarget(expectedItemKey)) return itemKey(stack).equals(expectedItemKey);
        return isCompletedBoxFor(stack, expectedItemKey)
                || (offhand && isUsableShulkerFor(stack, expectedItemKey))
                || (isShulkerBox(stack) && shulkerContainsItem(stack, expectedItemKey));
    }

    private static int moveSummon(ItemStack current, ServerPlayer source, String base, String batch, String itemKey,
                                  MoveKind kind, UUID initiator) {
        if (!FGASettings.fakePlayerItemSortQuickShulker) {
            OnlineInventory target = looseTarget(source, base, batch, itemKey);
            if (target == null) return 0;
            int moved = moveLoose(current, target, itemKey);
            if (moved > 0) target.save();
            return moved;
        }
        if (kind == MoveKind.WHOLE_SHULKER) {
            OnlineInventory primary = primaryOnlineTarget(source, base, batch);
            if (primary == null) return 0;
            cleanOpenedTarget(primary, base, itemKey, source.getUUID(), initiator, new OverflowContext(source, batch), true);
            normalizePrimaryTarget(base, primary, itemKey, source.getUUID(), initiator, new OverflowContext(source, batch));
            primary.save();
            return moveFullBoxIntoOnlineOverflow(current, source, base, batch, itemKey);
        }
        OnlineInventory primary = primaryOnlineTarget(source, base, batch);
        if (primary == null) return 0;
        OverflowContext overflow = new OverflowContext(source, batch);
        cleanOpenedTarget(primary, base, itemKey, source.getUUID(), initiator, overflow, true);
        normalizePrimaryTarget(base, primary, itemKey, source.getUUID(), initiator, overflow);
        int moved = kind == MoveKind.SPLIT_SHULKER
                ? moveShulkerContentsIntoPrimary(current, base, primary, itemKey, source.getUUID(), initiator, overflow)
                : moveLooseIntoPrimary(current, base, primary, itemKey, source.getUUID(), initiator, overflow);
        primary.save();
        return moved;
    }

    private static OnlineInventory looseTarget(ServerPlayer source, String base, String batch, String itemKey) {
        MinecraftServer minecraftServer = server;
        if (minecraftServer == null) return null;
        for (int index = 0; index < 1000; index++) {
            String name = index == 0 ? base : base + "_" + index;
            ServerPlayer existing = minecraftServer.getPlayerList().getPlayerByName(name);
            if (existing != null) {
                if (!(existing instanceof EntityPlayerMPFake) || skipped(minecraftServer, name)) continue;
                existing.kill();
            }
            if (skipped(minecraftServer, name)) continue;
            if (!EntityPlayerMPFake.createFake(name, minecraftServer, source.position(), source.getYRot(), source.getXRot(),
                    source.serverLevel().dimension(), GameType.SURVIVAL, false)) continue;
            ServerPlayer spawned = minecraftServer.getPlayerList().getPlayerByName(name);
            if (!(spawned instanceof EntityPlayerMPFake)) continue;
            AUTO_SPAWNED_BY_BATCH.computeIfAbsent(batch, ignored -> new LinkedHashSet<>()).add(name);
            OnlineInventory inventory = new OnlineInventory(spawned);
            if (hasLooseRoom(inventory, itemKey)) return inventory;
        }
        lastError = "no usable sorter target for " + base;
        return null;
    }

    private static OnlineInventory primaryOnlineTarget(ServerPlayer source, String base, String batch) {
        MinecraftServer minecraftServer = server;
        if (minecraftServer == null || skipped(minecraftServer, base)) return null;
        ServerPlayer existing = minecraftServer.getPlayerList().getPlayerByName(base);
        if (existing != null) {
            if (!(existing instanceof EntityPlayerMPFake)) return null;
            existing.kill();
        }
        if (!EntityPlayerMPFake.createFake(base, minecraftServer, source.position(), source.getYRot(), source.getXRot(),
                source.serverLevel().dimension(), GameType.SURVIVAL, false)) return null;
        ServerPlayer spawned = minecraftServer.getPlayerList().getPlayerByName(base);
        if (!(spawned instanceof EntityPlayerMPFake)) return null;
        AUTO_SPAWNED_BY_BATCH.computeIfAbsent(batch, ignored -> new LinkedHashSet<>()).add(base);
        return new OnlineInventory(spawned);
    }

    private static boolean skipped(MinecraftServer minecraftServer, String name) {
        String mode = FGASettings.fakePlayerItemSortWhitelist;
        if ("modWhitelist".equals(mode) && FakePlayerItemSortConfig.snapshot().whitelist().contains(name)) return true;
        if (!"vanillaWhitelist".equals(mode)) return false;
        for (String value : minecraftServer.getPlayerList().getWhiteList().getUserList()) {
            if (value.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static boolean hasLooseRoom(TargetInventory inventory, String itemKey) {
        for (int i = 0; i < MAIN_SIZE; i++) {
            ItemStack stack = inventory.main(i);
            if (stack.isEmpty() || (itemKey(stack).equals(itemKey) && stack.getCount() < stack.getMaxStackSize())) return true;
        }
        ItemStack offhand = inventory.offhand();
        return offhand.isEmpty() || (itemKey(offhand).equals(itemKey) && offhand.getCount() < offhand.getMaxStackSize());
    }

    private static int moveLoose(ItemStack from, TargetInventory target, String itemKey) {
        int left = from.getCount();
        for (int i = 0; i < MAIN_SIZE && left > 0; i++) {
            ItemStack dest = target.main(i);
            if (!dest.isEmpty() && itemKey(dest).equals(itemKey) && dest.getCount() < dest.getMaxStackSize()) {
                int n = Math.min(left, dest.getMaxStackSize() - dest.getCount());
                dest.grow(n);
                left -= n;
            }
        }
        ItemStack offhand = target.offhand();
        if (!offhand.isEmpty() && itemKey(offhand).equals(itemKey) && offhand.getCount() < offhand.getMaxStackSize() && left > 0) {
            int n = Math.min(left, offhand.getMaxStackSize() - offhand.getCount());
            offhand.grow(n);
            left -= n;
        }
        for (int i = 0; i < MAIN_SIZE && left > 0; i++) {
            if (target.main(i).isEmpty()) {
                int n = Math.min(left, from.getMaxStackSize());
                target.setMain(i, FGACompat.copyWithCount(from, n));
                left -= n;
            }
        }
        if (target.offhand().isEmpty() && left > 0) {
            int n = Math.min(left, from.getMaxStackSize());
            target.setOffhand(FGACompat.copyWithCount(from, n));
            left -= n;
        }
        return from.getCount() - left;
    }

    private static int moveLooseIntoPrimary(ItemStack from, String baseTarget, TargetInventory target, String itemKey,
                                            UUID sourceId, UUID initiator, OverflowContext overflow) {
        if (!from.getItem().canFitInsideContainerItems() && !isShulkerTarget(itemKey)) {
            lastError = "item cannot be packed into shulker boxes: " + BuiltInRegistries.ITEM.getKey(from.getItem());
            return 0;
        }
        normalizePrimaryTarget(baseTarget, target, itemKey, sourceId, initiator, overflow);
        int left = from.getCount();
        for (int i = PRIMARY_LOOSE_START; i < MAIN_SIZE && left > 0; i++) {
            ItemStack dest = target.main(i);
            if (!dest.isEmpty() && itemKey(dest).equals(itemKey) && dest.getCount() < dest.getMaxStackSize()) {
                int n = Math.min(left, dest.getMaxStackSize() - dest.getCount());
                dest.grow(n);
                left -= n;
            }
        }
        for (int i = PRIMARY_LOOSE_START; i < MAIN_SIZE && left > 0; i++) {
            if (target.main(i).isEmpty()) {
                int n = Math.min(left, from.getMaxStackSize());
                target.setMain(i, FGACompat.copyWithCount(from, n));
                left -= n;
            }
        }
        if (left > 0) {
            int boxed = isShulkerTarget(itemKey)
                    ? moveLooseShulkerTargetIntoOverflow(from, left, baseTarget, itemKey, sourceId, initiator, overflow)
                    : moveLooseIntoOverflow(from, left, baseTarget, itemKey, sourceId, initiator, overflow);
            left -= boxed;
        }
        return from.getCount() - left;
    }

    private static void normalizePrimaryTarget(String baseTarget, TargetInventory target, String itemKey,
                                               UUID sourceId, UUID initiator, OverflowContext overflow) {
        for (int i = 0; i < MAIN_SIZE; i++) {
            ItemStack stack = target.main(i);
            if (stack.isEmpty()) continue;
            if (isShulkerBox(stack) && !isShulkerTarget(itemKey)) {
                if (storeBoxInOverflow(stack, baseTarget, itemKey, sourceId, initiator, overflow)) target.setMain(i, ItemStack.EMPTY);
            } else if (i <= PRIMARY_HOTBAR_END && itemKey(stack).equals(itemKey)) {
                int moved;
                if (isShulkerTarget(itemKey)) {
                    moved = moveLooseWithinPrimary(stack, target, itemKey);
                    if (moved < stack.getCount()) {
                        moved += moveLooseShulkerTargetIntoOverflow(stack, stack.getCount() - moved, baseTarget,
                                itemKey, sourceId, initiator, overflow);
                    }
                } else {
                    moved = moveLooseIntoOverflow(stack, stack.getCount(), baseTarget, itemKey, sourceId, initiator, overflow);
                }
                stack.shrink(moved);
                if (stack.isEmpty()) target.setMain(i, ItemStack.EMPTY);
            }
        }
        ItemStack offhand = target.offhand();
        if (!offhand.isEmpty() && ((isShulkerBox(offhand) && !isShulkerTarget(itemKey)) || itemKey(offhand).equals(itemKey))) {
            boolean moved = isShulkerTarget(itemKey)
                    ? moveLooseShulkerTargetIntoOverflow(offhand, offhand.getCount(), baseTarget, itemKey, sourceId, initiator, overflow) == offhand.getCount()
                    : isShulkerBox(offhand)
                            ? storeBoxInOverflow(offhand, baseTarget, itemKey, sourceId, initiator, overflow)
                            : moveLooseIntoOverflow(offhand, offhand.getCount(), baseTarget, itemKey, sourceId, initiator, overflow) == offhand.getCount();
            if (moved) target.setOffhand(ItemStack.EMPTY);
        }
    }

    private static int moveLooseWithinPrimary(ItemStack source, TargetInventory target, String itemKey) {
        int left = source.getCount();
        for (int slot = PRIMARY_LOOSE_START; slot < MAIN_SIZE && left > 0; slot++) {
            ItemStack destination = target.main(slot);
            if (!destination.isEmpty() && itemKey(destination).equals(itemKey) && destination.getCount() < destination.getMaxStackSize()) {
                int moved = Math.min(left, destination.getMaxStackSize() - destination.getCount());
                destination.grow(moved);
                left -= moved;
            }
        }
        for (int slot = PRIMARY_LOOSE_START; slot < MAIN_SIZE && left > 0; slot++) {
            if (target.main(slot).isEmpty()) {
                int moved = Math.min(left, source.getMaxStackSize());
                target.setMain(slot, FGACompat.copyWithCount(source, moved));
                left -= moved;
            }
        }
        return source.getCount() - left;
    }

    private static int moveShulkerContentsIntoPrimary(ItemStack sourceBox, String baseTarget, TargetInventory target,
                                                      String itemKey, UUID sourceId, UUID initiator, OverflowContext overflow) {
        if (!isShulkerBox(sourceBox)) return 0;
        NonNullList<ItemStack> contents = shulkerContents(sourceBox);
        int moved = 0;
        for (int slot = 0; slot < contents.size(); slot++) {
            ItemStack content = contents.get(slot);
            if (content.isEmpty() || !itemKey(content).equals(itemKey)) continue;
            int before = content.getCount();
            int n = moveLooseIntoPrimary(content.copy(), baseTarget, target, itemKey, sourceId, initiator, overflow);
            if (n <= 0) break;
            content.shrink(n);
            if (content.isEmpty()) contents.set(slot, ItemStack.EMPTY);
            moved += n;
            if (n < before) break;
        }
        if (moved > 0) setShulkerContents(sourceBox, contents);
        return moved;
    }

    private static int moveLooseIntoOverflow(ItemStack source, int count, String baseTarget, String itemKey,
                                             UUID sourceId, UUID initiator, OverflowContext context) {
        int moved = 0;
        for (int index = 1; index < 1000 && moved < count; index++) {
            TargetInventory overflow;
            try {
                overflow = openOverflowTarget(baseTarget, index, context);
            } catch (IOException e) {
                notice(sourceId, initiator, "overflow open failed for " + baseTarget + "_" + index + ": " + e.getMessage());
                return moved;
            }
            if (overflow == null) continue;
            cleanOpenedTarget(overflow, baseTarget + "_" + index, itemKey, sourceId, initiator, context, false);
            normalizeOverflowTarget(overflow, itemKey, sourceId, initiator);
            int n = fillOverflowOffhand(overflow, source, count - moved, itemKey, sourceId, initiator);
            if (n > 0) {
                moved += n;
                if (!overflow.save()) return moved - n;
            }
        }
        return moved;
    }

    /** Shulker boxes cannot be nested. Their overflow targets therefore store loose boxes directly. */
    private static int moveLooseShulkerTargetIntoOverflow(ItemStack source, int count, String baseTarget, String itemKey,
                                                          UUID sourceId, UUID initiator, OverflowContext context) {
        int moved = 0;
        for (int index = 1; index < 1000 && moved < count; index++) {
            TargetInventory overflow;
            try {
                overflow = openOverflowTarget(baseTarget, index, context);
            } catch (IOException e) {
                notice(sourceId, initiator, "shulker overflow open failed for " + baseTarget + "_" + index + ": " + e.getMessage());
                return moved;
            }
            if (overflow == null) continue;
            cleanOpenedTarget(overflow, baseTarget + "_" + index, itemKey, sourceId, initiator, context, false);
            int left = count - moved;
            for (int slot = 0; slot < MAIN_SIZE && left > 0; slot++) {
                ItemStack destination = overflow.main(slot);
                if (!destination.isEmpty() && itemKey(destination).equals(itemKey)
                        && destination.getCount() < destination.getMaxStackSize()) {
                    int added = Math.min(left, destination.getMaxStackSize() - destination.getCount());
                    destination.grow(added);
                    left -= added;
                }
            }
            for (int slot = 0; slot < MAIN_SIZE && left > 0; slot++) {
                if (overflow.main(slot).isEmpty()) {
                    int added = Math.min(left, source.getMaxStackSize());
                    overflow.setMain(slot, FGACompat.copyWithCount(source, added));
                    left -= added;
                }
            }
            int added = count - moved - left;
            if (added > 0 && overflow.save()) moved += added;
        }
        return moved;
    }

    private static boolean storeBoxInOverflow(ItemStack box, String baseTarget, String itemKey,
                                              UUID sourceId, UUID initiator, OverflowContext context) {
        if (isCompletedBoxFor(box, itemKey)) return moveFullBoxIntoOverflow(box, baseTarget, itemKey, context);
        for (int index = 1; index < 1000; index++) {
            TargetInventory overflow;
            try {
                overflow = openOverflowTarget(baseTarget, index, context);
            } catch (IOException e) {
                notice(sourceId, initiator, "overflow open failed for " + baseTarget + "_" + index + ": " + e.getMessage());
                return false;
            }
            if (overflow == null) continue;
            cleanOpenedTarget(overflow, baseTarget + "_" + index, itemKey, sourceId, initiator, context, false);
            normalizeOverflowTarget(overflow, itemKey, sourceId, initiator);
            if (isUsableShulkerFor(box, itemKey) && overflow.offhand().isEmpty()) {
                overflow.setOffhand(box.copyWithCount(1));
                return overflow.save();
            }
            int slot = firstEmptyMainSlot(overflow);
            if (slot < 0) continue;
            overflow.setMain(slot, box.copyWithCount(1));
            return overflow.save();
        }
        return false;
    }

    private static TargetInventory openOverflowTarget(String baseTarget, int index, OverflowContext context) throws IOException {
        MinecraftServer minecraftServer = server;
        if (minecraftServer == null) return null;
        String name = baseTarget + "_" + index;
        if (skipped(minecraftServer, name)) return null;
        ServerPlayer existing = minecraftServer.getPlayerList().getPlayerByName(name);
        if (context.source() == null) {
            if (existing != null) return existing instanceof EntityPlayerMPFake ? new OnlineInventory(existing) : null;
            return OfflineInventory.open(minecraftServer, name);
        }
        if (existing != null) {
            if (!(existing instanceof EntityPlayerMPFake)) return null;
            existing.kill();
        }
        if (!EntityPlayerMPFake.createFake(name, minecraftServer, context.source().position(), context.source().getYRot(),
                context.source().getXRot(), context.source().serverLevel().dimension(), GameType.SURVIVAL, false)) return null;
        ServerPlayer spawned = minecraftServer.getPlayerList().getPlayerByName(name);
        if (!(spawned instanceof EntityPlayerMPFake)) return null;
        AUTO_SPAWNED_BY_BATCH.computeIfAbsent(context.batch(), ignored -> new LinkedHashSet<>()).add(name);
        return new OnlineInventory(spawned);
    }

    private static void normalizeOverflowTarget(TargetInventory target, String itemKey, UUID sourceId, UUID initiator) {
        ItemStack offhand = target.offhand();
        if (isCompletedBoxFor(offhand, itemKey)) {
            int slot = firstEmptyMainSlot(target);
            if (slot >= 0) {
                target.setMain(slot, offhand);
                target.setOffhand(ItemStack.EMPTY);
                offhand = ItemStack.EMPTY;
            }
        }
        for (int slot = 0; slot < MAIN_SIZE; slot++) {
            ItemStack stack = target.main(slot);
            if (stack.isEmpty()) continue;
            if (!isShulkerBox(stack)) {
                if (!itemKey(stack).equals(itemKey)) notice(sourceId, initiator, "other item found in overflow target");
                continue;
            }
            if (!isUsableShulkerFor(stack, itemKey)) continue;
            offhand = target.offhand();
            if (offhand.isEmpty()) {
                target.setOffhand(stack);
                target.setMain(slot, ItemStack.EMPTY);
                continue;
            }
            if (!isUsableShulkerFor(offhand, itemKey)) continue;
            int moved = mergeShulkerContents(offhand, stack, itemKey);
            if (moved <= 0) continue;
            target.setOffhand(offhand);
            if (isOrdinaryEmptyShulker(stack) && returnEmptyShulkerToDepot(stack)) target.setMain(slot, ItemStack.EMPTY);
            if (isCompletedBoxFor(offhand, itemKey)) {
                int empty = firstEmptyMainSlot(target);
                if (empty >= 0) {
                    target.setMain(empty, offhand);
                    target.setOffhand(ItemStack.EMPTY);
                }
            }
        }
    }

    private static int fillOverflowOffhand(TargetInventory target, ItemStack source, int count, String itemKey,
                                           UUID sourceId, UUID initiator) {
        ItemStack offhand = target.offhand();
        if (!isUsableShulkerFor(offhand, itemKey)) {
            if (!offhand.isEmpty()) return 0;
            offhand = takeEmptyShulkerFromDepot(sourceId, initiator);
            if (offhand.isEmpty()) return 0;
            target.setOffhand(offhand);
        }
        int moved = addToShulker(offhand, source, count, itemKey);
        target.setOffhand(offhand);
        if (isCompletedBoxFor(offhand, itemKey)) {
            int slot = firstEmptyMainSlot(target);
            if (slot >= 0) {
                target.setMain(slot, offhand);
                target.setOffhand(ItemStack.EMPTY);
            }
        }
        return moved;
    }

    private static int mergeShulkerContents(ItemStack destination, ItemStack source, String itemKey) {
        NonNullList<ItemStack> contents = shulkerContents(source);
        int moved = 0;
        for (int slot = 0; slot < contents.size(); slot++) {
            ItemStack stack = contents.get(slot);
            if (stack.isEmpty() || !itemKey(stack).equals(itemKey)) continue;
            int n = addToShulker(destination, stack, stack.getCount(), itemKey);
            if (n <= 0) break;
            stack.shrink(n);
            if (stack.isEmpty()) contents.set(slot, ItemStack.EMPTY);
            moved += n;
        }
        if (moved > 0) setShulkerContents(source, contents);
        return moved;
    }

    private static boolean returnEmptyShulkerToDepot(ItemStack box) {
        MinecraftServer minecraftServer = server;
        if (minecraftServer == null || !isOrdinaryEmptyShulker(box)) return false;
        try {
            ManagedInventory depot = openDepot(minecraftServer);
            if (depot == null) return false;
            int slot = firstEmptyMainSlot(depot.inventory());
            if (slot < 0) return false;
            depot.inventory().setMain(slot, box.copyWithCount(1));
            return depot.inventory().save();
        } catch (IOException e) {
            lastError = "empty shulker return failed: " + e.getMessage();
            return false;
        }
    }


    private static int addToShulker(ItemStack shulker, ItemStack source, int count, String itemKey) {
        NonNullList<ItemStack> contents = shulkerContents(shulker);
        int left = count;
        for (int i = 0; i < contents.size() && left > 0; i++) {
            ItemStack dest = contents.get(i);
            if (!dest.isEmpty() && itemKey(dest).equals(itemKey) && dest.getCount() < dest.getMaxStackSize()) {
                int n = Math.min(left, dest.getMaxStackSize() - dest.getCount());
                dest.grow(n);
                left -= n;
            }
        }
        for (int i = 0; i < contents.size() && left > 0; i++) {
            if (contents.get(i).isEmpty()) {
                int n = Math.min(left, source.getMaxStackSize());
                contents.set(i, FGACompat.copyWithCount(source, n));
                left -= n;
            }
        }
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        return count - left;
    }

    private static int shulkerFreeSpace(ItemStack shulker, String itemKey) {
        if (!isPlainShulker(shulker)) return 0;
        int space = 0;
        for (ItemStack stack : shulkerContents(shulker)) {
            if (stack.isEmpty()) space += 64;
            else if (itemKey(stack).equals(itemKey)) space += stack.getMaxStackSize() - stack.getCount();
            else return 0;
        }
        return space;
    }

    private static NonNullList<ItemStack> shulkerContents(ItemStack shulker) {
        NonNullList<ItemStack> contents = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
        ItemContainerContents component = shulker.get(DataComponents.CONTAINER);
        if (component != null) component.copyInto(contents);
        return contents;
    }

    private static void setShulkerContents(ItemStack shulker, NonNullList<ItemStack> contents) {
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
    }

    private static void copyShulkerContents(ItemStack target, ItemStack source) {
        setShulkerContents(target, shulkerContents(source));
    }

    private static boolean isUsableShulkerFor(ItemStack stack, String itemKey) {
        return isPlainShulker(stack) && shulkerFreeSpace(stack, itemKey) > 0;
    }

    private static boolean isOrdinaryEmptyShulker(ItemStack stack) {
        return isPlainShulker(stack) && shulkerContents(stack).stream().allMatch(ItemStack::isEmpty);
    }

    private static boolean isPlainShulker(ItemStack stack) {
        return stack.is(Items.SHULKER_BOX) && stack.get(DataComponents.CUSTOM_NAME) == null;
    }

    private static boolean isShulkerTarget(String itemKey) {
        int component = itemKey.indexOf('|');
        return "minecraft:shulker_box".equals(component < 0 ? itemKey : itemKey.substring(0, component));
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static boolean hasShulkerContents(ItemStack stack) {
        return shulkerContents(stack).stream().anyMatch(content -> !content.isEmpty());
    }

    private static ItemStack firstShulkerContent(ItemStack stack) {
        for (ItemStack content : shulkerContents(stack)) if (!content.isEmpty()) return content;
        return ItemStack.EMPTY;
    }

    private static boolean isFullSingleItemShulker(ItemStack stack) {
        if (!isShulkerBox(stack)) return false;
        String key = "";
        for (ItemStack content : shulkerContents(stack)) {
            if (content.isEmpty() || content.getCount() < content.getMaxStackSize()) return false;
            String contentKey = itemKey(content);
            if (key.isEmpty()) key = contentKey;
            else if (!key.equals(contentKey)) return false;
        }
        return !key.isEmpty();
    }

    private static boolean isCompletedBoxFor(ItemStack stack, String itemKey) {
        return isFullSingleItemShulker(stack) && itemKey(firstShulkerContent(stack)).equals(itemKey);
    }

    private static boolean moveFullBoxIntoOverflow(ItemStack box, String baseTarget, String itemKey, OverflowContext context) {
        try {
            if (context.source() == null) return moveFullBoxIntoOfflineOverflow(box, baseTarget, itemKey) > 0;
            return moveFullBoxIntoOnlineOverflow(box, context.source(), baseTarget, context.batch(), itemKey) > 0;
        } catch (IOException e) {
            lastError = "overflow write failed for " + baseTarget + ": " + e.getMessage();
            return false;
        }
    }

    private static int moveFullBoxIntoOfflineOverflow(ItemStack box, String baseTarget, String itemKey) throws IOException {
        for (int index = 1; index < 1000; index++) {
            TargetInventory overflow = openOverflowTarget(baseTarget, index, new OverflowContext(null, null));
            if (overflow == null) continue;
            cleanOpenedTarget(overflow, baseTarget + "_" + index, itemKey, null, null, new OverflowContext(null, null), false);
            normalizeOverflowTarget(overflow, itemKey, null, null);
            int slot = firstEmptyMainSlot(overflow);
            if (slot < 0) continue;
            overflow.setMain(slot, box.copyWithCount(1));
            return overflow.save() ? 1 : 0;
        }
        return 0;
    }

    private static int moveFullBoxIntoOnlineOverflow(ItemStack box, ServerPlayer source, String baseTarget, String batch,
                                                     String itemKey) {
        for (int index = 1; index < 1000; index++) {
            TargetInventory overflow;
            try {
                overflow = openOverflowTarget(baseTarget, index, new OverflowContext(source, batch));
            } catch (IOException e) {
                lastError = "overflow write failed for " + baseTarget + "_" + index + ": " + e.getMessage();
                return 0;
            }
            if (overflow == null) continue;
            cleanOpenedTarget(overflow, baseTarget + "_" + index, itemKey, source.getUUID(), null,
                    new OverflowContext(source, batch), false);
            normalizeOverflowTarget(overflow, itemKey, source.getUUID(), null);
            int slot = firstEmptyMainSlot(overflow);
            if (slot < 0) continue;
            overflow.setMain(slot, box.copyWithCount(1));
            return overflow.save() ? 1 : 0;
        }
        return 0;
    }

    private static int firstEmptyMainSlot(TargetInventory inventory) {
        for (int i = 0; i < MAIN_SIZE; i++) if (inventory.main(i).isEmpty()) return i;
        return -1;
    }

    private static String depotName() {
        return "chinese".equals(FGASettings.fakePlayerItemSortTargetLanguage) ? "\u6f5c\u5f71\u76d2\u8865\u8d27" : "box_restock";
    }

    private static ItemStack takeEmptyShulkerFromDepot(UUID sourceId, UUID initiator) {
        MinecraftServer minecraftServer = server;
        if (minecraftServer == null) return ItemStack.EMPTY;
        ItemStack carried = takeEmptyShulkerFromSource(sourceId);
        if (!carried.isEmpty()) return carried;
        if (FGASettings.fakePlayerItemSortShulkerRestock) restockDepot(sourceId, initiator);
        try {
            ManagedInventory depot = openDepot(minecraftServer);
            if (depot == null) {
                notice(sourceId, initiator, "box depot is occupied by a real player: " + depotName());
                return ItemStack.EMPTY;
            }
            for (int slot = 0; slot < MAIN_SIZE; slot++) {
                ItemStack stack = depot.inventory().main(slot);
                if (!isOrdinaryEmptyShulker(stack)) continue;
                depot.inventory().setMain(slot, ItemStack.EMPTY);
                if (depot.inventory().save()) {
                    if (FGASettings.fakePlayerItemSortShulkerRestock) restockDepot(sourceId, initiator);
                    return stack.copyWithCount(1);
                }
                return ItemStack.EMPTY;
            }
            return ItemStack.EMPTY;
        } catch (IOException e) {
            notice(sourceId, initiator, "box depot read failed: " + e.getMessage());
            return ItemStack.EMPTY;
        } finally { finishDepotUse(minecraftServer); }
    }

    private static ItemStack takeEmptyShulkerFromSource(UUID sourceId) {
        MinecraftServer minecraftServer = server;
        if (minecraftServer == null || sourceId == null) return ItemStack.EMPTY;
        ServerPlayer source = minecraftServer.getPlayerList().getPlayer(sourceId);
        if (source == null) return ItemStack.EMPTY;
        for (int slot = 0; slot < SOURCE_SLOT_COUNT; slot++) {
            ItemStack stack = sourceStack(source.getInventory(), slot);
            if (!isOrdinaryEmptyShulker(stack)) continue;
            setSourceStack(source.getInventory(), slot, ItemStack.EMPTY);
            source.getInventory().setChanged();
            return stack.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    private static ManagedInventory openDepot(MinecraftServer minecraftServer) throws IOException {
        String name = depotName();
        ServerPlayer online = minecraftServer.getPlayerList().getPlayerByName(name);
        if (online != null) return online instanceof EntityPlayerMPFake ? new ManagedInventory(name, new OnlineInventory(online)) : null;
        return new ManagedInventory(name, OfflineInventory.open(minecraftServer, name));
    }

    private static int countDepotBoxes(TargetInventory depot) {
        int count = 0;
        for (int slot = 0; slot < MAIN_SIZE; slot++) if (isOrdinaryEmptyShulker(depot.main(slot))) count++;
        return count;
    }

    private static void legacyRestockPlaceholder(MinecraftServer minecraftServer) {
        restockDepot(null, null);
    }

    private static int restockDepot(UUID sourceId, UUID initiator) {
        MinecraftServer minecraftServer = server;
        if (minecraftServer == null) return 0;
        long now = System.currentTimeMillis();
        if (now < nextDepotRestockAttemptMs) return 0;
        boolean spawnedForRestock = minecraftServer.getPlayerList().getPlayerByName(depotName()) == null;
        try {
            ManagedInventory depot = openOnlineDepotForCraft(minecraftServer, sourceId, initiator);
            if (depot == null) {
                nextDepotRestockAttemptMs = now + RESTOCK_RETRY_MS;
                notice(sourceId, initiator, "box restock failed: depot is occupied by a real player");
                return 0;
            }
            int current = countDepotBoxes(depot.inventory());
            if (current >= DEPOT_RESTOCK_AT) return 0;
            int slots = 0;
            for (int slot = 0; slot < MAIN_SIZE; slot++) if (depot.inventory().main(slot).isEmpty()) slots++;
            int wanted = Math.min(DEPOT_TARGET_BOXES - current, slots);
            if (wanted <= 0) return 0;

            List<MaterialSource> logs = materialSources(minecraftServer, true);
            List<MaterialSource> shells = materialSources(minecraftServer, false);
            if (countDepotMaterial(depot.inventory(), true) < DEPOT_MATERIAL_RESTOCK_AT) {
                transferMaterialsToDepot(depot.inventory(), logs, true, DEPOT_MATERIAL_TARGET);
            }
            if (countDepotMaterial(depot.inventory(), false) < DEPOT_MATERIAL_RESTOCK_AT) {
                transferMaterialsToDepot(depot.inventory(), shells, false, DEPOT_MATERIAL_TARGET);
            }
            if (!ensureDepotWorkbench(depot.inventory())) {
                nextDepotRestockAttemptMs = now + RESTOCK_RETRY_MS;
                notice(sourceId, initiator, "box restock failed: no slot available for depot crafting table");
                return 0;
            }
            int craftable = Math.min(wanted, Math.min(countDepotMaterial(depot.inventory(), true) / 2,
                    countDepotMaterial(depot.inventory(), false) / 2));
            if (craftable <= 0) {
                nextDepotRestockAttemptMs = now + RESTOCK_RETRY_MS;
                notice(sourceId, initiator, "box restock failed: need 2 logs and 2 shulker shells per box");
                return 0;
            }
            consumeDepotMaterials(depot.inventory(), true, craftable * 2);
            consumeDepotMaterials(depot.inventory(), false, craftable * 2);
            Set<TargetInventory> changed = Collections.newSetFromMap(new IdentityHashMap<>());
            for (MaterialSource source : logs) changed.add(source.inventory());
            for (MaterialSource source : shells) changed.add(source.inventory());
            for (TargetInventory inventory : changed) {
                if (!inventory.save()) {
                    nextDepotRestockAttemptMs = now + RESTOCK_RETRY_MS;
                    notice(sourceId, initiator, "box restock failed while saving materials");
                    return 0;
                }
            }
            int placed = 0;
            for (int slot = 0; slot < MAIN_SIZE && placed < craftable; slot++) {
                if (depot.inventory().main(slot).isEmpty()) {
                    depot.inventory().setMain(slot, new ItemStack(Items.SHULKER_BOX));
                    placed++;
                }
            }
            if (!depot.inventory().save()) {
                nextDepotRestockAttemptMs = now + RESTOCK_RETRY_MS;
                notice(sourceId, initiator, "box restock failed while saving depot");
                return 0;
            }
            nextDepotRestockAttemptMs = 0L;
            notice(sourceId, initiator, "box restock crafted " + placed + " plain shulker boxes");
            return placed;
        } catch (IOException e) {
            nextDepotRestockAttemptMs = now + RESTOCK_RETRY_MS;
            notice(sourceId, initiator, "box restock failed: " + e.getMessage());
            return 0;
        } finally {
            if (spawnedForRestock) finishDepotUse(minecraftServer);
        }
    }

    private static boolean isDepotCleanupSlot(int slot) {
        return slot < MAIN_SIZE || slot == SOURCE_OFFHAND_SLOT;
    }

    private static boolean isDepotAllowed(ItemStack stack) {
        return stack.isEmpty() || stack.is(Items.CRAFTING_TABLE) || isLog(stack)
                || stack.is(Items.SHULKER_SHELL) || isOrdinaryEmptyShulker(stack);
    }

    /** The depot is a temporary worker: sort its foreign inventory, then always log it out. */
    private static void finishDepotUse(MinecraftServer minecraftServer) {
        ServerPlayer depot = minecraftServer.getPlayerList().getPlayerByName(depotName());
        if (!(depot instanceof EntityPlayerMPFake)) return;
        if (!AUTO_SPAWNED_DEPOTS.contains(depot.getUUID())) return;
        if (JOBS.containsKey(depot.getUUID())) return;
        if (hasDepotForeignItems(depot)) {
            JOBS.put(depot.getUUID(), new Job(depot.getUUID(), false, null, true));
        } else {
            closeDepotFake(minecraftServer, depot);
        }
    }

    private static boolean hasDepotForeignItems(ServerPlayer depot) {
        for (int slot = 0; slot < MAIN_SIZE; slot++) if (!isDepotAllowed(depot.getInventory().getItem(slot))) return true;
        return !isDepotAllowed(depot.getInventory().offhand.get(0));
    }

    private static void closeDepotFake(MinecraftServer minecraftServer, ServerPlayer depot) {
        if (depot instanceof EntityPlayerMPFake && depot.getScoreboardName().equals(depotName())) {
            AUTO_SPAWNED_DEPOTS.remove(depot.getUUID());
            depot.kill();
        }
    }

    private static int countDepotMaterial(TargetInventory inventory, boolean logs) {
        int total = 0;
        for (int slot = 0; slot < MAIN_SIZE; slot++) {
            ItemStack stack = inventory.main(slot);
            if (!stack.isEmpty() && (logs ? isLog(stack) : stack.is(Items.SHULKER_SHELL))) total += stack.getCount();
        }
        return total;
    }

    private static void transferMaterialsToDepot(TargetInventory depot, List<MaterialSource> sources, boolean logs, int target) {
        int needed = target - countDepotMaterial(depot, logs);
        for (MaterialSource source : sources) {
            for (int slot = 0; slot < MAIN_SIZE && needed > 0; slot++) {
                ItemStack stack = source.inventory().main(slot);
                if (stack.isEmpty() || !(logs ? isLog(stack) : stack.is(Items.SHULKER_SHELL))) continue;
                int moved = putDepotHotbarMaterial(depot, stack, needed);
                if (moved <= 0) return;
                stack.shrink(moved);
                if (stack.isEmpty()) source.inventory().setMain(slot, ItemStack.EMPTY);
                needed -= moved;
            }
            if (needed <= 0) return;
        }
    }

    private static int putDepotHotbarMaterial(TargetInventory depot, ItemStack source, int count) {
        int left = count;
        for (int slot = 1; slot <= PRIMARY_HOTBAR_END && left > 0; slot++) {
            ItemStack destination = depot.main(slot);
            if (!destination.isEmpty() && ItemStack.isSameItemSameComponents(destination, source)
                    && destination.getCount() < destination.getMaxStackSize()) {
                int moved = Math.min(left, destination.getMaxStackSize() - destination.getCount());
                destination.grow(moved);
                left -= moved;
            }
        }
        for (int slot = 1; slot <= PRIMARY_HOTBAR_END && left > 0; slot++) {
            if (depot.main(slot).isEmpty()) {
                int moved = Math.min(left, source.getMaxStackSize());
                depot.setMain(slot, FGACompat.copyWithCount(source, moved));
                left -= moved;
            }
        }
        return count - left;
    }

    private static void consumeDepotMaterials(TargetInventory depot, boolean logs, int amount) {
        int left = amount;
        for (int slot = 0; slot < MAIN_SIZE && left > 0; slot++) {
            ItemStack stack = depot.main(slot);
            if (stack.isEmpty() || !(logs ? isLog(stack) : stack.is(Items.SHULKER_SHELL))) continue;
            int used = Math.min(left, stack.getCount());
            stack.shrink(used);
            if (stack.isEmpty()) depot.setMain(slot, ItemStack.EMPTY);
            left -= used;
        }
    }

    private static boolean ensureDepotWorkbench(TargetInventory depot) {
        for (int slot = 0; slot <= PRIMARY_HOTBAR_END; slot++) {
            if (depot.main(slot).is(Items.CRAFTING_TABLE)) return true;
        }
        for (int slot = PRIMARY_LOOSE_START; slot < MAIN_SIZE; slot++) {
            if (depot.main(slot).is(Items.CRAFTING_TABLE)) {
                ItemStack table = depot.main(slot);
                depot.setMain(slot, depot.main(0));
                depot.setMain(0, table);
                return true;
            }
        }
        // The depot uses its internal crafting-table slot; logs are replenished before this call.
        ItemStack displaced = depot.main(0);
        if (!displaced.isEmpty()) {
            boolean moved = false;
            for (int slot = PRIMARY_LOOSE_START; slot < MAIN_SIZE; slot++) {
                if (depot.main(slot).isEmpty()) { depot.setMain(slot, displaced); moved = true; break; }
            }
            if (!moved) return false;
        }
        depot.setMain(0, new ItemStack(Items.CRAFTING_TABLE));
        return true;
    }

    private static boolean isLog(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        return path.endsWith("_log") || path.endsWith("_stem") || path.endsWith("_hyphae");
    }

    private static ManagedInventory openOnlineDepotForCraft(MinecraftServer minecraftServer, UUID sourceId, UUID initiator) throws IOException {
        String name = depotName();
        ServerPlayer online = minecraftServer.getPlayerList().getPlayerByName(name);
        if (online != null) return online instanceof EntityPlayerMPFake ? new ManagedInventory(name, new OnlineInventory(online)) : null;
        ServerPlayer reference = sourceId == null ? null : minecraftServer.getPlayerList().getPlayer(sourceId);
        if (reference == null && initiator != null) reference = minecraftServer.getPlayerList().getPlayer(initiator);
        if (reference != null) {
            if (EntityPlayerMPFake.createFake(name, minecraftServer, reference.position(), reference.getYRot(), reference.getXRot(),
                    reference.serverLevel().dimension(), GameType.SURVIVAL, false)) {
                ServerPlayer spawned = minecraftServer.getPlayerList().getPlayerByName(name);
                if (spawned instanceof EntityPlayerMPFake) {
                    AUTO_SPAWNED_DEPOTS.add(spawned.getUUID());
                    return new ManagedInventory(name, new OnlineInventory(spawned));
                }
            }
        }
        return new ManagedInventory(name, OfflineInventory.open(minecraftServer, name));
    }

    private static void addOwnMaterialSources(TargetInventory inventory, List<MaterialSource> result, boolean logs) {
        Set<String> keys = new LinkedHashSet<>();
        for (int slot = 0; slot < MAIN_SIZE; slot++) {
            ItemStack stack = inventory.main(slot);
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String path = id.getPath();
            boolean matches = logs ? path.endsWith("_log") || path.endsWith("_stem") || path.endsWith("_hyphae")
                    : stack.is(Items.SHULKER_SHELL);
            if (matches && keys.add(itemKey(stack))) result.add(new MaterialSource(inventory, itemKey(stack)));
        }
    }

    private static List<MaterialSource> materialSources(MinecraftServer minecraftServer, boolean logs) throws IOException {
        Map<String, OfflineInventory> inventories = new LinkedHashMap<>();
        List<MaterialSource> result = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            String path = id.getPath();
            boolean matches = logs ? path.endsWith("_log") || path.endsWith("_stem") || path.endsWith("_hyphae")
                    : id.equals(BuiltInRegistries.ITEM.getKey(Items.SHULKER_SHELL));
            if (!matches) continue;
            ItemStack material = new ItemStack(BuiltInRegistries.ITEM.get(id));
            String name = route(material);
            if (minecraftServer.getPlayerList().getPlayerByName(name) != null) continue;
            OfflineInventory inventory = inventories.get(name);
            if (inventory == null) {
                inventory = OfflineInventory.openExisting(minecraftServer, name);
                if (inventory == null) continue;
                inventories.put(name, inventory);
            }
            result.add(new MaterialSource(inventory, itemKey(material)));
        }
        return result;
    }

    private static int available(List<MaterialSource> sources) {
        int total = 0;
        for (MaterialSource source : sources) {
            for (int slot = 0; slot < MAIN_SIZE; slot++) {
                ItemStack stack = source.inventory().main(slot);
                if (!stack.isEmpty() && itemKey(stack).equals(source.itemKey())) total += stack.getCount();
            }
        }
        return total;
    }

    private static void consume(List<MaterialSource> sources, int amount) {
        int left = amount;
        for (MaterialSource source : sources) {
            for (int slot = 0; slot < MAIN_SIZE && left > 0; slot++) {
                ItemStack stack = source.inventory().main(slot);
                if (stack.isEmpty() || !itemKey(stack).equals(source.itemKey())) continue;
                int used = Math.min(left, stack.getCount());
                stack.shrink(used);
                if (stack.isEmpty()) source.inventory().setMain(slot, ItemStack.EMPTY);
                left -= used;
            }
            if (left == 0) return;
        }
    }

    private static void notice(UUID sourceId, UUID initiator, String message) {
        lastError = message;
        long now = System.currentTimeMillis();
        String key = message + "|" + sourceId + "|" + initiator;
        Long previous = NOTICE_TIMES.get(key);
        if (previous != null && now - previous < NOTICE_THROTTLE_MS) return;
        NOTICE_TIMES.put(key, now);
        if (NOTICE_TIMES.size() > 128) {
            NOTICE_TIMES.entrySet().removeIf(entry -> now - entry.getValue() > NOTICE_THROTTLE_MS * 6);
        }
        MinecraftServer minecraftServer = server;
        if (minecraftServer == null) return;
        Set<UUID> recipients = new LinkedHashSet<>();
        if (sourceId != null) recipients.add(sourceId);
        if (initiator != null) recipients.add(initiator);
        for (UUID recipient : recipients) {
            ServerPlayer player = minecraftServer.getPlayerList().getPlayer(recipient);
            if (player != null) player.sendSystemMessage(Component.literal(message));
        }
    }

    private static void finishAutoSpawnedBatchIfComplete(ServerPlayer source, String batch, String itemKey) {
        Set<String> spawned = AUTO_SPAWNED_BY_BATCH.get(batch);
        if (spawned == null || spawned.isEmpty()) return;
        if (hasSourceItem(source, itemKey) || READY.stream().anyMatch(move -> move.batch().equals(batch))) return;
        MinecraftServer minecraftServer = server;
        if (minecraftServer == null) return;
        for (String name : spawned) {
            ServerPlayer player = minecraftServer.getPlayerList().getPlayerByName(name);
            if (player instanceof EntityPlayerMPFake fake) {
                fake.kill(net.minecraft.network.chat.Component.literal("FGA item sorting batch completed"));
            }
        }
        AUTO_SPAWNED_BY_BATCH.remove(batch);
    }

    private static boolean hasSourceItem(ServerPlayer source, String itemKey) {
        for (int i = 0; i < SOURCE_SLOT_COUNT; i++) {
            ItemStack stack = sourceStack(source.getInventory(), i);
            if (itemKey(stack).equals(itemKey) || shulkerContainsItem(stack, itemKey)) return true;
        }
        return false;
    }

    private static boolean shulkerContainsItem(ItemStack stack, String itemKey) {
        if (!isShulkerBox(stack)) return false;
        for (ItemStack content : shulkerContents(stack)) {
            if (!content.isEmpty() && itemKey(content).equals(itemKey)) return true;
        }
        return false;
    }

    private static void persistCache() {
        if (cachePath == null) return;
        try {
            Files.createDirectories(cachePath.getParent());
            Path temp = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(ROUTES), StandardCharsets.UTF_8);
            try {
                Files.move(temp, cachePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, cachePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            lastError = e.getMessage();
        }
    }

    private static Map<String, String> loadChineseTranslations() {
        try (InputStream input = FakePlayerItemSortManager.class.getClassLoader()
                .getResourceAsStream("assets/carpet-fga-addition/lang/minecraft-1.21.1-zh_cn.json")) {
            if (input == null) return Map.of();
            JsonObject object = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) result.put(entry.getKey(), entry.getValue().getAsString());
            return Map.copyOf(result);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static void refreshDashboardSnapshot(MinecraftServer minecraftServer) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<Map.Entry<String, String>> routes = new ArrayList<>(ROUTES.entrySet());
        routes.sort(Map.Entry.comparingByValue());
        for (Map.Entry<String, String> route : routes) {
            long count = 0;
            int lastIndex = -1;
            for (int index = 0; index < 1000; index++) {
                String name = index == 0 ? route.getValue() : route.getValue() + "_" + index;
                TargetInventory inventory;
                ServerPlayer online = minecraftServer.getPlayerList().getPlayerByName(name);
                try {
                    if (online != null) {
                        if (!(online instanceof EntityPlayerMPFake)) break;
                        inventory = new OnlineInventory(online);
                    } else {
                        OfflineInventory offline = OfflineInventory.openExisting(minecraftServer, name);
                        if (offline == null) break;
                        inventory = offline;
                    }
                } catch (IOException ignored) { break; }
                lastIndex = index;
                for (int slot = 0; slot < MAIN_SIZE; slot++) count += countDashboardStack(inventory.main(slot), route.getKey());
                count += countDashboardStack(inventory.offhand(), route.getKey());
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("itemKey", route.getKey());
            entry.put("target", route.getValue());
            ItemStack displayStack = stackForKey(route.getKey());
            Map<String, String> names = new LinkedHashMap<>();
            names.put("server", serverDisplayName(route.getKey()));
            names.put("chinese", displayStack.isEmpty() ? cleanItemKey(route.getKey()) : cleanDisplayName(displayStack, "chinese"));
            names.put("english", displayStack.isEmpty() ? cleanItemKey(route.getKey()) : cleanDisplayName(displayStack, "english"));
            String custom = displayStack.isEmpty() ? null : FakePlayerItemSortConfig.snapshot().names()
                    .get(BuiltInRegistries.ITEM.getKey(displayStack.getItem()).toString());
            names.put("custom", custom == null ? names.get("server") : custom);
            entry.put("names", names);
            entry.put("count", count);
            entry.put("lastIndex", lastIndex);
            items.add(entry);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("jobs", JOBS.size()); root.put("queued", READY.size()); root.put("rebuildQueued", REBUILD_QUEUE.size()); root.put("cacheEntries", ROUTES.size());
        root.put("hits", HIT.get()); root.put("misses", MISS.get()); root.put("lastError", lastError); root.put("items", items);
        dashboardSnapshot = GSON.toJson(root);
        dashboardDirty = false;
    }

    private static long countDashboardStack(ItemStack stack, String itemKey) {
        if (stack.isEmpty()) return 0;
        if (itemKey(stack).equals(itemKey)) return stack.getCount();
        if (!isShulkerBox(stack)) return 0;
        long count = 0;
        for (ItemStack content : shulkerContents(stack)) if (!content.isEmpty() && itemKey(content).equals(itemKey)) count += content.getCount();
        return count;
    }

    public static String dashboardJson() {
        return dashboardSnapshot;
    }

    public static String status() {
        return "jobs=" + JOBS.size() + ", queue=" + READY.size() + ", cache=" + ROUTES.size()
                + ", hits=" + HIT.get() + ", misses=" + MISS.get();
    }

    private static long sorterMoveIntervalTicks() {
        return switch (FGASettings.fakePlayerItemSortSpeed) {
            case "4" -> 4L;
            case "16" -> 16L;
            default -> 8L;
        };
    }

    private interface TargetInventory {
        ItemStack main(int slot);
        void setMain(int slot, ItemStack stack);
        ItemStack offhand();
        void setOffhand(ItemStack stack);
        boolean save();
    }

    private static final class OnlineInventory implements TargetInventory {
        private final ServerPlayer player;

        private OnlineInventory(ServerPlayer player) {
            this.player = player;
        }

        private ServerPlayer player() {
            return player;
        }

        @Override
        public ItemStack main(int slot) {
            return player.getInventory().getItem(slot);
        }

        @Override
        public void setMain(int slot, ItemStack stack) {
            player.getInventory().setItem(slot, stack);
        }

        @Override
        public ItemStack offhand() {
            return player.getInventory().offhand.get(0);
        }

        @Override
        public void setOffhand(ItemStack stack) {
            player.getInventory().offhand.set(0, stack);
        }

        @Override
        public boolean save() {
            player.getInventory().setChanged();
            return true;
        }
    }

    private static final class OfflineInventory implements TargetInventory {
        private final MinecraftServer server;
        private final Path path;
        private final CompoundTag data;
        private final ItemStack[] main = new ItemStack[MAIN_SIZE];
        private ItemStack offhand = ItemStack.EMPTY;
        private final boolean existing;

        private OfflineInventory(MinecraftServer server, Path path, CompoundTag data, boolean existing) {
            this.server = server;
            this.path = path;
            this.data = data;
            this.existing = existing;
            Arrays.fill(main, ItemStack.EMPTY);
            readInventory();
        }

        static OfflineInventory open(MinecraftServer server, String name) throws IOException {
            UUID uuid = uuidFor(server, name);
            Path path = server.getWorldPath(LevelResource.ROOT).resolve("playerdata").resolve(uuid + ".dat");
            if (Files.exists(path)) return new OfflineInventory(server, path, NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()), true);
            CompoundTag data = new CompoundTag();
            data.put("Inventory", new ListTag());
            data.putString("fgaOfflineSorterName", name);
            return new OfflineInventory(server, path, data, false);
        }

        static OfflineInventory openExisting(MinecraftServer server, String name) throws IOException {
            UUID uuid = uuidFor(server, name);
            Path path = server.getWorldPath(LevelResource.ROOT).resolve("playerdata").resolve(uuid + ".dat");
            if (!Files.exists(path)) return null;
            return new OfflineInventory(server, path, NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()), true);
        }

        private Path path() { return path; }

        private static UUID uuidFor(MinecraftServer server, String name) {
            ServerPlayer online = server.getPlayerList().getPlayerByName(name);
            if (online != null) {
                LOCAL_PROFILE_UUIDS.put(name.toLowerCase(Locale.ROOT), online.getUUID());
                return online.getUUID();
            }
            UUID cached = LOCAL_PROFILE_UUIDS.get(name.toLowerCase(Locale.ROOT));
            if (cached != null) return cached;
            return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        }

        private void readInventory() {
            HolderLookup.Provider registry = server.registryAccess();
            ListTag inventory = data.getList("Inventory", Tag.TAG_COMPOUND);
            for (int i = 0; i < inventory.size(); i++) {
                CompoundTag entry = inventory.getCompound(i);
                int slot = entry.getByte("Slot");
                ItemStack stack = ItemStack.parseOptional(registry, entry);
                if (stack.isEmpty()) continue;
                if (slot >= 0 && slot < MAIN_SIZE) main[slot] = stack;
                else if (slot == OFFHAND_NBT_SLOT) offhand = stack;
            }
        }

        @Override
        public ItemStack main(int slot) {
            return main[slot];
        }

        @Override
        public void setMain(int slot, ItemStack stack) {
            main[slot] = stack;
        }

        @Override
        public ItemStack offhand() {
            return offhand;
        }

        @Override
        public void setOffhand(ItemStack stack) {
            offhand = stack;
        }

        @Override
        public boolean save() {
            try {
                HolderLookup.Provider registry = server.registryAccess();
                ListTag kept = new ListTag();
                ListTag original = data.getList("Inventory", Tag.TAG_COMPOUND);
                for (int i = 0; i < original.size(); i++) {
                    CompoundTag entry = original.getCompound(i);
                    int slot = entry.getByte("Slot");
                    if ((slot >= 0 && slot < MAIN_SIZE) || slot == OFFHAND_NBT_SLOT) continue;
                    kept.add(entry.copy());
                }
                for (int slot = 0; slot < MAIN_SIZE; slot++) addStack(kept, registry, slot, main[slot]);
                addStack(kept, registry, OFFHAND_NBT_SLOT, offhand);
                data.put("Inventory", kept);

                Files.createDirectories(path.getParent());
                Path temp = path.resolveSibling(path.getFileName() + ".tmp");
                NbtIo.writeCompressed(data, temp);
                try {
                    Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } catch (IOException | RuntimeException e) {
                lastError = "offline playerdata save failed" + (existing ? "" : " for new target") + ": " + e.getMessage();
                return false;
            }
        }

        private static void addStack(ListTag list, HolderLookup.Provider registry, int slot, ItemStack stack) {
            if (stack.isEmpty()) return;
            CompoundTag saved = (CompoundTag) stack.save(registry);
            saved.putByte("Slot", (byte) slot);
            list.add(saved);
        }
    }

    private record SourceMove(String sourceKey, String itemKey, ItemStack routeStack, MoveKind kind) {}

    private record OverflowContext(ServerPlayer source, String batch) {}

    private record ManagedInventory(String name, TargetInventory inventory) {}

    private record MaterialSource(TargetInventory inventory, String itemKey) {}

    private record PlannedMove(UUID source, UUID initiator, int slot, String target, String sourceKey, String itemKey,
                               String batch, MoveKind kind) {}

    private record RebuildRequest(String itemKey, String target, UUID source, UUID initiator, boolean all) {}

    private static final class Job {
        private final UUID player;
        private final boolean continuous;
        private final UUID initiator;
        private final boolean depotCleanup;
        private volatile boolean pending;
        private volatile long pauseUntilTick;

        private Job(UUID player, boolean continuous, UUID initiator) {
            this(player, continuous, initiator, false);
        }

        private Job(UUID player, boolean continuous, UUID initiator, boolean depotCleanup) {
            this.player = player;
            this.continuous = continuous;
            this.initiator = initiator;
            this.depotCleanup = depotCleanup;
        }

        UUID player() { return player; }
        boolean continuous() { return continuous; }
        UUID initiator() { return initiator; }
        boolean depotCleanup() { return depotCleanup; }
        boolean pending() { return pending; }
        void pending(boolean value) { pending = value; }
        boolean coolingDown(long tick) { return tick < pauseUntilTick; }
        void pauseUntil(long tick) { pauseUntilTick = Math.max(pauseUntilTick, tick); }
    }
}
//#endif
