//#if MC >= 1.20.5
package carpet.fga.inventoryadvancement;

import org.slf4j.LoggerFactory;
import carpet.fga.inventoryadvancement.index.PlayerIndex;
import carpet.fga.inventoryadvancement.index.InventoryTriggerEntry;
import carpet.fga.FGACompat;
import carpet.fga.inventoryadvancement.metrics.StatsCollector;
//#if MC < 26.2
import carpet.fga.mixin.SimpleCriterionTriggerAccessor;
//#endif
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
//#if MC >= 26.2
//$$ import net.minecraft.advancements.predicates.ContextAwarePredicate;
//$$ import net.minecraft.advancements.predicates.entity.EntityPredicate;
//$$ import net.minecraft.advancements.triggers.CriterionTrigger;
//#else
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
//#endif
//#if MC >= 26.2
//$$ import net.minecraft.advancements.triggers.InventoryChangeTrigger;
//#else
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
//#endif
//#if MC >= 26.2
//$$ import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
//#else
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
//#endif
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
//#if MC >= 26.2
//$$ import net.minecraft.server.PlayerAdvancements.TriggerInstanceKey;
//#endif
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import org.slf4j.Logger;

public final class InventoryAdvancementRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/inventory-advancement");
    private static final Set<String> CONFLICTING_MODS = Set.of("achiopt", "cerulean", "icterine");
    private static final int GLOBAL_MISMATCH_THRESHOLD = 3;
    private static final Verification MATCHED_VERIFICATION =
            new Verification(true, null, List.of(), List.of());

    private final Map<PlayerAdvancements, PlayerIndex> indexes = new IdentityHashMap<>();
    private final AtomicLong registryGeneration = new AtomicLong();
    private final AtomicInteger activePredicateScopes = new AtomicInteger();
    private final StatsCollector stats = new StatsCollector();
    private final ThreadLocal<ProcessingState> processing = ThreadLocal.withInitial(ProcessingState::new);

    private volatile OptimizationMode commandMode;
    private volatile boolean reloadInProgress;
    private volatile boolean replacementHealthy;
    private volatile boolean selfCheckCompleted;
    private volatile boolean indexMaintenanceSuspended;
    private volatile boolean listenerAccessFailureLogged;
    private volatile String disabledReason = "server_not_started";
    private int consecutiveMismatches;

    public void startupSelfCheck() {
        stats.setEnabled(InventoryAdvancementManager.metricsEnabled());
        List<String> conflicts = CONFLICTING_MODS.stream().filter(id -> net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id)).toList();
        if (!conflicts.isEmpty() || Boolean.getBoolean("invadvopt.mixin.conflict")) {
            replacementHealthy = false;
            selfCheckCompleted = true;
            disabledReason = "conflicting_mod:" + String.join(",", conflicts);
            suspendIndexMaintenance();
            LOGGER.error("[invadvopt] Trigger replacement refused because a conflicting advancement optimizer is loaded: {}. Vanilla behavior is retained.", conflicts);
            return;
        }

        List<String> missingHooks = new ArrayList<>();
        //#if MC >= 26.2
        for (String hook : List.of("trigger", "reload")) {
        //#else
        //$$ for (String hook : List.of("listener", "trigger", "predicate", "reload")) {
        //#endif
            if (!Boolean.getBoolean("invadvopt.mixin." + hook)) missingHooks.add(hook);
        }
        if (!verifyVanillaDescriptors()) missingHooks.add("vanilla_descriptors");
        if (!missingHooks.isEmpty()) {
            replacementHealthy = false;
            selfCheckCompleted = true;
            disabledReason = "mixin_self_check:" + String.join(",", missingHooks);
            suspendIndexMaintenance();
            LOGGER.error("[invadvopt] Mixin self-check failed ({}). Optimization is disabled; the original trigger remains active.", missingHooks);
            return;
        }
        replacementHealthy = true;
        selfCheckCompleted = true;
        disabledReason = "none";
        LOGGER.info("[invadvopt] EXACT inventory advancement acceleration is ready (registry generation {}).", registryGeneration.get());
    }

    private boolean verifyVanillaDescriptors() {
        try {
            Method trigger = InventoryChangeTrigger.class.getDeclaredMethod("trigger", ServerPlayer.class, Inventory.class, ItemStack.class);
            //#if MC >= 26.2
            Method activeTriggers = PlayerAdvancements.class.getDeclaredMethod("getTriggerMapForType", CriterionTrigger.class);
            return trigger.getReturnType() == void.class && java.util.Map.class.isAssignableFrom(activeTriggers.getReturnType());
            //#else
            //$$ Method add = SimpleCriterionTrigger.class.getDeclaredMethod("addPlayerListener", PlayerAdvancements.class, CriterionTrigger.Listener.class);
            //$$ Method remove = SimpleCriterionTrigger.class.getDeclaredMethod("removePlayerListener", PlayerAdvancements.class, CriterionTrigger.Listener.class);
            //$$ Method removeAll = SimpleCriterionTrigger.class.getDeclaredMethod("removePlayerListeners", PlayerAdvancements.class);
            //$$ return trigger.getReturnType() == void.class && add.getReturnType() == void.class
            //$$         && remove.getReturnType() == void.class && removeAll.getReturnType() == void.class;
            //#endif
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.error("[invadvopt] Vanilla method descriptor verification failed.", exception);
            return false;
        }
    }

    //#if MC < 26.2
    //$$ @SuppressWarnings("unchecked")
    public void addListener(InventoryChangeTrigger trigger, PlayerAdvancements advancements, CriterionTrigger.Listener<?> listener) {
        if (!shouldMaintainIndexes()) {
            suspendIndexMaintenance();
            return;
        }
        InventoryTriggerEntry typed = new InventoryTriggerEntry(
                (CriterionTrigger.Listener<InventoryChangeTrigger.TriggerInstance>) listener);
        synchronized (indexes) {
            if (resumeIndexMaintenance()) {
                rebuildIndex(trigger, advancements);
                return;
            }
            PlayerIndex.AddResult result = indexes.computeIfAbsent(advancements, ignored -> new PlayerIndex())
                    .add(typed, registryGeneration.get());
            if (result == PlayerIndex.AddResult.ADDED_UNSAFE_PLAN) {
                stats.recordIndexCondition("unsafe_plan");
            }
        }
    }

    //$$ @SuppressWarnings("unchecked")
    public void removeListener(InventoryChangeTrigger trigger, PlayerAdvancements advancements, CriterionTrigger.Listener<?> listener) {
        if (!shouldMaintainIndexes()) {
            suspendIndexMaintenance();
            return;
        }
        synchronized (indexes) {
            if (resumeIndexMaintenance()) {
                rebuildIndex(trigger, advancements);
                return;
            }
            PlayerIndex index = indexes.get(advancements);
            if (index == null) {
                rebuildIndex(trigger, advancements);
                return;
            }
            if (!index.remove(new InventoryTriggerEntry(
                    (CriterionTrigger.Listener<InventoryChangeTrigger.TriggerInstance>) listener))) {
                stats.recordIndexCondition("remove_miss");
            }
        }
    }
    //#endif

    public void removeListeners(PlayerAdvancements advancements) {
        if (!shouldMaintainIndexes()) {
            suspendIndexMaintenance();
            return;
        }
        synchronized (indexes) {
            indexes.remove(advancements);
        }
    }

    public boolean handleTrigger(InventoryChangeTrigger trigger, ServerPlayer player, Inventory inventory, ItemStack changedStack) {
        if (!shouldMaintainIndexes()) {
            suspendIndexMaintenance();
            return false;
        }

        ProcessingState state = processing.get();
        if (state.optimizing) {
            beginVanilla(state, player, changedStack, listenerCount(player), "reentrant_call");
            return false;
        }

        String unavailable = unavailableReason(player);
        if (unavailable != null) {
            beginVanilla(state, player, changedStack, listenerCount(player), unavailable);
            return false;
        }

        PlayerIndex index;
        synchronized (indexes) {
            resumeIndexMaintenance();
            index = indexes.get(player.getAdvancements());
            if (index == null) {
                index = rebuildIndex(trigger, player.getAdvancements());
            }
        }
        if (index == null) {
            beginVanilla(state, player, changedStack, 0, "index_missing");
            return false;
        }

        int rawCount = index.listenerCount();
        String indexFallback = index.fallbackReason();
        if (indexFallback != null) {
            beginVanilla(state, player, changedStack, rawCount, indexFallback);
            return false;
        }

        state.optimizing = true;
        long started = stats.enabled() ? System.nanoTime() : 0L;
        startPredicateScope(state);
        int candidateCount = rawCount;
        try {
            List<InventoryTriggerEntry> awards;
            long tick = FGACompat.server(player).getTickCount();
            synchronized (index) {
                PlayerIndex.Selection selection = index.select(inventory, changedStack, registryGeneration.get(), tick,
                        InventoryAdvancementManager.periodicFullScanTicks());
                rawCount = selection.allListeners().size();
                candidateCount = selection.candidates().size();
                double shadowVerifyRate = InventoryAdvancementManager.shadowVerifyRate();
                boolean sampleVerify = selection.verify() || shadowVerifyRate > 0.0D
                        && ThreadLocalRandom.current().nextDouble() < shadowVerifyRate;
                if (selection.mandatoryFull()) stats.recordFullScan();
                state.matchContext.reset(player);
                List<InventoryTriggerEntry> optimizedMatches =
                        evaluate(selection.candidates(), inventory, changedStack, selection.fullSlots(), selection.emptySlots(),
                                selection.occupiedSlots(), state.matchContext);
                awards = optimizedMatches;

                if (sampleVerify && !selection.mandatoryFull()) {
                    stats.recordFullScan();
                    Verification verification = verifyFull(selection, optimizedMatches, inventory, changedStack,
                            selection.fullSlots(), selection.emptySlots(), selection.occupiedSlots(), state.matchContext);
                    index.verified(tick);
                    if (!verification.matches()) {
                        awards = verification.fullMatches();
                        onMismatch(index, player, changedStack, selection, verification);
                    } else {
                        consecutiveMismatches = 0;
                    }
                } else if (sampleVerify) {
                    index.verified(tick);
                }
            }

            if (awards != null) {
                for (InventoryTriggerEntry listener : awards) {
                    listener.award(player.getAdvancements());
                }
            }
            stats.recordTrigger(player, changedStack, rawCount, candidateCount,
                    started == 0L ? 0L : System.nanoTime() - started);
            return true;
        } catch (Throwable throwable) {
            LOGGER.error("[invadvopt] Optimized trigger failed safely for player {} and item {}; retrying through vanilla.",
                    player.getUUID(), safeItemName(changedStack), throwable);
            return fallThroughFromOptimized(state, player, changedStack, rawCount, "optimization_exception", started);
        } finally {
            state.matchContext.clear();
            if (state.optimizing) {
                endPredicateScope(state);
                state.optimizing = false;
            }
        }
    }

    private List<InventoryTriggerEntry> evaluate(
            Iterable<InventoryTriggerEntry> source,
            Inventory inventory, ItemStack changedStack, int full, int empty, int occupied, LazyMatchContext context) {
        List<InventoryTriggerEntry> result = null;
        for (InventoryTriggerEntry listener : source) {
            if (matches(listener, inventory, changedStack, full, empty, occupied, context)) {
                if (result == null) result = new ArrayList<>();
                result.add(listener);
            }
        }
        return result;
    }

    private Verification verifyFull(
            PlayerIndex.Selection selection,
            List<InventoryTriggerEntry> optimizedMatches,
            Inventory inventory, ItemStack changedStack, int full, int empty, int occupied, LazyMatchContext context) {
        List<InventoryTriggerEntry> missing = null;
        for (InventoryTriggerEntry listener : selection.allListeners()) {
            if (selection.isCandidate(listener)) continue;
            if (matches(listener, inventory, changedStack, full, empty, occupied, context)) {
                if (missing == null) missing = new ArrayList<>();
                missing.add(listener);
            }
        }
        if (missing == null) return MATCHED_VERIFICATION;

        // Candidate listeners were matched by the original TriggerInstance above, so the
        // complete result is exactly optimizedMatches plus any matching omitted listener.
        // The former set-based implementation built four identity maps to derive the same
        // result and could never produce an "extra" entry because it seeded the full set
        // with every optimized match.
        int optimizedSize = optimizedMatches == null ? 0 : optimizedMatches.size();
        List<InventoryTriggerEntry> fullMatches =
                new ArrayList<>(optimizedSize + missing.size());
        if (optimizedMatches != null) fullMatches.addAll(optimizedMatches);
        fullMatches.addAll(missing);
        return new Verification(false, fullMatches, missing, List.of());
    }

    private static boolean matches(
            InventoryTriggerEntry listener,
            Inventory inventory, ItemStack changedStack, int full, int empty, int occupied, LazyMatchContext context) {
        InventoryChangeTrigger.TriggerInstance trigger = listener.trigger();
        if (!trigger.matches(inventory, changedStack, full, empty, occupied)) return false;
        Optional<ContextAwarePredicate> playerPredicate = trigger.player();
        return playerPredicate.isEmpty() || playerPredicate.get().matches(context.get());
    }

    private void onMismatch(PlayerIndex index, ServerPlayer player, ItemStack stack, PlayerIndex.Selection selection, Verification result) {
        stats.recordMismatch();
        consecutiveMismatches++;
        if (InventoryAdvancementManager.disableOnMismatch()) index.disable();
        logMismatch("missing", result.missing(), player, stack, selection);
        logMismatch("extra", result.extra(), player, stack, selection);
        if (consecutiveMismatches >= GLOBAL_MISMATCH_THRESHOLD) {
            commandMode = OptimizationMode.VANILLA;
            disabledReason = "global_mismatch_circuit_breaker";
            LOGGER.error("[invadvopt] {} consecutive verification mismatches; globally switching to VANILLA mode.", consecutiveMismatches);
        }
    }

    private void logMismatch(String type, Iterable<InventoryTriggerEntry> listeners,
            ServerPlayer player, ItemStack stack, PlayerIndex.Selection selection) {
        for (InventoryTriggerEntry listener : listeners) {
            LOGGER.error("[invadvopt] Shadow mismatch type={} advancement={} criterion={} player={} item={} listenerGeneration={} registryGeneration={}",
                    type, listener.advancementId(), listener.criterion(), player.getUUID(), safeItemName(stack),
                    selection.listenerGeneration(), selection.registryGeneration());
        }
    }

    private String unavailableReason(ServerPlayer player) {
        if (reloadInProgress) return "datapack_reload_in_progress";
        MinecraftServer server = FGACompat.server(player);
        if (server == null || !server.isSameThread()) return "off_thread_call";
        return null;
    }

    private boolean shouldMaintainIndexes() {
        return InventoryAdvancementManager.enabled()
                && mode() != OptimizationMode.VANILLA
                && (!selfCheckCompleted || replacementHealthy);
    }

    private void suspendIndexMaintenance() {
        if (indexMaintenanceSuspended) return;
        synchronized (indexes) {
            if (indexMaintenanceSuspended) return;
            indexes.clear();
            indexMaintenanceSuspended = true;
        }
    }

    /** Must be called while holding {@link #indexes}. */
    private boolean resumeIndexMaintenance() {
        if (!indexMaintenanceSuspended) return false;
        indexes.clear();
        indexMaintenanceSuspended = false;
        return true;
    }

    private PlayerIndex rebuildIndex(InventoryChangeTrigger trigger, PlayerAdvancements advancements) {
        try {
            PlayerIndex index = new PlayerIndex();
            int unsafePlans = index.replaceAll(currentListeners(trigger, advancements), registryGeneration.get());
            stats.recordIndexCondition("unsafe_plan", unsafePlans);
            indexes.put(advancements, index);
            listenerAccessFailureLogged = false;
            return index;
        } catch (RuntimeException | LinkageError exception) {
            if (!listenerAccessFailureLogged) {
                listenerAccessFailureLogged = true;
                LOGGER.error("[invadvopt] Could not rebuild the inventory advancement listener index; vanilla behavior is retained.", exception);
            }
            return null;
        }
    }

    private static List<InventoryTriggerEntry> currentListeners(
            InventoryChangeTrigger trigger, PlayerAdvancements advancements) {
        //#if MC >= 26.2
        //$$ Map<TriggerInstanceKey, InventoryChangeTrigger.TriggerInstance> source = advancements.getTriggerMapForType(trigger);
        //$$ if (source == null || source.isEmpty()) return List.of();
        //$$ List<InventoryTriggerEntry> result = new ArrayList<>(source.size());
        //$$ for (Map.Entry<TriggerInstanceKey, InventoryChangeTrigger.TriggerInstance> entry : source.entrySet()) {
        //$$     result.add(new InventoryTriggerEntry(entry.getKey().advancement(), entry.getKey().criterion(), entry.getValue()));
        //$$ }
        //$$ return result;
        //#else
        Set<CriterionTrigger.Listener<?>> source = ((SimpleCriterionTriggerAccessor)(Object)trigger)
                .invadvopt$getPlayers().get(advancements);
        if (source == null || source.isEmpty()) return List.of();
        List<InventoryTriggerEntry> result = new ArrayList<>(source.size());
        for (CriterionTrigger.Listener<?> listener : source) {
            result.add(new InventoryTriggerEntry((CriterionTrigger.Listener<InventoryChangeTrigger.TriggerInstance>) listener));
        }
        return result;
        //#endif
    }

    private boolean fallThroughFromOptimized(ProcessingState state, ServerPlayer player, ItemStack stack,
            int rawCount, String reason, long started) {
        endPredicateScope(state);
        state.optimizing = false;
        beginVanilla(state, player, stack, rawCount, reason, started);
        return false;
    }

    private void beginVanilla(ProcessingState state, ServerPlayer player, ItemStack stack, int rawCount, String reason) {
        beginVanilla(state, player, stack, rawCount, reason, stats.enabled() ? System.nanoTime() : 0L);
    }

    private void beginVanilla(ProcessingState state, ServerPlayer player, ItemStack stack, int rawCount, String reason, long started) {
        stats.recordFallback(reason);
        boolean trackPredicates = !"index_desynchronized".equals(reason);
        if (trackPredicates) startPredicateScope(state);
        state.vanillaFrames.push(new VanillaFrame(player, stack, rawCount, reason, started, trackPredicates));
    }

    public void onVanillaTriggerReturn() {
        ProcessingState state = processing.get();
        VanillaFrame frame = state.vanillaFrames.poll();
        if (frame == null) return;
        if (frame.trackPredicates()) endPredicateScope(state);
        stats.recordTrigger(frame.player(), frame.stack(), frame.rawListeners(), frame.rawListeners(),
                frame.started() == 0L ? 0L : System.nanoTime() - frame.started());
        if (state.vanillaFrames.isEmpty() && !state.optimizing) processing.remove();
    }

    public void onItemPredicateTest() {
        if (activePredicateScopes.get() <= 0) return;
        ProcessingState state = processing.get();
        if (state.predicateScopeDepth > 0) {
            state.predicateTests++;
        } else {
            processing.remove();
        }
    }

    private void startPredicateScope(ProcessingState state) {
        if (!stats.enabled()) return;
        if (state.predicateScopeDepth++ == 0) {
            state.predicateTests = 0L;
            activePredicateScopes.incrementAndGet();
        }
    }

    private void endPredicateScope(ProcessingState state) {
        if (state.predicateScopeDepth <= 0) return;
        if (--state.predicateScopeDepth == 0) {
            stats.recordPredicateTests(state.predicateTests);
            state.predicateTests = 0L;
            if (activePredicateScopes.decrementAndGet() < 0) activePredicateScopes.set(0);
        }
    }

    public void reloadStarted() {
        reloadInProgress = true;
    }

    public void reloadFuture(CompletableFuture<Void> future) {
        if (future == null) {
            reloadCompleted();
            return;
        }
        future.whenComplete((ignored, throwable) -> reloadCompleted());
    }

    public void tagsUpdated() {
        reloadCompleted();
    }

    private void reloadCompleted() {
        long generation = registryGeneration.incrementAndGet();
        if (shouldMaintainIndexes()) {
            synchronized (indexes) {
                for (PlayerIndex index : indexes.values()) index.markReload(generation);
            }
        } else {
            suspendIndexMaintenance();
        }
        reloadInProgress = false;
    }

    public void requestVerification() {
        synchronized (indexes) {
            for (PlayerIndex index : indexes.values()) index.requestVerification();
        }
    }

    public void resetPlayerCircuitBreakers() {
        synchronized (indexes) {
            indexes.clear();
            indexMaintenanceSuspended = true;
        }
        consecutiveMismatches = 0;
    }

    public void clear() {
        synchronized (indexes) { indexes.clear(); }
        reloadInProgress = false;
        activePredicateScopes.set(0);
        processing.remove();
    }

    public void setMode(OptimizationMode mode) {
        commandMode = mode;
        if (mode == OptimizationMode.VANILLA) suspendIndexMaintenance();
        if (mode != OptimizationMode.VANILLA && replacementHealthy) disabledReason = "none";
    }

    public OptimizationMode mode() {
        OptimizationMode override = commandMode;
        return override != null ? override : InventoryAdvancementManager.mode();
    }

    public String status() {
        return "mode=" + mode() + ", enabled=" + InventoryAdvancementManager.enabled() + ", replacementHealthy=" + replacementHealthy
                + ", reloadInProgress=" + reloadInProgress + ", registryGeneration=" + registryGeneration.get()
                + ", indexedPlayers=" + indexedPlayers() + ", indexMaintenanceSuspended=" + indexMaintenanceSuspended
                + ", disabledReason=" + disabledReason;
    }

    public int indexedPlayers() {
        synchronized (indexes) { return indexes.size(); }
    }

    public StatsCollector stats() {
        return stats;
    }

    private int listenerCount(ServerPlayer player) {
        synchronized (indexes) {
            PlayerIndex index = indexes.get(player.getAdvancements());
            return index == null ? 0 : index.listenerCount();
        }
    }

    private static String safeItemName(ItemStack stack) {
        return stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static final class ProcessingState {
        private boolean optimizing;
        private int predicateScopeDepth;
        private long predicateTests;
        private final LazyMatchContext matchContext = new LazyMatchContext();
        private final Deque<VanillaFrame> vanillaFrames = new ArrayDeque<>();
    }

    private static final class LazyMatchContext {
        private ServerPlayer player;
        private LootContext context;

        private void reset(ServerPlayer player) {
            this.player = player;
            context = null;
        }

        private LootContext get() {
            if (context == null) context = EntityPredicate.createContext(player, player);
            return context;
        }

        private void clear() {
            player = null;
            context = null;
        }
    }

    private record VanillaFrame(ServerPlayer player, ItemStack stack, int rawListeners, String reason, long started,
            boolean trackPredicates) {}

    private record Verification(boolean matches,
            List<InventoryTriggerEntry> fullMatches,
            List<InventoryTriggerEntry> missing,
            List<InventoryTriggerEntry> extra) {}
}
//#endif

