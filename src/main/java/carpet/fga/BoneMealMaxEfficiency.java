package carpet.fga;

//#if MC == 1.21.1
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.util.RandomSource;

import java.util.function.Predicate;

/** Utilities for the 1.21.1 bone meal maximum-efficiency rule. */
public final class BoneMealMaxEfficiency {
    private static final int MAX_TREE_ATTEMPTS = 64;
    private BoneMealMaxEfficiency() {
    }

    public static boolean enabled() {
        return FGASettings.boneMealMaxEfficiency;
    }

    public static int maxBoundedGrowth(int minimum, int maximum) {
        return enabled() ? maximum : minimum;
    }

    public static int chooseBambooGrowth(net.minecraft.util.RandomSource random, int bound) {
        return enabled() ? Math.max(0, bound - 1) : random.nextInt(bound);
    }

    /**
     * Attempts the selected vanilla tree feature repeatedly until it can be
     * placed, while keeping the vanilla feature choice and placement checks.
     * A bounded retry count prevents an impossible location from hanging the
     * server.
     */
    public static boolean growTreeToLimit(TreeGrower treeGrower, ServerLevel level,
                                          BlockPos pos, BlockState state, RandomSource random) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (int attempt = 0; attempt < MAX_TREE_ATTEMPTS; attempt++) {
            if (treeGrower.growTree(level, generator, pos, state, random)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Grows weeping and twisting vines through every currently growable block.
     * The caller keeps the vanilla target and success checks; this only replaces
     * the random number of segments after a successful bone meal operation.
     */
    public static void growVinesToLimit(ServerLevel level, BlockPos pos, BlockState state,
                                        Direction direction, Predicate<BlockState> canGrowInto) {
        BlockPos cursor = pos.relative(direction);
        int age = Math.min(state.getValue(net.minecraft.world.level.block.GrowingPlantHeadBlock.AGE) + 1,
                net.minecraft.world.level.block.GrowingPlantHeadBlock.MAX_AGE);
        while (canGrowInto.test(level.getBlockState(cursor))) {
            level.setBlockAndUpdate(cursor,
                    state.setValue(net.minecraft.world.level.block.GrowingPlantHeadBlock.AGE, age));
            cursor = cursor.relative(direction);
            age = Math.min(age + 1, net.minecraft.world.level.block.GrowingPlantHeadBlock.MAX_AGE);
        }
    }
}
//#endif
