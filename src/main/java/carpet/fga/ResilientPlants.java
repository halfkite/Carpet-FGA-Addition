//#if MC >= 1.21 && MC <= 26.2
package carpet.fga;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class ResilientPlants {
    private static final TagKey<Block> CANDIDATE_TAG = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("carpet-fga-addition", "resilient_plant_candidates"));

    private static final Set<String> VANILLA_NON_BUSH_CANDIDATES = Set.of(
            "minecraft:bamboo",
            "minecraft:cactus",
            "minecraft:chorus_flower",
            "minecraft:chorus_plant",
            "minecraft:glow_lichen",
            "minecraft:hanging_roots",
            "minecraft:kelp",
            "minecraft:kelp_plant",
            "minecraft:lily_pad",
            "minecraft:sea_pickle",
            "minecraft:seagrass",
            "minecraft:sugar_cane",
            "minecraft:twisting_vines",
            "minecraft:twisting_vines_plant",
            "minecraft:vine",
            "minecraft:weeping_vines",
            "minecraft:weeping_vines_plant");

    private static volatile Set<Block> configuredBlocks = Set.of();

    private ResilientPlants() {
    }

    public static boolean matches(BlockState state) {
        String mode = FGASettings.resilientPlants;
        if ("false".equalsIgnoreCase(mode)) return false;
        Block block = state.getBlock();
        if ("true".equalsIgnoreCase(mode)) return block instanceof BushBlock;
        return configuredBlocks.contains(block);
    }

    public static String validate(String raw) {
        String value = stripQuotes(raw);
        if (value.equalsIgnoreCase("false")) return "false";
        if (value.equalsIgnoreCase("true")) return "true";
        if (value.equals("[]")) return value;
        if (value.length() < 2 || value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']') {
            throw new IllegalArgumentException(
                    "resilientPlants must be false, true, or a block list such as [flower,cactus]");
        }

        Set<Block> parsed = parseBlocks(value.substring(1, value.length() - 1));
        return parsed.stream()
                .map(BuiltInRegistries.BLOCK::getKey)
                .map(ResourceLocation::toString)
                .sorted()
                .collect(Collectors.joining(",", "[", "]"));
    }

    public static void setConfiguredBlocks(String value) {
        if ("false".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "[]".equals(value)) {
            configuredBlocks = Set.of();
            return;
        }
        configuredBlocks = Set.copyOf(parseBlocks(value.substring(1, value.length() - 1)));
    }

    private static Set<Block> parseBlocks(String body) {
        Set<Block> result = new LinkedHashSet<>();
        if (body.trim().isEmpty()) return result;

        for (String rawEntry : body.split(",", -1)) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                throw new IllegalArgumentException("resilientPlants cannot contain an empty block id");
            }
            ResourceLocation id = ResourceLocation.tryParse(
                    entry.contains(":") ? entry : "minecraft:" + entry);
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                throw new IllegalArgumentException("unknown block id: " + entry);
            }
            Block block =
                    //#if MC >= 1.21.2
                    //$$ BuiltInRegistries.BLOCK.getValue(id);
                    //#else
                    BuiltInRegistries.BLOCK.get(id);
                    //#endif
            if (!isCandidate(block, id)) {
                throw new IllegalArgumentException("block is not a supported plant candidate: " + id);
            }
            result.add(block);
        }
        return result;
    }

    private static boolean isCandidate(Block block, ResourceLocation id) {
        return block instanceof BushBlock
                || VANILLA_NON_BUSH_CANDIDATES.contains(id.toString())
                || block.defaultBlockState().is(CANDIDATE_TAG);
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
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals("false") || lower.equals("true") ? lower : value;
    }
}
//#endif
