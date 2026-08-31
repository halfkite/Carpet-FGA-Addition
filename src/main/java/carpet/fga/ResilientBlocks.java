//#if MC >= 1.21
package carpet.fga;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class ResilientBlocks {
    private static volatile Set<Block> configuredBlocks = Set.of();

    private ResilientBlocks() {
    }

    public static boolean matches(BlockState state) {
        if ("false".equalsIgnoreCase(FGASettings.resilientBlocks)) return false;
        return configuredBlocks.contains(state.getBlock());
    }

    public static String validate(String raw) {
        String value = stripQuotes(raw);
        if (value.equalsIgnoreCase("false")) return "false";
        if (value.equals("[]")) return value;
        if (value.length() < 2 || value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']') {
            throw new IllegalArgumentException(
                    "resilientBlocks must be false or a block list such as [sand,scaffolding]");
        }

        Set<Block> parsed = parseBlocks(value.substring(1, value.length() - 1));
        return parsed.stream()
                .map(BuiltInRegistries.BLOCK::getKey)
                .map(ResourceLocation::toString)
                .sorted()
                .collect(Collectors.joining(",", "[", "]"));
    }

    public static void setConfiguredBlocks(String value) {
        if ("false".equalsIgnoreCase(value) || "[]".equals(value)) {
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
                throw new IllegalArgumentException("resilientBlocks cannot contain an empty block id");
            }
            ResourceLocation id = ResourceLocation.tryParse(
                    entry.contains(":") ? entry : "minecraft:" + entry);
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                throw new IllegalArgumentException("unknown block id: " + entry);
            }
            //#if MC >= 1.21.2
            //$$ Block block = BuiltInRegistries.BLOCK.getValue(id);
            //#else
            Block block = BuiltInRegistries.BLOCK.get(id);
            //#endif
            result.add(block);
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
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals("false") ? lower : value;
    }
}
//#endif
