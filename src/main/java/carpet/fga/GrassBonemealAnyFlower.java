//#if MC >= 1.21
package carpet.fga;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BonemealableBlock;
//#if MC >= 26.3
//$$ import net.minecraft.world.level.block.BonemealSource;
//#endif
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * grassBonemealAnyFlower 规则实现。
 *
 * 重写草方块骨粉催熟（GrassBlock#performBonemeal）中的花分支：
 * 原版只会放置当前群系的 flower feature（其 placed feature 带 BiomeFilter，
 * 无法跨群系复用），这里改为从固定花表中随机选花直接放置，
 * 游走与草分支逻辑与 1.21.1 原版字节码保持一致。
 */
public final class GrassBonemealAnyFlower {
    /** 原版本就能通过骨粉催熟获得的小花 */
    public static final List<Block> SMALL_FLOWERS = List.of(
            Blocks.DANDELION,
            Blocks.POPPY,
            Blocks.BLUE_ORCHID,
            Blocks.ALLIUM,
            Blocks.AZURE_BLUET,
            Blocks.RED_TULIP,
            Blocks.ORANGE_TULIP,
            Blocks.WHITE_TULIP,
            Blocks.PINK_TULIP,
            Blocks.OXEYE_DAISY,
            Blocks.CORNFLOWER,
            Blocks.LILY_OF_THE_VALLEY
    );

    /** 大花（all 模式额外包含） */
    public static final List<Block> TALL_FLOWERS = List.of(
            Blocks.SUNFLOWER,
            Blocks.LILAC,
            Blocks.ROSE_BUSH,
            Blocks.PEONY
    );

    public static final List<Block> ALL_FLOWERS = Stream.concat(
            SMALL_FLOWERS.stream(), TALL_FLOWERS.stream()).toList();

    private GrassBonemealAnyFlower() {
    }

    public static boolean isEnabled() {
        return !"false".equals(FGASettings.grassBonemealAnyFlower);
    }

    public static void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos) {
        BlockPos origin = pos.above();
        Optional<Holder.Reference<PlacedFeature>> grassBonemeal =
                //#if MC <= 1.21.1
                level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
                        .getHolder(VegetationPlacements.GRASS_BONEMEAL);
                //#else
                //$$ level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
                //$$         .get(VegetationPlacements.GRASS_BONEMEAL);
                //#endif

        outer:
        for (int i = 0; i < 128; i++) {
            BlockPos target = origin;

            for (int j = 0; j < i / 16; j++) {
                target = target.offset(random.nextInt(3) - 1,
                        (random.nextInt(3) - 1) * random.nextInt(3) / 2,
                        random.nextInt(3) - 1);
                if (!level.getBlockState(target.below()).is(Blocks.GRASS_BLOCK)
                        || level.getBlockState(target).isCollisionShapeFullBlock(level, target)) {
                    continue outer;
                }
            }

            BlockState targetState = level.getBlockState(target);
            if (targetState.is(Blocks.SHORT_GRASS) && random.nextInt(10) == 0) {
                //#if MC < 26.3
                if (((BonemealableBlock) Blocks.SHORT_GRASS)
                        .isValidBonemealTarget(level, target, targetState)) {
                    ((BonemealableBlock) Blocks.SHORT_GRASS)
                            .performBonemeal(level, random, target, targetState);
                }
                //#else
                //$$ if (((BonemealableBlock) Blocks.SHORT_GRASS)
                //$$         .isValidBonemealTarget(level, target, targetState, BonemealSource.INTERACTION)) {
                //$$     ((BonemealableBlock) Blocks.SHORT_GRASS)
                //$$             .performBonemeal(level, random, target, targetState, BonemealSource.INTERACTION);
                //$$ }
                //#endif
            }

            if (!targetState.isAir()) {
                continue;
            }

            //#if MC >= 26.1.2
            //$$ if (level.isOutsideBuildHeight(target)) {
            //$$     continue;
            //$$ }
            //#endif

            if (random.nextInt(8) == 0) {
                List<Block> flowers = "all".equals(FGASettings.grassBonemealAnyFlower)
                        ? ALL_FLOWERS
                        : SMALL_FLOWERS;
                BlockState flowerState = flowers.get(random.nextInt(flowers.size())).defaultBlockState();
                if (!flowerState.canSurvive(level, target)) {
                    continue;
                }
                if (flowerState.getBlock() instanceof DoublePlantBlock) {
                    DoublePlantBlock.placeAt(level, flowerState, target, 2);
                } else {
                    level.setBlock(target, flowerState, 2);
                }
            } else {
                if (grassBonemeal.isEmpty()) {
                    continue;
                }
                grassBonemeal.get().value()
                        .place(level, level.getChunkSource().getGenerator(), random, target);
            }
        }
    }
}
//#endif
