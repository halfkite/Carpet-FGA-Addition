//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class NaturalIceManager {
    private NaturalIceManager() { }

    public static BlockState choose(BlockState original, RandomSource random) {
        if (!original.is(Blocks.ICE) || "false".equals(FGASettings.iceFormationChances)) return original;
        return switch (NewFeatureOptions.iceChoice(FGASettings.iceFormationChances, random.nextInt(100))) {
            case 1 -> Blocks.PACKED_ICE.defaultBlockState();
            case 2 -> Blocks.BLUE_ICE.defaultBlockState();
            default -> original;
        };
    }
}
//#endif
