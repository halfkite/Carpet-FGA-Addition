//#if MC >= 1.20.1 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.ResilientPlants;
import net.minecraft.core.BlockPos;
//#if MC >= 1.21 && MC <= 26.3
import net.minecraft.server.level.WorldGenRegion;
//#endif
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class ResilientPlantBlockStateMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void carpetFga$allowConfiguredPlants(LevelReader level, BlockPos pos,
                                                  CallbackInfoReturnable<Boolean> callback) {
        //#if MC >= 1.21 && MC <= 26.3
        // Terrain features also call canSurvive: never waive support while
        // decorating chunks. ServerLevel implements WorldGenLevel too, so
        // testing that interface would also disable normal player placement
        // and neighbor-update survival overrides. The WorldGenRegion guard is
        // shared by all supported build nodes; the executable server probe is
        // maintained at the 1.21.1 baseline and each port is build-validated.
        if (level instanceof WorldGenRegion) return;
        //#endif
        if (ResilientPlants.matches((BlockState) (Object) this)) {
            callback.setReturnValue(true);
        }
    }
}
//#endif
