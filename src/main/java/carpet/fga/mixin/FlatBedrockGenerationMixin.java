//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.FlatBedrockManager;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class FlatBedrockGenerationMixin {
    @Shadow @Final private Holder<NoiseGeneratorSettings> settings;

//#if MC >= 26.3
//$$     @Inject(method = "buildTerrain", at = @At("RETURN"), cancellable = true)
//$$     private void carpetFga$flatten(ChunkAccess chunk, Blender blender, RandomState random,
//$$             StructureManager structures, BiomeManager biomes, WorldGenRegion region, Set<Holder<Biome>> generatingBiomes,
//$$             CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
//$$         if ("false".equals(FGASettings.flatBedrock)) return;
//$$         cir.setReturnValue(cir.getReturnValue().thenApply(result -> {
//$$             FlatBedrockManager.apply(result, region.getLevel().dimension(), settings.value().noiseSettings());
//$$             return result;
//$$         }));
//$$     }
//#else
    @Inject(method = "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V", at = @At("TAIL"))
    private void carpetFga$flatten(WorldGenRegion region, StructureManager structures, RandomState random,
            ChunkAccess chunk, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if ("false".equals(FGASettings.flatBedrock)) return;
        FlatBedrockManager.apply(chunk, region.getLevel().dimension(), settings.value().noiseSettings());
    }
//#endif
}
//#endif
