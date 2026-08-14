//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.TrialSpawnerMultiplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;
import java.util.UUID;

@Mixin(TrialSpawnerState.class)
public abstract class TrialSpawnerStateRewardMultiplierMixin {
    @Redirect(
            method =
                    //#if MC >= 26.1.2
                    //$$ "lambda$tickAndGetNext$2(Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawner;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/resources/ResourceKey;)V",
                    //#else
                    "method_55211(Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawner;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/resources/ResourceKey;)V",
                    //#endif
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawner;ejectReward(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/resources/ResourceKey;)V")
    )
    private static void carpetFga$ejectScaledReward(TrialSpawner spawner, ServerLevel level, BlockPos pos,
                                                    ResourceKey<LootTable> loot) {
        TrialSpawnerData data = spawner.getData();
        Set<UUID> players = ((TrialSpawnerDataAccessor) data).carpetFga$getDetectedPlayers();
        UUID current = players.isEmpty() ? null : players.iterator().next();
        int copies = current == null ? 1 : TrialSpawnerMultiplier.participantWeight(current);
        for (int i = 0; i < copies; i++) spawner.ejectReward(level, pos, loot);
    }
}
//#endif
