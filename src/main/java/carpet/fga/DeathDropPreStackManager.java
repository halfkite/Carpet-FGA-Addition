//#if MC <= 26.2
package carpet.fga;

import carpet.fga.mixin.ItemEntityAccessor;
import net.minecraft.core.BlockPos;
//#if MC >= 1.19.3
import net.minecraft.core.registries.BuiltInRegistries;
//#else
//$$ import net.minecraft.core.Registry;
//#endif
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

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

    public static void begin(Entity entity, ServerLevel level) {
        Double range = FGASettings.preStackEntityRange(entity);
        if (range == null) {
            throw new IllegalArgumentException("entity is not configured for pre-stacking: " + entity.getType());
        }
        CONTEXTS.get().push(CaptureContext.entity(entity, level, range));
    }

    public static void beginBlock(ServerLevel level, BlockPos position, BlockState state) {
        CONTEXTS.get().push(CaptureContext.block(level, position, state));
    }

    public static boolean blockPreStackConfigured() {
        //#if MC >= 1.20.5 && MC < 26.2
        return !DropPreStackConfig.snapshot().blocks().isEmpty()
                || !DropPreStackConfig.snapshot().containerBlocks().isEmpty();
        //#else
        //$$ return false;
        //#endif
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
            ResourceLocation itemId =
                    //#if MC >= 1.19.3
                    BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
                    //#else
                    //$$ Registry.ITEM.getKey(itemEntity.getItem().getItem());
                    //#endif
        double range = context.rangeFor(itemId);
        if (!Double.isFinite(range)) {
            return false;
        }
        context.drops.add(new CapturedDrop(itemEntity, range));
        return true;
    }

    public static void finish(Entity entity, boolean completedNormally) {
        finishSource(entity, completedNormally);
    }

    public static void finishBlock(ServerLevel level, BlockPos position, boolean completedNormally) {
        finishSource(new BlockSource(level, position), completedNormally);
    }

    private static void finishSource(Object source, boolean completedNormally) {
        Deque<CaptureContext> contexts = CONTEXTS.get();
        if (contexts.isEmpty()) {
            return;
        }
        CaptureContext context = contexts.pop();
        if (contexts.isEmpty()) {
            CONTEXTS.remove();
        }
        if (!context.matches(source)) {
            clear();
            throw new IllegalStateException("Mismatched drop pre-stack capture context");
        }

        if (!completedNormally) {
            CACHED_DROPS.clear();
            context.drops.forEach(drop -> addDirectly(context.level, drop.entity()));
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
        for (CapturedDrop captured : context.drops) {
            ItemEntity source = captured.entity();
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
                if (remaining == 0 || !cached.isEligible(context.position, captured.range())) {
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
                cache.add(new CachedDrop(spawned, context.position));
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

    private record CaptureContext(Object source, ServerLevel level, Vec3 position, double entityRange,
                                  double containerBlockRange, boolean blockSource, List<CapturedDrop> drops) {
        private static CaptureContext entity(Entity entity, ServerLevel level, double range) {
            return new CaptureContext(entity, level, entity.position(), range, Double.NaN, false, new ArrayList<>());
        }

        private static CaptureContext block(ServerLevel level, BlockPos position, BlockState state) {
            //#if MC >= 1.20.5 && MC < 26.2
            ResourceLocation blockId =
                    //#if MC >= 1.19.3
                    BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    //#else
                    //$$ Registry.BLOCK.getKey(state.getBlock());
                    //#endif
            Double range = DropPreStackConfig.containerBlockRange(blockId);
            if (range == null) {
                ResourceLocation itemId =
                        //#if MC >= 1.19.3
                        BuiltInRegistries.ITEM.getKey(state.getBlock().asItem());
                        //#else
                        //$$ Registry.ITEM.getKey(state.getBlock().asItem());
                        //#endif
                range = DropPreStackConfig.blockRange(itemId);
            }
            return new CaptureContext(new BlockSource(level, position), level, Vec3.atCenterOf(position), Double.NaN,
                    range == null ? Double.NaN : range, true, new ArrayList<>());
            //#else
            //$$ return new CaptureContext(new BlockSource(level, position), level, Vec3.atCenterOf(position), Double.NaN,
            //$$         Double.NaN, true, new ArrayList<>());
            //#endif
        }

        private double rangeFor(ResourceLocation itemId) {
            if (!blockSource) return entityRange;
            //#if MC >= 1.20.5 && MC < 26.2
            if (Double.isFinite(containerBlockRange)) return containerBlockRange;
            Double range = DropPreStackConfig.blockRange(itemId);
            return range == null ? Double.NaN : range;
            //#else
            //$$ return Double.NaN;
            //#endif
        }

        private boolean matches(Object candidate) {
            return source == candidate || (source instanceof BlockSource expected
                    && candidate instanceof BlockSource actual
                    && expected.level() == actual.level()
                    && expected.position().equals(actual.position()));
        }
    }

    private record CapturedDrop(ItemEntity entity, double range) {
    }

    private record BlockSource(ServerLevel level, BlockPos position) {
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
