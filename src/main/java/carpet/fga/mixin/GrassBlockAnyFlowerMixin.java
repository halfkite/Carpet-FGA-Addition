//#if MC >= 1.21
package carpet.fga.mixin;

import carpet.fga.GrassBonemealAnyFlower;
import carpet.fga.FGASettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.GrassBlock;
//#if MC >= 26.3
//$$ import net.minecraft.world.level.block.BonemealSource;
//#endif
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GrassBlock.class)
abstract class GrassBlockAnyFlowerMixin {
    @Inject(
            //#if MC < 26.3
            method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            //#else
            //$$ method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/BonemealSource;)V",
            //#endif
            at = @At("HEAD"),
            cancellable = true
    )
    private void carpetFga$anyFlowerBonemeal(ServerLevel level, RandomSource random, BlockPos pos,
                                             BlockState state,
                                             //#if MC < 26.3
                                             CallbackInfo ci) {
                                             //#else
                                             //$$ BonemealSource source, CallbackInfo ci) {
                                             //#endif
        if (!"false".equals(FGASettings.grassBonemealAnyFlower)) {
            GrassBonemealAnyFlower.performBonemeal(level, random, pos);
            ci.cancel();
        }
    }
}
//#endif
