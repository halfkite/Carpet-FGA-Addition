//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Mixin(TrialSpawnerData.class)
public interface TrialSpawnerDataAccessor {
    @Accessor("detectedPlayers")
    Set<UUID> carpetFga$getDetectedPlayers();

    @Accessor("currentMobs")
    Set<UUID> carpetFga$getCurrentMobs();

    @Accessor("cooldownEndsAt")
    void carpetFga$setCooldownEndsAt(long value);

    @Accessor("nextMobSpawnsAt")
    void carpetFga$setNextMobSpawnsAt(long value);

    @Accessor("totalMobsSpawned")
    void carpetFga$setTotalMobsSpawned(int value);

    @Accessor("ejectingLootTable")
    Optional<ResourceKey<LootTable>> carpetFga$getEjectingLootTable();

    @Accessor("ejectingLootTable")
    void carpetFga$setEjectingLootTable(Optional<ResourceKey<LootTable>> value);
}
//#endif
