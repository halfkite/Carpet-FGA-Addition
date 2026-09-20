package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.3
import carpet.fga.BoneMealMaxEfficiency;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BambooStalkBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BambooStalkBlock.class)
abstract class BambooStalkBlockBoneMealMaxMixin {
    @Redirect(
            //#if MC < 26.3
            method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            //#else
            //$$ method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/BonemealSource;)V",
            //#endif
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/RandomSource;nextInt(I)I")
    )
    private int carpetFga$maxBambooGrowth(RandomSource random, int bound) {
        return BoneMealMaxEfficiency.chooseBambooGrowth(random, bound);
    }
}
//#endif
