//#if MC >= 1.20.5
package carpet.fga.inventoryadvancement.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
//#if MC >= 26.2
//$$ import net.minecraft.advancements.triggers.InventoryChangeTrigger;
//#else
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
//#endif
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class PlayerIndex {
    private final IdentityHashMap<InventoryTriggerEntry, CompiledPlan> plans = new IdentityHashMap<>();
    private final Map<ListenerKey, InventoryTriggerEntry> canonicalListeners = new HashMap<>();
    private final IdentityHashMap<InventoryTriggerEntry, Integer> listenerPositions = new IdentityHashMap<>();
    private final List<InventoryTriggerEntry> listeners = new ArrayList<>();
    private final Map<Integer, Set<InventoryTriggerEntry>> byItem = new HashMap<>();
    private final Set<InventoryTriggerEntry> alwaysCheck = IdentitySet.create();
    private final Set<InventoryTriggerEntry> wildcard = IdentitySet.create();
    private final Set<InventoryTriggerEntry> slotSensitive = IdentitySet.create();
    private final Set<InventoryTriggerEntry> pendingCheck = IdentitySet.create();
    private final Set<InventoryTriggerEntry> candidateSet = IdentitySet.create();
    private final List<InventoryTriggerEntry> candidates = new ArrayList<>();
    private final InventorySnapshot snapshot = new InventorySnapshot();
    private final Selection selection = new Selection();

    private long listenerGeneration;
    private long compiledListenerGeneration;
    private long registryGeneration = -1L;
    private long lastVerifiedTick = Long.MIN_VALUE;
    private boolean disabled;
    private boolean forceVerification;

    public synchronized AddResult add(InventoryTriggerEntry listener, long currentRegistryGeneration) {
        if (registryGeneration != currentRegistryGeneration || compiledListenerGeneration != listenerGeneration) {
            rebuild(currentRegistryGeneration);
            snapshot.invalidate();
        }
        ListenerKey key = key(listener);
        if (canonicalListeners.containsKey(key)) {
            return AddResult.DUPLICATE;
        }
        canonicalListeners.put(key, listener);
        listenerPositions.put(listener, listeners.size());
        listeners.add(listener);
        listenerGeneration++;
        CompiledPlan plan = PlanCompiler.compile(listener);
        plans.put(listener, plan);
        addToIndexes(plan);
        // A newly registered criterion may match an item which was already present. Only
        // that listener needs a first-event check; invalidating the player snapshot would
        // turn one registration into a complete scan of every advancement listener.
        pendingCheck.add(listener);
        compiledListenerGeneration = listenerGeneration;
        registryGeneration = currentRegistryGeneration;
        return plan.indexSafe() ? AddResult.ADDED : AddResult.ADDED_UNSAFE_PLAN;
    }

    public synchronized boolean remove(InventoryTriggerEntry listener) {
        InventoryTriggerEntry canonical = canonicalListeners.remove(key(listener));
        if (canonical == null) {
            return false;
        }
        CompiledPlan plan = plans.remove(canonical);
        removeListenerPosition(canonical);
        pendingCheck.remove(canonical);
        if (plan != null) {
            removeFromIndexes(plan);
        }
        listenerGeneration++;
        compiledListenerGeneration = listenerGeneration;
        return true;
    }

    public synchronized int replaceAll(
            Iterable<InventoryTriggerEntry> currentListeners,
            long currentRegistryGeneration) {
        clearIndexState();
        int unsafePlans = 0;
        for (InventoryTriggerEntry listener : currentListeners) {
            if (canonicalListeners.putIfAbsent(key(listener), listener) != null) continue;
            listenerPositions.put(listener, listeners.size());
            listeners.add(listener);
            CompiledPlan plan = PlanCompiler.compile(listener);
            plans.put(listener, plan);
            addToIndexes(plan);
            if (!plan.indexSafe()) unsafePlans++;
        }
        listenerGeneration++;
        compiledListenerGeneration = listenerGeneration;
        registryGeneration = currentRegistryGeneration;
        snapshot.invalidate();
        forceVerification = true;
        return unsafePlans;
    }

    public synchronized Selection select(
            Inventory inventory,
            ItemStack changedStack,
            long currentRegistryGeneration,
            long currentTick,
            int periodicTicks) {
        boolean rebuilt = false;
        if (registryGeneration != currentRegistryGeneration || compiledListenerGeneration != listenerGeneration) {
            rebuild(currentRegistryGeneration);
            snapshot.invalidate();
            rebuilt = true;
        }

        boolean firstSnapshot = snapshot.update(inventory);
        snapshot.includeChangedStack(changedStack);

        boolean periodic = periodicTicks > 0
                && (lastVerifiedTick == Long.MIN_VALUE || currentTick - lastVerifiedTick >= periodicTicks);
        boolean verify = forceVerification || periodic;
        forceVerification = false;

        candidateSet.clear();
        candidates.clear();
        boolean mandatoryFull = firstSnapshot || rebuilt;
        if (mandatoryFull) {
            // Do not populate candidateSet here. Growing its IdentityHashMap to the full
            // listener count would make every later clear() scan that peak-sized table,
            // even when steady-state selection contains only a handful of listeners.
            candidates.addAll(listeners);
        } else {
            addAll(pendingCheck);
            addAll(alwaysCheck);
            addAll(slotSensitive);
            addAll(wildcard);
            for (int index = 0; index < snapshot.changedRawIdCount(); index++) {
                int rawId = snapshot.changedRawIdAt(index);
                Set<InventoryTriggerEntry> indexed = byItem.get(rawId);
                if (indexed != null) {
                    addAll(indexed);
                }
            }
        }
        pendingCheck.clear();
        return selection.update(candidates, listeners, candidateSet, mandatoryFull, verify,
                listenerGeneration, registryGeneration, snapshot.fullSlots(), snapshot.emptySlots(), snapshot.occupiedSlots());
    }

    public synchronized void verified(long tick) {
        lastVerifiedTick = tick;
    }

    public synchronized void markReload(long newRegistryGeneration) {
        registryGeneration = newRegistryGeneration - 1L;
        snapshot.invalidate();
        forceVerification = true;
    }

    public synchronized void requestVerification() {
        forceVerification = true;
    }

    public synchronized void disable() {
        disabled = true;
    }

    public synchronized String fallbackReason() {
        return disabled ? "index_desynchronized" : null;
    }

    public synchronized int listenerCount() {
        return listeners.size();
    }

    private void rebuild(long currentRegistryGeneration) {
        plans.clear();
        byItem.clear();
        alwaysCheck.clear();
        wildcard.clear();
        slotSensitive.clear();
        for (InventoryTriggerEntry listener : listeners) {
            CompiledPlan plan = PlanCompiler.compile(listener);
            plans.put(listener, plan);
            addToIndexes(plan);
        }
        compiledListenerGeneration = listenerGeneration;
        registryGeneration = currentRegistryGeneration;
    }

    private void addToIndexes(CompiledPlan plan) {
        if (plan.alwaysCheck()) alwaysCheck.add(plan.listener());
        if (plan.wildcard()) wildcard.add(plan.listener());
        if (plan.slotSensitive()) slotSensitive.add(plan.listener());
        for (int rawId : plan.rawItemIds()) {
            byItem.computeIfAbsent(rawId, ignored -> IdentitySet.create()).add(plan.listener());
        }
    }

    private void removeFromIndexes(CompiledPlan plan) {
        alwaysCheck.remove(plan.listener());
        wildcard.remove(plan.listener());
        slotSensitive.remove(plan.listener());
        for (int rawId : plan.rawItemIds()) {
            Set<InventoryTriggerEntry> indexed = byItem.get(rawId);
            if (indexed != null) {
                indexed.remove(plan.listener());
                if (indexed.isEmpty()) byItem.remove(rawId);
            }
        }
    }

    private void clearIndexState() {
        plans.clear();
        canonicalListeners.clear();
        listenerPositions.clear();
        listeners.clear();
        byItem.clear();
        alwaysCheck.clear();
        wildcard.clear();
        slotSensitive.clear();
        pendingCheck.clear();
        candidateSet.clear();
        candidates.clear();
    }

    private void removeListenerPosition(
            InventoryTriggerEntry listener) {
        Integer position = listenerPositions.remove(listener);
        if (position == null) return;
        int lastPosition = listeners.size() - 1;
        InventoryTriggerEntry last = listeners.remove(lastPosition);
        if (position < lastPosition) {
            listeners.set(position, last);
            listenerPositions.put(last, position);
        }
    }

    private static ListenerKey key(
            InventoryTriggerEntry listener) {
        return new ListenerKey(listener.advancementId(), listener.criterion());
    }

    private void addAll(Iterable<InventoryTriggerEntry> source) {
        for (InventoryTriggerEntry listener : source) {
            if (candidateSet.add(listener)) {
                candidates.add(listener);
            }
        }
    }

    public static final class Selection {
        private List<InventoryTriggerEntry> candidates;
        private List<InventoryTriggerEntry> allListeners;
        private Set<InventoryTriggerEntry> candidateSet;
        private boolean mandatoryFull;
        private boolean verify;
        private long listenerGeneration;
        private long registryGeneration;
        private int fullSlots;
        private int emptySlots;
        private int occupiedSlots;

        private Selection update(
                List<InventoryTriggerEntry> candidates,
                List<InventoryTriggerEntry> allListeners,
                Set<InventoryTriggerEntry> candidateSet,
                boolean mandatoryFull,
                boolean verify,
                long listenerGeneration,
                long registryGeneration,
                int fullSlots,
                int emptySlots,
                int occupiedSlots) {
            this.candidates = candidates;
            this.allListeners = allListeners;
            this.candidateSet = candidateSet;
            this.mandatoryFull = mandatoryFull;
            this.verify = verify;
            this.listenerGeneration = listenerGeneration;
            this.registryGeneration = registryGeneration;
            this.fullSlots = fullSlots;
            this.emptySlots = emptySlots;
            this.occupiedSlots = occupiedSlots;
            return this;
        }

        public List<InventoryTriggerEntry> candidates() { return candidates; }
        public List<InventoryTriggerEntry> allListeners() { return allListeners; }
        public boolean mandatoryFull() { return mandatoryFull; }
        public boolean verify() { return verify; }
        public long listenerGeneration() { return listenerGeneration; }
        public long registryGeneration() { return registryGeneration; }
        public int fullSlots() { return fullSlots; }
        public int emptySlots() { return emptySlots; }
        public int occupiedSlots() { return occupiedSlots; }

        public boolean isCandidate(InventoryTriggerEntry listener) {
            return mandatoryFull || candidateSet.contains(listener);
        }
    }

    public enum AddResult {
        ADDED,
        ADDED_UNSAFE_PLAN,
        DUPLICATE
    }

    private record ListenerKey(ResourceLocation advancementId, String criterion) {}
}
//#endif

