package carpet.fga.mixin;

import carpet.fga.FGASettings;
//#if MC >= 1.21 && MC <= 26.2
import carpet.fga.TerrainRegenerationManager;
//#endif
//#if MC >= 1.18
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
//#else
//$$ import net.minecraft.server.level.WorldGenRegion;
//$$ import net.minecraft.world.level.StructureFeatureManager;
//$$ import net.minecraft.world.level.chunk.ChunkGenerator;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorVoidDecorationMixin {
    @Inject(method="applyBiomeDecoration",at=@At("HEAD"),cancellable=true)
    //#if MC >= 1.18
    private void fga$skipFeatures(WorldGenLevel level,ChunkAccess chunk,StructureManager structures,CallbackInfo ci){
    //#else
    //$$ private void fga$skipFeatures(WorldGenRegion level,StructureFeatureManager structures,CallbackInfo ci){
    //#endif
        //#if MC >= 1.21 && MC <= 26.2
        if(FGASettings.voidWorldGeneration&&!TerrainRegenerationManager.forceNormalGeneration())ci.cancel();
        //#else
        //$$ if(FGASettings.voidWorldGeneration)ci.cancel();
        //#endif
    }
}
