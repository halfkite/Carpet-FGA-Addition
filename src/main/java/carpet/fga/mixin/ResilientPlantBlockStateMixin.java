//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.ResilientPlants;
import net.minecraft.core.BlockPos;
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
        if (ResilientPlants.matches((BlockState) (Object) this)) {
            callback.setReturnValue(true);
        }
    }
}
//#endif
