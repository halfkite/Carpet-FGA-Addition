package carpet.fga.mixin;

import carpet.fga.FGASettings;
//#if MC >= 1.21 && MC <= 26.2
import carpet.fga.TerrainRegenerationManager;
//#endif
//#if MC >= 1.18
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
//#else
//$$ import net.minecraft.server.level.WorldGenRegion;
//$$ import net.minecraft.world.level.LevelAccessor;
//$$ import net.minecraft.world.level.StructureFeatureManager;
//$$ import net.minecraft.world.level.biome.BiomeManager;
//$$ import net.minecraft.world.level.chunk.ChunkAccess;
//$$ import net.minecraft.world.level.levelgen.GenerationStep;
//$$ import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorVoidMixin {
    private static boolean voiding(){
        //#if MC >= 1.21 && MC <= 26.2
        return FGASettings.voidWorldGeneration&&!TerrainRegenerationManager.forceNormalGeneration();
        //#else
        //$$ return FGASettings.voidWorldGeneration;
        //#endif
    }
    //#if MC >= 1.21
    @Inject(method="fillFromNoise",at=@At("HEAD"),cancellable=true)
    private void fga$skipNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir){if(voiding())cir.setReturnValue(CompletableFuture.completedFuture(chunk));}
    @Inject(method="buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",at=@At("HEAD"),cancellable=true)
    private void fga$skipSurface(WorldGenRegion level,StructureManager structures,RandomState random,ChunkAccess chunk,CallbackInfo ci){if(voiding())ci.cancel();}
    @Inject(method="applyCarvers",at=@At("HEAD"),cancellable=true)
    //#if MC >= 1.21.2
    //$$ private void fga$skipCarvers(WorldGenRegion level,long seed,RandomState random,BiomeManager biomes,StructureManager structures,ChunkAccess chunk,CallbackInfo ci){if(voiding())ci.cancel();}
    //#else
    private void fga$skipCarvers(WorldGenRegion level,long seed,RandomState random,BiomeManager biomes,StructureManager structures,ChunkAccess chunk,GenerationStep.Carving carving,CallbackInfo ci){if(voiding())ci.cancel();}
    //#endif
    //#elseif MC >= 1.19
    //$$ @Inject(method="fillFromNoise",at=@At("HEAD"),cancellable=true)
    //$$ private void fga$skipNoise(java.util.concurrent.Executor executor, Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir){if(voiding())cir.setReturnValue(CompletableFuture.completedFuture(chunk));}
    //$$ @Inject(method="buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",at=@At("HEAD"),cancellable=true)
    //$$ private void fga$skipSurface(WorldGenRegion level,StructureManager structures,RandomState random,ChunkAccess chunk,CallbackInfo ci){if(voiding())ci.cancel();}
    //$$ @Inject(method="applyCarvers",at=@At("HEAD"),cancellable=true)
    //$$ private void fga$skipCarvers(WorldGenRegion level,long seed,RandomState random,BiomeManager biomes,StructureManager structures,ChunkAccess chunk,GenerationStep.Carving carving,CallbackInfo ci){if(voiding())ci.cancel();}
    //#elseif MC >= 1.18
    //$$ @Inject(method="fillFromNoise",at=@At("HEAD"),cancellable=true)
    //$$ private void fga$skipNoise(java.util.concurrent.Executor executor, Blender blender, net.minecraft.world.level.StructureFeatureManager structures, ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir){if(voiding())cir.setReturnValue(CompletableFuture.completedFuture(chunk));}
    //$$ @Inject(method="buildSurface",at=@At("HEAD"),cancellable=true)
    //$$ private void fga$skipSurface(WorldGenRegion level,net.minecraft.world.level.StructureFeatureManager structures,ChunkAccess chunk,CallbackInfo ci){if(voiding())ci.cancel();}
    //$$ @Inject(method="applyCarvers",at=@At("HEAD"),cancellable=true)
    //$$ private void fga$skipCarvers(WorldGenRegion level,long seed,BiomeManager biomes,net.minecraft.world.level.StructureFeatureManager structures,ChunkAccess chunk,GenerationStep.Carving carving,CallbackInfo ci){if(voiding())ci.cancel();}
    //#elseif MC >= 1.17
    //$$ @Inject(method="fillFromNoise",at=@At("HEAD"),cancellable=true)
    //$$ private void fga$skipNoise(java.util.concurrent.Executor executor, StructureFeatureManager structures, ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir){if(voiding())cir.setReturnValue(CompletableFuture.completedFuture(chunk));}
    //$$ @Inject(method="buildSurfaceAndBedrock",at=@At("HEAD"),cancellable=true)
    //$$ private void fga$skipSurface(WorldGenRegion level,ChunkAccess chunk,CallbackInfo ci){if(voiding())ci.cancel();}
    //#else
    //$$ @Inject(method="fillFromNoise",at=@At("HEAD"),cancellable=true)
    //$$ private void fga$skipNoise(LevelAccessor level, StructureFeatureManager structures, ChunkAccess chunk, CallbackInfo ci){if(voiding())ci.cancel();}
    //$$ @Inject(method="buildSurfaceAndBedrock",at=@At("HEAD"),cancellable=true)
    //$$ private void fga$skipSurface(WorldGenRegion level,ChunkAccess chunk,CallbackInfo ci){if(voiding())ci.cancel();}
    //#endif
}
