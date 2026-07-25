//#if MC <= 26.2
package carpet.fga;

import carpet.fga.mixin.ItemEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class DeathDropPreStackManager {
    private static final ThreadLocal<Deque<CaptureContext>> CONTEXTS =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<ItemEntity>> DIRECT_SPAWNS =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<ServerLevel, List<CachedDrop>> CACHED_DROPS = new IdentityHashMap<>();

    private DeathDropPreStackManager() {
    }

    public static void begin(Mob mob, ServerLevel level) {
        CONTEXTS.get().push(new CaptureContext(mob, level));
    }

    public static boolean capture(ServerLevel level, Entity entity) {
        if (entity instanceof ItemEntity itemEntity && DIRECT_SPAWNS.get().contains(itemEntity)) {
            return false;
        }
        Deque<CaptureContext> contexts = CONTEXTS.get();
        if (contexts.isEmpty() || !(entity instanceof ItemEntity itemEntity)) {
            return false;
        }
        CaptureContext context = contexts.peek();
        if (context.level != level) {
            return false;
        }
        context.drops.add(itemEntity);
        return true;
    }

    public static void finish(Mob mob, boolean completedNormally) {
        Deque<CaptureContext> contexts = CONTEXTS.get();
        if (contexts.isEmpty()) {
            return;
        }
        CaptureContext context = contexts.pop();
        if (contexts.isEmpty()) {
            CONTEXTS.remove();
        }
        if (context.mob != mob) {
            clear();
            throw new IllegalStateException("Mismatched mob death drop capture context");
        }

        if (!completedNormally) {
            CACHED_DROPS.clear();
            context.drops.forEach(drop -> addDirectly(context.level, drop));
            return;
        }
        flush(context);
    }

    public static void clearTickCache() {
        CACHED_DROPS.clear();
    }

    public static void clear() {
        CONTEXTS.remove();
        DIRECT_SPAWNS.remove();
        CACHED_DROPS.clear();
    }

    private static void flush(CaptureContext context) {
        List<CachedDrop> cache = CACHED_DROPS.computeIfAbsent(context.level, ignored -> new ArrayList<>());
        for (ItemEntity source : context.drops) {
            ItemStack sourceStack = source.getItem();
            if (sourceStack.isEmpty()) {
                continue;
            }

            int limit = FGASettings.effectiveDroppedItemStackLimit(sourceStack);
            if (limit <= 1) {
                addDirectly(context.level, source);
                continue;
            }

            int remaining = sourceStack.getCount();
            for (CachedDrop cached : cache) {
                if (remaining == 0 || !cached.isEligible(context.deathPosition, FGASettings.preStackMobDeathDropsRange)) {
                    continue;
                }
                ItemEntity destination = cached.entity;
                if (!destination.isAlive()) {
                    continue;
                }
                ItemStack destinationStack = destination.getItem();
                if (!FGACompat.isSameItemSameTags(sourceStack, destinationStack)) {
                    continue;
                }
                int destinationLimit = FGASettings.effectiveDroppedItemStackLimit(destinationStack);
                int moved = Math.min(remaining, Math.max(0, destinationLimit - destinationStack.getCount()));
                if (moved > 0) {
                    destinationStack.grow(moved);
                    remaining -= moved;
                }
            }

            boolean usedSource = false;
            while (remaining > 0) {
                int count = Math.min(remaining, limit);
                ItemEntity spawned;
                if (!usedSource) {
                    sourceStack.setCount(count);
                    spawned = source;
                    usedSource = true;
                } else {
                    spawned = copyForOverflow(source, FGACompat.copyWithCount(sourceStack, count));
                }
                addDirectly(context.level, spawned);
                cache.add(new CachedDrop(spawned, context.deathPosition));
                remaining -= count;
            }
        }
    }

    private static ItemEntity copyForOverflow(ItemEntity source, ItemStack stack) {
        ItemEntity copy = FGACompat.createItemEntity(FGACompat.level(source), source.getX(), source.getY(), source.getZ(), stack,
                source.getDeltaMovement().x, source.getDeltaMovement().y, source.getDeltaMovement().z);
        FGACompat.copyRotation(source, copy);
        ((ItemEntityAccessor) copy).carpetFga$setPickupDelay(
                ((ItemEntityAccessor) source).carpetFga$getPickupDelay());
        return copy;
    }

    private static void addDirectly(ServerLevel level, ItemEntity entity) {
        Deque<ItemEntity> directSpawns = DIRECT_SPAWNS.get();
        directSpawns.push(entity);
        try {
            level.addFreshEntity(entity);
        } finally {
            directSpawns.pop();
            if (directSpawns.isEmpty()) {
                DIRECT_SPAWNS.remove();
            }
        }
    }

    private record CaptureContext(Mob mob, ServerLevel level, Vec3 deathPosition, List<ItemEntity> drops) {
        private CaptureContext(Mob mob, ServerLevel level) {
            this(mob, level, mob.position(), new ArrayList<>());
        }
    }

    private record CachedDrop(ItemEntity entity, Vec3 deathPosition) {
        private boolean isEligible(Vec3 otherPosition, double range) {
            if (range == 0.0D) {
                return FGACompat.containing(deathPosition).equals(FGACompat.containing(otherPosition));
            }
            return deathPosition.distanceToSqr(otherPosition) <= range * range;
        }
    }
}
//#endif
