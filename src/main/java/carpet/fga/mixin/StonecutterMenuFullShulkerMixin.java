//#if MC >= 1.21
package carpet.fga.mixin;

import carpet.fga.FullShulkerBoxCraftingManager;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
//#if MC >= 1.21.3
//$$ import net.minecraft.world.item.crafting.SelectableRecipe;
//#endif
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@Mixin(StonecutterMenu.class)
public abstract class StonecutterMenuFullShulkerMixin {
    /** Content item each menu's current recipe list was built from; null means vanilla behavior. */
    private static final Map<StonecutterMenu, ItemStack> LIST_CONTENT = new WeakHashMap<>();

    @Shadow @Final private DataSlot selectedRecipeIndex;
    @Shadow @Final private net.minecraft.world.level.Level level;
    @Shadow private ItemStack input;
    //#if MC == 1.21.1
    @Shadow private List<RecipeHolder<StonecutterRecipe>> recipes;

    @Shadow
    protected abstract void setupRecipeList(Container container, ItemStack stack);
    //#else
    //$$ @Shadow private SelectableRecipe.SingleInputSet<StonecutterRecipe> recipesForInput;
    //$$
    //$$ @Shadow
    //$$ private void setupRecipeList(ItemStack stack) {
    //$$     throw new AssertionError();
    //$$ }
    //#endif

    @Inject(method = "setupRecipeList", at = @At("HEAD"), cancellable = true)
    //#if MC == 1.21.1
    private void carpetFga$fullBoxRecipeList(Container ignoredContainer, ItemStack stack, CallbackInfo ci) {
    //#else
    //$$ private void carpetFga$fullBoxRecipeList(ItemStack stack, CallbackInfo ci) {
    //#endif
        StonecutterMenu menu = (StonecutterMenu) (Object) this;
        Container container = menu.container;
        FullShulkerBoxCraftingManager.registerStonecutterMenu(menu);
        ItemStack content = FullShulkerBoxCraftingManager.stonecutterBoxContent(stack);
        if (content.isEmpty()) {
            LIST_CONTENT.remove(menu);
            return;
        }
        //#if MC == 1.21.1
        List<RecipeHolder<StonecutterRecipe>> contentRecipes =
                FullShulkerBoxCraftingManager.stonecutterRecipesForContent(this.level, content);
        this.recipes = new ArrayList<>(contentRecipes);
        //#else
        //$$ this.recipesForInput =
        //$$         FullShulkerBoxCraftingManager.stonecutterRecipesForContent(this.level, content);
        //#endif
        this.selectedRecipeIndex.set(-1);
        menu.getSlot(1).set(ItemStack.EMPTY);
        LIST_CONTENT.put(menu, content);
        ci.cancel();
    }

    @Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true)
    private void carpetFga$fullBoxResultSlot(CallbackInfo ci) {
        StonecutterMenu menu = (StonecutterMenu) (Object) this;
        FullShulkerBoxCraftingManager.registerStonecutterMenu(menu);
        ItemStack stack = menu.container.getItem(0);
        ItemStack content = FullShulkerBoxCraftingManager.stonecutterBoxContent(stack);
        if (content.isEmpty()) {
            LIST_CONTENT.remove(menu);
            return;
        }
        Slot resultSlot = menu.getSlot(1);
        int index = this.selectedRecipeIndex.get();
        RecipeHolder<StonecutterRecipe> recipe = carpetFga$selectedRecipe(index);
        FullShulkerBoxCraftingManager.StonecutterPlan plan = recipe == null
                ? null
                : FullShulkerBoxCraftingManager.analyzeStonecutter(this.level, stack, recipe);
        if (plan == null) {
            resultSlot.set(ItemStack.EMPTY);
        } else {
            resultSlot.set(plan.previewBox());
            if (resultSlot.container instanceof ResultContainer resultContainer) {
                resultContainer.setRecipeUsed(recipe);
            }
        }
        ci.cancel();
    }

    private RecipeHolder<StonecutterRecipe> carpetFga$selectedRecipe(int index) {
        //#if MC == 1.21.1
        List<RecipeHolder<StonecutterRecipe>> list = this.recipes;
        return index >= 0 && index < list.size() ? list.get(index) : null;
        //#else
        //$$ List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> entries =
        //$$         this.recipesForInput.entries();
        //$$ if (index < 0 || index >= entries.size()) return null;
        //$$ return entries.get(index).recipe().recipe().orElse(null);
        //#endif
    }

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
    private void carpetFga$fullBoxSlotsChanged(Container container, CallbackInfo ci) {
        StonecutterMenu menu = (StonecutterMenu) (Object) this;
        ItemStack stored = LIST_CONTENT.get(menu);
        if (stored == null) return;
        ItemStack current = container.getItem(0);
        ItemStack content = FullShulkerBoxCraftingManager.stonecutterBoxContent(current);
        if (content.isEmpty() || !ItemStack.matches(stored, content)) {
            LIST_CONTENT.remove(menu);
            this.input = current.copy();
            //#if MC == 1.21.1
            this.setupRecipeList(container, current);
            //#else
            //$$ this.setupRecipeList(current);
            //#endif
            ci.cancel();
        }
    }
}
//#endif
