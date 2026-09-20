//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class LightSourceStonecuttingRecipes {
    private static final String LIGHT_BLOCK_RECIPE_SUFFIX = "_from_light_block_stonecutting";
    private static final String LIGHT_SOURCE_RECIPE_SUFFIX = "_from_light_source_stonecutting";

    private LightSourceStonecuttingRecipes() {
    }

    public static boolean isRecipe(ResourceLocation id) {
        return id != null
                && "carpet-fga-addition".equals(id.getNamespace())
                && (id.getPath().endsWith(LIGHT_BLOCK_RECIPE_SUFFIX)
                    || id.getPath().endsWith(LIGHT_SOURCE_RECIPE_SUFFIX));
    }

    /** Returns a stable menu sort key with the brightest level first. */
    public static int menuSortOrder(Object recipe) {
        if (!(recipe instanceof RecipeHolder<?> holder)) return Integer.MAX_VALUE;
        String path =
                //#if MC >= 1.21.3
                //$$ holder.id().location().getPath();
                //#else
                holder.id().getPath();
                //#endif
        int prefix = path.indexOf("light_level_");
        if (prefix < 0) return Integer.MAX_VALUE;
        int orderEnd = path.indexOf('_', prefix + "light_level_".length());
        if (orderEnd < 0) return Integer.MAX_VALUE;
        int levelStart = orderEnd + 1;
        int levelEnd = path.indexOf("_from_", levelStart);
        if (levelStart <= 0 || levelEnd <= levelStart) return Integer.MAX_VALUE;
        try {
            return 15 - Integer.parseInt(path.substring(levelStart, levelEnd));
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }
}
//#endif
