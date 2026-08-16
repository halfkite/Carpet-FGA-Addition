//#if MC >= 1.21 && MC <= 26.2
package carpet.fga;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.RecipeHolder;
//#if MC >= 1.21.3
//$$ import net.minecraft.world.item.crafting.SelectableRecipe;
//$$ import net.minecraft.world.item.crafting.StonecutterRecipe;
//#endif

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class WoodStonecuttingRecipes {
    private static final String RECIPE_SUFFIX = "_from_wood_stonecutting";
    private static final Map<Slot, StonecutterMenu> MENUS_BY_INPUT_SLOT =
            Collections.synchronizedMap(new WeakHashMap<>());

    private WoodStonecuttingRecipes() {
    }

    public static boolean isWoodRecipe(ResourceLocation id) {
        return "carpet-fga-addition".equals(id.getNamespace())
                && id.getPath().endsWith(RECIPE_SUFFIX);
    }

    public static boolean isTwoInputRecipe(StonecutterMenu menu) {
        RecipeHolder<?> recipe = selectedRecipe(menu);
        return recipe != null && requiredInputCount(recipe) > 1;
    }

    public static void registerInputSlot(Slot inputSlot, StonecutterMenu menu) {
        MENUS_BY_INPUT_SLOT.put(inputSlot, menu);
    }

    public static boolean isTwoInputRecipe(Slot inputSlot) {
        StonecutterMenu menu = MENUS_BY_INPUT_SLOT.get(inputSlot);
        return menu != null && isTwoInputRecipe(menu);
    }

    public static boolean isTwoInputRecipe(RecipeHolder<?> recipe) {
        return requiredInputCount(recipe) > 1;
    }

    public static int requiredInputCount(StonecutterMenu menu) {
        RecipeHolder<?> recipe = selectedRecipe(menu);
        return recipe == null ? 1 : requiredInputCount(recipe);
    }

    public static int requiredInputCount(Slot inputSlot) {
        StonecutterMenu menu = MENUS_BY_INPUT_SLOT.get(inputSlot);
        return menu == null ? 1 : requiredInputCount(menu);
    }

    public static int requiredInputCount(RecipeHolder<?> recipe) {
        ResourceLocation id =
                //#if MC >= 1.21.3
                //$$ recipe.id().location();
                //#else
                recipe.id();
                //#endif
        if (!isWoodRecipe(id)) return 1;
        String path = id.getPath();
        if (path.startsWith("bamboo_to_")) return 9;
        boolean bambooBlockInput = path.startsWith("bamboo_block_to_")
                || path.startsWith("stripped_bamboo_block_to_");
        if (bambooBlockInput && (path.contains("_to_bamboo_fence_gate_")
                || path.contains("_to_crafting_table_")
                || path.contains("_to_bowl_")
                || path.contains("_to_bamboo_raft_"))) return 2;
        if (path.contains("_barrel_from_wood_stonecutting")
                || path.contains("_chest_from_wood_stonecutting")) return bambooBlockInput ? 4 : 2;
        return 1;
    }

    private static RecipeHolder<?> selectedRecipe(StonecutterMenu menu) {
        int index = menu.getSelectedRecipeIndex();
        //#if MC >= 1.21.3
        //$$ List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> recipes = menu.getVisibleRecipes().entries();
        //$$ return index >= 0 && index < recipes.size() ? recipes.get(index).recipe().recipe().orElse(null) : null;
        //#else
        List<RecipeHolder<?>> recipes = rawRecipes(menu.getRecipes());
        return index >= 0 && index < recipes.size() ? recipes.get(index) : null;
        //#endif
    }

    @SuppressWarnings("unchecked")
    private static List<RecipeHolder<?>> rawRecipes(List<?> recipes) {
        return (List<RecipeHolder<?>>) (List<?>) recipes;
    }
}
//#endif
