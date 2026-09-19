package carpet.fga.mixin;

//#if MC == 1.21.1
import carpet.fga.BoneMealMaxEfficiency;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CropBlock.class)
abstract class CropBlockBoneMealMaxMixin {
    @Redirect(
            method = "getBonemealAgeIncrease(Lnet/minecraft/world/level/Level;)I",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;nextInt(Lnet/minecraft/util/RandomSource;II)I")
    )
    private int carpetFga$maxCropGrowth(RandomSource random, int minimum, int maximum) {
        return BoneMealMaxEfficiency.enabled()
                ? maximum
                : Mth.nextInt(random, minimum, maximum);
    }
}
//#endif
