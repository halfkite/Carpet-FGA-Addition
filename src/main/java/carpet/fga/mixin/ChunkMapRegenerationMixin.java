//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.TerrainRegenerationManager;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * A chunk covered by a live regeneration task must not be loaded from its stored data: vanilla only
 * runs the generation pipeline when the stored chunk status is not good enough, so the load is
 * answered with a fresh empty chunk and the chunk is generated again. Deleting the stored region
 * data instead does not work, because the server writes the whole region header from a cached copy
 * and puts the deleted entries back.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapRegenerationMixin {
    @Shadow
    @Final
    ServerLevel level;

    @Inject(method = "scheduleChunkLoad", at = @At("HEAD"), cancellable = true)
    private void carpetFga$regenerateInsteadOfLoading(ChunkPos pos,
                                                      CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        String dimension = this.level.dimension().location().toString();
        if (TerrainRegenerationManager.shouldRegenerateFromScratch(pos, dimension)) {
            ChunkAccess fresh = ((ChunkMapRegenerationAccessor) this).carpetFga$createEmptyChunk(pos);
            TerrainRegenerationManager.markRegenerated(pos, dimension);
            cir.setReturnValue(CompletableFuture.completedFuture(fresh));
        }
    }
}
//#endif
