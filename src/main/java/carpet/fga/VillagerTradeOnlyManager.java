//#if MC >= 1.21.1
package carpet.fga;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.GiveGiftToHero;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class VillagerTradeOnlyManager {
    private static final long RESTOCK_CHECK_INTERVAL = 20L;
    private static final Map<Villager, Vec3> ANCHORS = new WeakHashMap<>();
    private static final Map<Villager, Long> NEXT_RESTOCK_CHECKS = new WeakHashMap<>();
    private static final ThreadLocal<Villager> FILTERED_BRAIN = new ThreadLocal<>();
    private static volatile VillagerPerformanceConfig.State config = VillagerPerformanceConfig.State.defaults();

    private VillagerTradeOnlyManager() {}

    public static void applyConfig(VillagerPerformanceConfig.State value) {
        config = value;
        clear();
    }

    public static void clear() {
        synchronized (ANCHORS) {
            ANCHORS.clear();
            NEXT_RESTOCK_CHECKS.clear();
        }
        FILTERED_BRAIN.remove();
    }

    public static boolean enabled() {
        return !FGASettings.villagerPerformanceOptimization.equals("false")
                && !VillagerPerformanceConfig.isLoadFailed();
    }

    public static boolean isTradeOnly(Villager villager) {
        VillagerPerformanceConfig.State current = config;
        if (!enabled() || current.tradeMode() == VillagerPerformanceConfig.TradeMode.FALSE) return release(villager);
        if (matches(villager, current.tradeBlocks(), current.tradeNames())) {
            if (current.tradeMode() == VillagerPerformanceConfig.TradeMode.STATIC) {
                synchronized (ANCHORS) { ANCHORS.putIfAbsent(villager, villager.position()); }
            }
            return true;
        }
        return release(villager);
    }

    public static boolean isGiftVillager(Villager villager) {
        VillagerPerformanceConfig.State current = config;
        return enabled() && current.giftEnabled() && matches(villager, current.giftBlocks(), current.giftNames());
    }

    private static boolean matches(Villager villager, Set<net.minecraft.resources.ResourceLocation> blocks, Set<String> names) {
        if (villager.hasCustomName() && names.contains(villager.getCustomName().getString())) return true;
        Block block = villager.level().getBlockState(villager.blockPosition().below()).getBlock();
        return blocks.contains(BuiltInRegistries.BLOCK.getKey(block));
    }

    private static boolean release(Villager villager) {
        synchronized (ANCHORS) {
            ANCHORS.remove(villager);
            NEXT_RESTOCK_CHECKS.remove(villager);
        }
        return false;
    }

    public static void tickBrain(Brain<LivingEntity> brain, ServerLevel level, Villager villager) {
        if (!isTradeOnly(villager)) {
            brain.tick(level, villager);
            return;
        }
        boolean gift = isGiftVillager(villager);
        if (!gift) return;

        long gameTime = level.getGameTime();
        for (BehaviorControl<? super Villager> behavior : villager.getBrain().getRunningBehaviors()) {
            if (!allowsBehavior(villager, behavior)) behavior.doStop(level, villager, gameTime);
        }
        FILTERED_BRAIN.set(villager);
        try {
            brain.tick(level, villager);
        } finally {
            FILTERED_BRAIN.remove();
        }
    }

    public static boolean allowTryStart(BehaviorControl<?> behavior, LivingEntity entity) {
        Villager villager = FILTERED_BRAIN.get();
        return villager == null || entity != villager || allowsBehavior(villager, behavior);
    }

    public static boolean allowTick(BehaviorControl<?> behavior, LivingEntity entity) {
        return allowTryStart(behavior, entity);
    }

    private static boolean allowsBehavior(Villager villager, BehaviorControl<?> behavior) {
        if (behavior instanceof GiveGiftToHero || behavior instanceof LookAtTargetSink) return true;
        return config.tradeMode() == VillagerPerformanceConfig.TradeMode.AI
                && behavior instanceof MoveToTargetSink;
    }

    public static void tickRestock(Villager villager) {
        if (!isTradeOnly(villager) || villager.isBaby() || !(villager.level() instanceof ServerLevel level)) return;
        //#if MC >= 1.21.5
        //$$ var profession = villager.getVillagerData().profession();
        //$$ if (profession.is(VillagerProfession.NONE) || profession.is(VillagerProfession.NITWIT)) return;
        //#else
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) return;
        //#endif
        long gameTime = level.getGameTime();
        synchronized (ANCHORS) {
            if (gameTime < NEXT_RESTOCK_CHECKS.getOrDefault(villager, 0L)) return;
            NEXT_RESTOCK_CHECKS.put(villager, gameTime + RESTOCK_CHECK_INTERVAL);
        }
        //#if MC >= 1.21.11
        //$$ villager.getBrain().updateActivityFromSchedule(level.environmentAttributes(), gameTime, villager.position());
        //$$ boolean workTime = villager.getBrain().isActive(Activity.WORK);
        //#else
        int dayTime = (int) Math.floorMod(level.getDayTime(), 24000L);
        boolean workTime = villager.getBrain().getSchedule().getActivityAt(dayTime) == Activity.WORK;
        //#endif
        if (workTime && villager.canRestock()
                &&
                //#if MC >= 1.21.11
                //$$ villager.shouldRestock(level)
                //#else
                villager.shouldRestock()
                //#endif
        ) villager.restock();
    }

    public static void applyStatic(Villager villager) {
        if (!enabled() || config.tradeMode() != VillagerPerformanceConfig.TradeMode.STATIC) return;
        synchronized (ANCHORS) {
            Vec3 anchor = ANCHORS.get(villager);
            if (anchor == null) return;
            BlockPos blockPos = BlockPos.containing(anchor).below();
            boolean nameMatches = villager.hasCustomName() && config.tradeNames().contains(villager.getCustomName().getString());
            boolean blockMatches = config.tradeBlocks().contains(BuiltInRegistries.BLOCK.getKey(villager.level().getBlockState(blockPos).getBlock()));
            if (!nameMatches && !blockMatches) { ANCHORS.remove(villager); return; }
            villager.setPos(anchor.x, anchor.y, anchor.z);
            villager.setDeltaMovement(Vec3.ZERO);
        }
    }
}
//#endif
