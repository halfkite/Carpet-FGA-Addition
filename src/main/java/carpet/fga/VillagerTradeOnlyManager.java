//#if MC >= 1.20.1
package carpet.fga;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class VillagerTradeOnlyManager {
    private static final long GIFT_CHECK_INTERVAL = 60L;
    private static final long RESTOCK_CHECK_INTERVAL = 20L;
    private static final int GIFT_THROW_DISTANCE = 5;
    private static final int MIN_TIME_BETWEEN_GIFTS = 600;
    private static final int MAX_TIME_BETWEEN_GIFTS = 6600;
    //#if MC >= 1.21.5
    //$$ private static final Map<ResourceKey<VillagerProfession>, ResourceKey<LootTable>> GIFTS = Map.ofEntries(
    //$$         Map.entry(VillagerProfession.ARMORER, BuiltInLootTables.ARMORER_GIFT),
    //$$         Map.entry(VillagerProfession.BUTCHER, BuiltInLootTables.BUTCHER_GIFT),
    //$$         Map.entry(VillagerProfession.CARTOGRAPHER, BuiltInLootTables.CARTOGRAPHER_GIFT),
    //$$         Map.entry(VillagerProfession.CLERIC, BuiltInLootTables.CLERIC_GIFT),
    //$$         Map.entry(VillagerProfession.FARMER, BuiltInLootTables.FARMER_GIFT),
    //$$         Map.entry(VillagerProfession.FISHERMAN, BuiltInLootTables.FISHERMAN_GIFT),
    //$$         Map.entry(VillagerProfession.FLETCHER, BuiltInLootTables.FLETCHER_GIFT),
    //$$         Map.entry(VillagerProfession.LEATHERWORKER, BuiltInLootTables.LEATHERWORKER_GIFT),
    //$$         Map.entry(VillagerProfession.LIBRARIAN, BuiltInLootTables.LIBRARIAN_GIFT),
    //$$         Map.entry(VillagerProfession.MASON, BuiltInLootTables.MASON_GIFT),
    //$$         Map.entry(VillagerProfession.SHEPHERD, BuiltInLootTables.SHEPHERD_GIFT),
    //$$         Map.entry(VillagerProfession.TOOLSMITH, BuiltInLootTables.TOOLSMITH_GIFT),
    //$$         Map.entry(VillagerProfession.WEAPONSMITH, BuiltInLootTables.WEAPONSMITH_GIFT)
    //$$ );
    //#elseif MC >= 1.20.5
    private static final Map<VillagerProfession, ResourceKey<LootTable>> GIFTS = Map.ofEntries(
            Map.entry(VillagerProfession.ARMORER, BuiltInLootTables.ARMORER_GIFT),
            Map.entry(VillagerProfession.BUTCHER, BuiltInLootTables.BUTCHER_GIFT),
            Map.entry(VillagerProfession.CARTOGRAPHER, BuiltInLootTables.CARTOGRAPHER_GIFT),
            Map.entry(VillagerProfession.CLERIC, BuiltInLootTables.CLERIC_GIFT),
            Map.entry(VillagerProfession.FARMER, BuiltInLootTables.FARMER_GIFT),
            Map.entry(VillagerProfession.FISHERMAN, BuiltInLootTables.FISHERMAN_GIFT),
            Map.entry(VillagerProfession.FLETCHER, BuiltInLootTables.FLETCHER_GIFT),
            Map.entry(VillagerProfession.LEATHERWORKER, BuiltInLootTables.LEATHERWORKER_GIFT),
            Map.entry(VillagerProfession.LIBRARIAN, BuiltInLootTables.LIBRARIAN_GIFT),
            Map.entry(VillagerProfession.MASON, BuiltInLootTables.MASON_GIFT),
            Map.entry(VillagerProfession.SHEPHERD, BuiltInLootTables.SHEPHERD_GIFT),
            Map.entry(VillagerProfession.TOOLSMITH, BuiltInLootTables.TOOLSMITH_GIFT),
            Map.entry(VillagerProfession.WEAPONSMITH, BuiltInLootTables.WEAPONSMITH_GIFT)
    );
    //#else
    //$$ private static final Map<VillagerProfession, net.minecraft.resources.ResourceLocation> GIFTS = Map.ofEntries(
    //$$         Map.entry(VillagerProfession.ARMORER, BuiltInLootTables.ARMORER_GIFT),
    //$$         Map.entry(VillagerProfession.BUTCHER, BuiltInLootTables.BUTCHER_GIFT),
    //$$         Map.entry(VillagerProfession.CARTOGRAPHER, BuiltInLootTables.CARTOGRAPHER_GIFT),
    //$$         Map.entry(VillagerProfession.CLERIC, BuiltInLootTables.CLERIC_GIFT),
    //$$         Map.entry(VillagerProfession.FARMER, BuiltInLootTables.FARMER_GIFT),
    //$$         Map.entry(VillagerProfession.FISHERMAN, BuiltInLootTables.FISHERMAN_GIFT),
    //$$         Map.entry(VillagerProfession.FLETCHER, BuiltInLootTables.FLETCHER_GIFT),
    //$$         Map.entry(VillagerProfession.LEATHERWORKER, BuiltInLootTables.LEATHERWORKER_GIFT),
    //$$         Map.entry(VillagerProfession.LIBRARIAN, BuiltInLootTables.LIBRARIAN_GIFT),
    //$$         Map.entry(VillagerProfession.MASON, BuiltInLootTables.MASON_GIFT),
    //$$         Map.entry(VillagerProfession.SHEPHERD, BuiltInLootTables.SHEPHERD_GIFT),
    //$$         Map.entry(VillagerProfession.TOOLSMITH, BuiltInLootTables.TOOLSMITH_GIFT),
    //$$         Map.entry(VillagerProfession.WEAPONSMITH, BuiltInLootTables.WEAPONSMITH_GIFT)
    //$$ );
    //#endif
    private static final Map<Villager, Vec3> ANCHORS = new WeakHashMap<>();
    private static final Map<Villager, Long> NEXT_RESTOCK_CHECKS = new WeakHashMap<>();
    private static final Map<Villager, Long> NEXT_GIFT_CHECKS = new WeakHashMap<>();
    private static final Map<Villager, Long> NEXT_GIFT_READY = new WeakHashMap<>();
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
            NEXT_GIFT_CHECKS.clear();
            NEXT_GIFT_READY.clear();
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
        boolean trade = isTradeOnly(villager);
        boolean gift = isGiftVillager(villager);
        if (!gift) {
            synchronized (ANCHORS) {
                NEXT_GIFT_CHECKS.remove(villager);
                NEXT_GIFT_READY.remove(villager);
            }
        }
        if (gift) {
            maybeThrowGift(level, villager);
            if (!trade) return;
        }
        if (!trade) {
            brain.tick(level, villager);
            return;
        }

        // Trade-optimized villagers keep Brain completely off. Gift throwing is handled above.
        long gameTime = level.getGameTime();
        for (BehaviorControl<? super Villager> behavior : villager.getBrain().getRunningBehaviors()) {
            behavior.doStop(level, villager, gameTime);
        }
    }

    public static boolean allowTryStart(BehaviorControl<?> behavior, LivingEntity entity) {
        Villager villager = FILTERED_BRAIN.get();
        return villager == null || entity != villager;
    }

    public static boolean allowTick(BehaviorControl<?> behavior, LivingEntity entity) {
        return allowTryStart(behavior, entity);
    }

    private static void maybeThrowGift(ServerLevel level, Villager villager) {
        long gameTime = level.getGameTime();
        synchronized (ANCHORS) {
            Long nextCheck = NEXT_GIFT_CHECKS.get(villager);
            if (nextCheck == null) {
                long phase = Math.floorMod(villager.getUUID().hashCode(), (int) GIFT_CHECK_INTERVAL);
                nextCheck = gameTime + Math.floorMod(phase - Math.floorMod(gameTime, GIFT_CHECK_INTERVAL), GIFT_CHECK_INTERVAL);
                NEXT_GIFT_CHECKS.put(villager, nextCheck);
            }
            if (gameTime < nextCheck) return;
            NEXT_GIFT_CHECKS.put(villager, gameTime + GIFT_CHECK_INTERVAL);

            long readyAt = NEXT_GIFT_READY.getOrDefault(villager, 0L);
            if (gameTime < readyAt) return;

            Player hero = findNearestHero(level, villager);
            if (hero == null) return;

            for (ItemStack stack : getGiftItems(level, villager)) {
                if (!stack.isEmpty()) BehaviorUtils.throwItem(villager, stack, hero.position());
            }
            NEXT_GIFT_READY.put(
                    villager,
                    gameTime + MIN_TIME_BETWEEN_GIFTS + villager.getRandom().nextInt(MAX_TIME_BETWEEN_GIFTS - MIN_TIME_BETWEEN_GIFTS + 1)
            );
        }
    }

    private static Player findNearestHero(ServerLevel level, Villager villager) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (!EntitySelector.NO_SPECTATORS.test(player)) continue;
            if (!player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) continue;
            if (!villager.hasLineOfSight(player)) continue;
            if (!villager.blockPosition().closerThan(player.blockPosition(), GIFT_THROW_DISTANCE)) continue;
            double distance = villager.distanceToSqr(player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private static List<ItemStack> getGiftItems(ServerLevel level, Villager villager) {
        if (villager.isBaby()) return List.of(new ItemStack(Items.POPPY));
        //#if MC >= 1.20.5
        ResourceKey<LootTable> giftTable;
        //#else
        //$$ net.minecraft.resources.ResourceLocation giftTable;
        //#endif
        //#if MC >= 1.21.5
        //$$ giftTable = GIFTS.get(villager.getVillagerData().profession().unwrapKey().orElse(null));
        //#else
        giftTable = GIFTS.get(villager.getVillagerData().getProfession());
        //#endif
        if (giftTable == null) return List.of(new ItemStack(Items.WHEAT_SEEDS));
        //#if MC >= 1.20.5
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(giftTable);
        //#else
        //$$ LootTable lootTable = level.getServer().getLootData().getLootTable(giftTable);
        //#endif
        LootParams lootParams = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, villager.position())
                .withParameter(LootContextParams.THIS_ENTITY, villager)
                .create(LootContextParamSets.GIFT);
        return lootTable.getRandomItems(lootParams);
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
