//#if MC >= 1.21 && MC <= 26.2
package carpet.fga;

import carpet.fga.mixin.ChunkMapLoadedChunksAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
//#if MC >= 1.21.2
//$$ import net.minecraft.core.Direction;
//$$ import net.minecraft.world.level.redstone.Orientation;
//#endif

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Parses configured comparator passthrough blocks and refreshes loaded comparators on rule changes. */
public final class ComparatorThroughBlocks {
    private static volatile Set<Block> configuredBlocks = Set.of();

    private ComparatorThroughBlocks() {}

    public static boolean matches(BlockState state) {
        return configuredBlocks.contains(state.getBlock());
    }

    public static String validate(String raw) {
        String value = stripQuotes(raw);
        if ("false".equals(value)) return value;
        if (value.length() < 2 || value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']') {
            throw new IllegalArgumentException(
                    "comparatorThroughBlocks must be false or a block list such as [chain,piston]");
        }
        Set<Block> parsed = parseBlocks(value.substring(1, value.length() - 1));
        if (parsed.isEmpty()) throw new IllegalArgumentException("comparatorThroughBlocks cannot be an empty block list");
        return parsed.stream()
                .map(BuiltInRegistries.BLOCK::getKey)
                .map(ResourceLocation::toString)
                .sorted()
                .collect(Collectors.joining(",", "[", "]"));
    }

    public static void setConfiguredBlocks(String value) {
        if ("false".equals(value)) {
            configuredBlocks = Set.of();
            return;
        }
        configuredBlocks = Set.copyOf(parseBlocks(value.substring(1, value.length() - 1)));
    }

    public static void refreshLoadedComparators(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            ChunkMapLoadedChunksAccessor chunks = (ChunkMapLoadedChunksAccessor) level.getChunkSource().chunkMap;
            Set<String> seen = new LinkedHashSet<>();
            for (ChunkHolder holder :
                    //#if MC >= 1.21.10
                    //$$ chunks.carpetFga$getVisibleChunkMap().values()) {
                    //#else
                    chunks.carpetFga$getLoadedChunks()) {
                    //#endif
                LevelChunk chunk = holder.getTickingChunk();
                //#if MC >= 1.20.5
                if (chunk == null) chunk = holder.getChunkToSend();
                //#endif
                if (chunk == null || !seen.add(chunk.getPos().toString())) continue;
                refreshChunk(level, chunk);
            }
        }
    }

    private static void refreshChunk(ServerLevel level, LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = level.getMinBuildHeight();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(state -> state.is(Blocks.COMPARATOR))) continue;
            int baseY = minY + sectionIndex * 16;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockPos pos = new BlockPos(minX + x, baseY + y, minZ + z);
                if (level.getBlockState(pos).is(Blocks.COMPARATOR)) {
                    //#if MC >= 1.21.2
                    //$$ level.neighborChanged(pos, Blocks.AIR, Orientation.of(
                    //$$         Direction.UP, Direction.NORTH, Orientation.SideBias.LEFT));
                    //#else
                    level.neighborChanged(pos, Blocks.AIR, pos);
                    //#endif
                }
            }
        }
    }

    private static Set<Block> parseBlocks(String body) {
        Set<Block> result = new LinkedHashSet<>();
        for (String rawEntry : body.split(",", -1)) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) throw new IllegalArgumentException("comparatorThroughBlocks cannot contain an empty block id");
            ResourceLocation id = ResourceLocation.tryParse(entry.contains(":") ? entry : "minecraft:" + entry);
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                throw new IllegalArgumentException("unknown block id: " + entry);
            }
            Block block =
                    //#if MC >= 1.21.2
                    //$$ BuiltInRegistries.BLOCK.getValue(id);
                    //#else
                    BuiltInRegistries.BLOCK.get(id);
                    //#endif
            if (!result.add(block)) throw new IllegalArgumentException("duplicate block id: " + id);
        }
        return result;
    }

    private static String stripQuotes(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1).trim();
            }
        }
        return value.toLowerCase(Locale.ROOT).equals("false") ? "false" : value;
    }
}
//#endif
