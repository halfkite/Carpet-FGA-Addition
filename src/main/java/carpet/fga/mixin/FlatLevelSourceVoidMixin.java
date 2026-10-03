//#if MC >= 1.21 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.TerrainRegenerationManager;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
//#if MC >= 26.3
//$$ import net.minecraft.core.Holder;
//$$ import net.minecraft.server.level.WorldGenRegion;
//$$ import net.minecraft.world.level.biome.Biome;
//$$ import net.minecraft.world.level.biome.BiomeManager;
//$$ import java.util.Set;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/** Leave biome, structure-start and structure-reference generation intact, but place no flat layers. */
@Mixin(FlatLevelSource.class)
public abstract class FlatLevelSourceVoidMixin {
    //#if MC >= 26.3
    //$$ @Inject(method = "buildTerrain", at = @At("HEAD"), cancellable = true)
    //$$ private void fga$skipFlatTerrain(ChunkAccess chunk, Blender blender, RandomState random,
    //$$         StructureManager structures, BiomeManager biomes, WorldGenRegion region,
    //$$         Set<Holder<Biome>> biomeSet, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
    //#else
    @Inject(method = "fillFromNoise", at = @At("HEAD"), cancellable = true)
    private void fga$skipFlatTerrain(Blender blender, RandomState random, StructureManager structures,
            ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
    //#endif
        if (FGASettings.voidWorldGeneration && !TerrainRegenerationManager.forceNormalGeneration(chunk)) {
            cir.setReturnValue(CompletableFuture.completedFuture(chunk));
        }
    }
}
//#endif
