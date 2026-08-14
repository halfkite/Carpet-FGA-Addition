//#if MC >= 1.21 && MC <= 26.2
package carpet.fga;

import carpet.fga.mixin.ChunkMapTrialStopAccessor;
import carpet.fga.mixin.TrialSpawnerDataAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public final class TrialSpawnerStopManager {
    private static final Map<TrialSpawner, Boolean> REFRESH_AFTER_REWARD = new WeakHashMap<>();

    private TrialSpawnerStopManager() {}

    public enum RewardMode { NONE, REWARD, FAST }

    public static Result stop(ServerLevel level, Predicate<BlockPos> area, RewardMode mode, boolean clearMobs) {
        int scanned = 0;
        int stopped = 0;
        int removed = 0;
        Set<Long> seenChunks = new HashSet<>();
        ChunkMapTrialStopAccessor chunks = (ChunkMapTrialStopAccessor) level.getChunkSource().chunkMap;
        Iterable<ChunkHolder> loaded =
                //#if MC >= 1.21.10
                //$$ chunks.carpetFga$getVisibleChunkMap().values();
                //#else
                chunks.carpetFga$getLoadedChunks();
                //#endif
        for (ChunkHolder holder : loaded) {
            LevelChunk chunk = holder.getTickingChunk();
            if (chunk == null) chunk = holder.getChunkToSend();
            if (chunk == null || !seenChunks.add(
                    //#if MC >= 26.1.2
                    //$$ chunk.getPos().pack()
                    //#else
                    chunk.getPos().toLong()
                    //#endif
            )) continue;
            scanned++;
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (!(blockEntity instanceof TrialSpawnerBlockEntity trial) || !area.test(trial.getBlockPos())) continue;
                StopResult result = stopOne(level, trial, mode, clearMobs);
                if (result.stopped()) stopped++;
                removed += result.removedMobs();
            }
        }
        return new Result(scanned, stopped, removed);
    }

    private static StopResult stopOne(ServerLevel level, TrialSpawnerBlockEntity blockEntity,
                                      RewardMode mode, boolean clearMobs) {
        TrialSpawner spawner = blockEntity.getTrialSpawner();
        TrialSpawnerState state = spawner.getState();
        TrialSpawnerData data = spawner.getData();
        TrialSpawnerDataAccessor access = (TrialSpawnerDataAccessor) data;
        int removed = clearMobs ? clearTrackedMobs(level, access.carpetFga$getCurrentMobs()) : 0;
        if (state == TrialSpawnerState.INACTIVE) {
            REFRESH_AFTER_REWARD.remove(spawner);
            resetData(data, access);
            spawner.markUpdated();
            return new StopResult(true, removed);
        }

        long now = level.getGameTime();
        access.carpetFga$setTotalMobsSpawned(0);
        access.carpetFga$setNextMobSpawnsAt(0L);
        access.carpetFga$setCooldownEndsAt(saturatedAdd(now, spawner.getTargetCooldownLength()));

        if (mode == RewardMode.NONE || access.carpetFga$getDetectedPlayers().isEmpty()) {
            refreshNow(level, blockEntity.getBlockPos(), spawner);
        } else if (mode == RewardMode.FAST) {
            ejectAllRewards(level, blockEntity.getBlockPos(), spawner, access);
            refreshNow(level, blockEntity.getBlockPos(), spawner);
        } else {
            REFRESH_AFTER_REWARD.put(spawner, Boolean.TRUE);
            spawner.setState(level, TrialSpawnerState.WAITING_FOR_REWARD_EJECTION);
            spawner.markUpdated();
        }
        return new StopResult(true, removed);
    }

    public static TrialSpawnerState finishRewardRefresh(ServerLevel level, BlockPos pos,
                                                         TrialSpawner spawner, TrialSpawnerState nextState) {
        if (nextState != TrialSpawnerState.COOLDOWN || REFRESH_AFTER_REWARD.remove(spawner) == null) {
            return nextState;
        }
        refreshNow(level, pos, spawner);
        return TrialSpawnerState.WAITING_FOR_PLAYERS;
    }

    public static void clear() {
        REFRESH_AFTER_REWARD.clear();
    }

    private static void refreshNow(ServerLevel level, BlockPos pos, TrialSpawner spawner) {
        REFRESH_AFTER_REWARD.remove(spawner);
        TrialSpawnerData data = spawner.getData();
        resetData(data, (TrialSpawnerDataAccessor) data);
        spawner.removeOminous(level, pos);
        spawner.setState(level, TrialSpawnerState.WAITING_FOR_PLAYERS);
        spawner.markUpdated();
    }

    private static void resetData(TrialSpawnerData data, TrialSpawnerDataAccessor access) {
        data.reset();
        access.carpetFga$setEjectingLootTable(Optional.empty());
    }

    private static int clearTrackedMobs(ServerLevel level, Set<UUID> tracked) {
        int removed = 0;
        for (UUID id : Set.copyOf(tracked)) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
                removed++;
            }
        }
        tracked.clear();
        return removed;
    }

    private static void ejectAllRewards(ServerLevel level, BlockPos pos, TrialSpawner spawner,
                                        TrialSpawnerDataAccessor access) {
        TrialSpawnerConfig config =
                //#if MC >= 1.21.8
                //$$ spawner.activeConfig();
                //#else
                spawner.getConfig();
                //#endif
        Optional<ResourceKey<LootTable>> selected = access.carpetFga$getEjectingLootTable();
        if (selected.isEmpty()) {
            //#if MC >= 1.21.5
            //$$ selected = config.lootTablesToEject().getRandom(level.getRandom());
            //#else
            selected = config.lootTablesToEject().getRandomValue(level.getRandom());
            //#endif
        }
        if (selected.isPresent()) {
            ResourceKey<LootTable> loot = selected.get();
            while (!access.carpetFga$getDetectedPlayers().isEmpty()) {
                UUID player = access.carpetFga$getDetectedPlayers().iterator().next();
                int copies = TrialSpawnerMultiplier.participantWeight(player);
                for (int i = 0; i < copies; i++) spawner.ejectReward(level, pos, loot);
                access.carpetFga$getDetectedPlayers().remove(player);
            }
        } else {
            access.carpetFga$getDetectedPlayers().clear();
        }
        access.carpetFga$setEjectingLootTable(Optional.empty());
    }

    private static long saturatedAdd(long value, int addition) {
        if (addition > 0 && value > Long.MAX_VALUE - addition) return Long.MAX_VALUE;
        return value + addition;
    }

    private record StopResult(boolean stopped, int removedMobs) {}
    public record Result(int scannedChunks, int stoppedSpawners, int removedMobs) {}
}
//#endif
