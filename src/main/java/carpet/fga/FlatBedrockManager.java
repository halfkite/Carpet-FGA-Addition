//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.resources.ResourceKey;

public final class FlatBedrockManager {
    private FlatBedrockManager() { }

    public static void apply(ChunkAccess chunk, ResourceKey<Level> dimension, NoiseSettings noise) {
        int layers = NewFeatureOptions.bedrockLayers(FGASettings.flatBedrock);
        if (layers == 0 || FGASettings.voidWorldGeneration && !TerrainRegenerationManager.forceNormalGeneration(chunk)) return;
        if (dimension.equals(Level.OVERWORLD)) {
            flatten(chunk, noise.minY(), 1, layers, Blocks.DEEPSLATE.defaultBlockState());
        } else if (dimension.equals(Level.NETHER)) {
            flatten(chunk, noise.minY(), 1, layers, Blocks.NETHERRACK.defaultBlockState());
            flatten(chunk, noise.minY() + noise.height() - 1, -1, layers, Blocks.NETHERRACK.defaultBlockState());
        }
    }

    private static void flatten(ChunkAccess chunk, int edgeY, int direction, int layers, BlockState replacement) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        for (int x = minX; x < minX + 16; x++) for (int z = minZ; z < minZ + 16; z++) {
            boolean hasBedrock = false;
            for (int depth = 0; depth < 5; depth++) {
                pos.set(x, edgeY + direction * depth, z);
                if (chunk.getBlockState(pos).is(Blocks.BEDROCK)) hasBedrock = true;
            }
            // Preserve generators without a bedrock boundary, including empty terrain.
            if (!hasBedrock) continue;
            for (int depth = 0; depth < 5; depth++) {
                pos.set(x, edgeY + direction * depth, z);
                BlockState original = chunk.getBlockState(pos);
                BlockState result = depth < layers ? Blocks.BEDROCK.defaultBlockState()
                        : original.is(Blocks.BEDROCK) ? replacement : original;
                if (result != original) {
//#if MC >= 1.21.5
//$$                     chunk.setBlockState(pos, result, 0);
//#else
                    chunk.setBlockState(pos, result, false);
//#endif
                }
            }
        }
    }
}
//#endif
