//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.food.FoodProperties;
//#if MC >= 1.20.2
import net.minecraft.world.item.crafting.RecipeHolder;
//#endif

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EnchantedGoldenCarrotManager {
    private static final String RECIPE_NAMESPACE = "carpet-fga-addition";
    private static final String RECIPE_FROM_CARROT = "enchanted_golden_carrot_from_carrot";
    private static final String RECIPE_FROM_GOLDEN_CARROT = "enchanted_golden_carrot_from_golden_carrot";
    private static final String MARKER_ROOT = "carpet_fga_addition";
    private static final String MARKER = "enchanted_golden_carrot";
    private static final Set<ResourceLocation> RECIPE_IDS = buildRecipeIds();

    private EnchantedGoldenCarrotManager() {}

    private static Set<ResourceLocation> buildRecipeIds() {
        Set<ResourceLocation> ids = new HashSet<>();
        ids.add(ResourceLocation.fromNamespaceAndPath(RECIPE_NAMESPACE, RECIPE_FROM_CARROT));
        ids.add(ResourceLocation.fromNamespaceAndPath(RECIPE_NAMESPACE, RECIPE_FROM_GOLDEN_CARROT));
        return Set.copyOf(ids);
    }

    public static boolean isRecipe(Object recipe) {
        ResourceLocation id = recipeId(recipe);
        return id != null && RECIPE_IDS.contains(id);
    }

    public static boolean isDisabledRecipe(Object recipe) {
        return isRecipe(recipe) && !FGASettings.enchantedGoldenCarrot;
    }

    public static boolean blockResultTake(ItemStack stack) {
        return isEnchantedGoldenCarrot(stack) && !FGASettings.enchantedGoldenCarrot;
    }

    public static boolean isEnchantedGoldenCarrot(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.GOLDEN_CARROT) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        CompoundTag root = data.copyTag();
        //#if MC < 1.21.5
        if (!root.contains(MARKER_ROOT, CompoundTag.TAG_COMPOUND)) return false;
        return root.getCompound(MARKER_ROOT).getBoolean(MARKER);
        //#else
        //$$ if (!root.contains(MARKER_ROOT)) return false;
        //$$ return root.getCompound(MARKER_ROOT).flatMap(value -> value.getBoolean(MARKER)).orElse(false);
        //#endif
    }

    public static void filterCraftingResult(CraftingContainer crafting, ResultContainer result,
                                             //#if MC >= 1.20.2
                                             RecipeHolder<?> recipe
                                             //#else
                                             //$$ Recipe<?> recipe
                                             //#endif
    ) {
        if (!isRecipe(recipe) && !isEnchantedGoldenCarrot(result.getItem(0))) return;
        boolean valid = FGASettings.enchantedGoldenCarrot;
        if (valid && isGoldenCarrotVariant(recipe)) {
            for (int slot = 0; slot < crafting.getContainerSize(); slot++) {
                if (isEnchantedGoldenCarrot(crafting.getItem(slot))) {
                    valid = false;
                    break;
                }
            }
        }
        if (!valid) {
            result.setItem(0, ItemStack.EMPTY);
            result.setRecipeUsed(null);
        }
    }

    private static boolean isGoldenCarrotVariant(Object recipe) {
        ResourceLocation id = recipeId(recipe);
        return id != null && id.getNamespace().equals(RECIPE_NAMESPACE)
                && id.getPath().equals(RECIPE_FROM_GOLDEN_CARROT);
    }

    public static boolean blockFoodUse(ItemStack stack, Player player) {
        return isEnchantedGoldenCarrot(stack)
                && !FGASettings.enchantedGoldenCarrot
                && player.getFoodData().getFoodLevel() >= 20;
    }

    public static FoodProperties foodPropertiesForUse(ItemStack stack, FoodProperties original) {
        if (FGASettings.enchantedGoldenCarrot || !isEnchantedGoldenCarrot(stack)) return original;
        FoodProperties normal = Items.GOLDEN_CARROT.getDefaultInstance().get(DataComponents.FOOD);
        return normal == null ? original : normal;
    }

    public static boolean isRecipeId(ResourceLocation id) {
        return id != null && RECIPE_IDS.contains(id);
    }

    public static List<ResourceLocation> recipeIds() {
        return List.copyOf(RECIPE_IDS);
    }

    private static ResourceLocation recipeId(Object recipe) {
        if (recipe == null) return null;
        //#if MC >= 1.20.2
        //#if MC < 1.21.3
        return recipe instanceof RecipeHolder<?> holder ? holder.id() : null;
        //#else
        //#if MC < 26.3
        //$$ return recipe instanceof RecipeHolder<?> holder ? holder.id().location() : null;
        //#else
        //$$ return recipe instanceof RecipeHolder<?> holder ? holder.id().identifier() : null;
        //#endif
        //#endif
        //#else
        //$$ return recipe instanceof Recipe<?> value ? value.getId() : null;
        //#endif
    }

}
//#endif
