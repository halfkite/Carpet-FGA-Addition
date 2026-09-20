package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.3
import carpet.fga.BoneMealMaxEfficiency;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.StemBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(StemBlock.class)
abstract class StemBlockBoneMealMaxMixin {
    @Redirect(
            //#if MC < 26.3
            method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            //#else
            //$$ method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/BonemealSource;)V",
            //#endif
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;nextInt(Lnet/minecraft/util/RandomSource;II)I")
    )
    private int carpetFga$maxStemGrowth(RandomSource random, int minimum, int maximum) {
        return BoneMealMaxEfficiency.enabled()
                ? maximum
                : Mth.nextInt(random, minimum, maximum);
    }
}
//#endif
