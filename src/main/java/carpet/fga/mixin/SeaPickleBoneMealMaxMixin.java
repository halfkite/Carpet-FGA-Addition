package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.3
import carpet.fga.BoneMealMaxEfficiency;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SeaPickleBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SeaPickleBlock.class)
abstract class SeaPickleBoneMealMaxMixin {
    @Redirect(
            //#if MC < 26.3
            method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            //#else
            //$$ method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/BonemealSource;)V",
            //#endif
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/RandomSource;nextInt(I)I",
                    ordinal = 1)
    )
    private int carpetFga$maxPickleCount(RandomSource random, int bound) {
        return BoneMealMaxEfficiency.enabled() ? bound - 1 : random.nextInt(bound);
    }
}
//#endif
