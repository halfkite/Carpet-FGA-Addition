//#if MC >= 1.20.5
package carpet.fga.inventoryadvancement.metrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class StatsCollector {
    private static final int HISTOGRAM_BUCKETS = 64;
    private final LongAdder triggers = new LongAdder();
    private final LongAdder rawListeners = new LongAdder();
    private final LongAdder candidateListeners = new LongAdder();
    private final LongAdder predicateTests = new LongAdder();
    private final LongAdder fullScans = new LongAdder();
    private final LongAdder fallbacks = new LongAdder();
    private final LongAdder mismatches = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final AtomicLong maxNanos = new AtomicLong();
    private final LongAdder[] latencyBuckets = new LongAdder[HISTOGRAM_BUCKETS];
    private final Map<String, LongAdder> fallbackReasons = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> indexConditions = new ConcurrentHashMap<>();
    private final Map<UUID, LongAdder> playerHotspots = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, LongAdder> itemHotspots = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;

    public StatsCollector() {
        for (int i = 0; i < latencyBuckets.length; i++) latencyBuckets[i] = new LongAdder();
    }

    public void recordPredicateTests(long count) {
        if (enabled && count > 0L) predicateTests.add(count);
    }

    public void recordTrigger(ServerPlayer player, ItemStack changedStack, int raw, int candidates, long nanos) {
        if (!enabled) return;
        triggers.increment();
        rawListeners.add(raw);
        candidateListeners.add(candidates);
        totalNanos.add(nanos);
        maxNanos.accumulateAndGet(nanos, Math::max);
        latencyBuckets[bucket(nanos)].increment();
        playerHotspots.computeIfAbsent(player.getUUID(), ignored -> new LongAdder()).increment();
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(changedStack.isEmpty() ? Items.AIR : changedStack.getItem());
        itemHotspots.computeIfAbsent(item, ignored -> new LongAdder()).increment();
    }

    public void recordFullScan() {
        if (!enabled) return;
        fullScans.increment();
    }

    public void recordFallback(String reason) {
        if (!enabled) return;
        fallbacks.increment();
        fallbackReasons.computeIfAbsent(reason, ignored -> new LongAdder()).increment();
    }

    public void recordMismatch() {
        if (!enabled) return;
        mismatches.increment();
    }

    public void recordIndexCondition(String condition) {
        recordIndexCondition(condition, 1L);
    }

    public void recordIndexCondition(String condition, long count) {
        if (!enabled || count <= 0L) return;
        indexConditions.computeIfAbsent(condition, ignored -> new LongAdder()).add(count);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public Snapshot snapshot() {
        long triggerCount = triggers.sum();
        long raw = rawListeners.sum();
        long candidate = candidateListeners.sum();
        return new Snapshot(triggerCount, raw, candidate, predicateTests.sum(), fullScans.sum(), fallbacks.sum(), mismatches.sum(),
                totalNanos.sum(), maxNanos.get(), percentile95(triggerCount), copy(fallbackReasons), copy(indexConditions),
                top(playerHotspots, 10), top(itemHotspots, 10));
    }

    public void reset() {
        triggers.reset();
        rawListeners.reset();
        candidateListeners.reset();
        predicateTests.reset();
        fullScans.reset();
        fallbacks.reset();
        mismatches.reset();
        totalNanos.reset();
        maxNanos.set(0L);
        for (LongAdder bucket : latencyBuckets) bucket.reset();
        fallbackReasons.clear();
        indexConditions.clear();
        playerHotspots.clear();
        itemHotspots.clear();
    }

    private long percentile95(long count) {
        if (count == 0) return 0L;
        long target = Math.max(1L, (long)Math.ceil(count * 0.95D));
        long seen = 0L;
        for (int i = 0; i < latencyBuckets.length; i++) {
            seen += latencyBuckets[i].sum();
            if (seen >= target) return i == 0 ? 1L : 1L << Math.min(i, 62);
        }
        return maxNanos.get();
    }

    private static int bucket(long nanos) {
        if (nanos <= 1L) return 0;
        return Math.min(HISTOGRAM_BUCKETS - 1, 64 - Long.numberOfLeadingZeros(nanos - 1L));
    }

    private static <K> Map<String, Long> copy(Map<K, LongAdder> source) {
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value.sum()));
        return Map.copyOf(result);
    }

    private static <K> List<Hotspot> top(Map<K, LongAdder> source, int limit) {
        List<Hotspot> result = new ArrayList<>(source.size());
        source.forEach((key, value) -> result.add(new Hotspot(String.valueOf(key), value.sum())));
        result.sort(Comparator.comparingLong(Hotspot::count).reversed());
        if (result.size() > limit) return List.copyOf(result.subList(0, limit));
        return List.copyOf(result);
    }

    public record Hotspot(String key, long count) {}

    public record Snapshot(
            long triggers,
            long rawListeners,
            long candidateListeners,
            long predicateTests,
            long fullScans,
            long fallbacks,
            long mismatches,
            long totalNanos,
            long maxNanos,
            long p95Nanos,
            Map<String, Long> fallbackReasons,
            Map<String, Long> indexConditions,
            List<Hotspot> playerHotspots,
            List<Hotspot> itemHotspots) {
        public double reductionPercent() {
            return rawListeners == 0 ? 0.0D : (1.0D - (double)candidateListeners / rawListeners) * 100.0D;
        }

        public double averageMicros() {
            return triggers == 0 ? 0.0D : (double)totalNanos / triggers / 1_000.0D;
        }
    }
}
//#endif

