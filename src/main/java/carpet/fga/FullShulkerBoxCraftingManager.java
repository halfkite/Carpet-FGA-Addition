//#if MC >= 1.16.5 && MC <= 26.2
package carpet.fga;

//#if MC == 1.21.1
import carpet.fga.mixin.StonecutterMenuAccessor;
//#endif
import net.minecraft.core.NonNullList;
//#if MC < 1.20.5
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
//#endif
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
//#if MC >= 1.21.3
//$$ import net.minecraft.server.level.ServerLevel;
//$$ import net.minecraft.world.item.crafting.SelectableRecipe;
//#endif
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
//#if MC < 1.20.1
import net.minecraft.world.inventory.MenuType;
//#elseif MC < 1.21
//$$ import net.minecraft.world.inventory.TransientCraftingContainer;
//#endif
import net.minecraft.world.inventory.ResultContainer;
//#if MC >= 1.21
import net.minecraft.world.inventory.Slot;
//#endif
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
//#if MC >= 1.20.5
import net.minecraft.world.item.component.ItemContainerContents;
//#endif
//#if MC >= 1.21
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.SingleRecipeInput;
//#endif
import net.minecraft.world.item.crafting.CraftingRecipe;
//#if MC >= 1.20.4
import net.minecraft.world.item.crafting.RecipeHolder;
//#endif
import net.minecraft.world.item.crafting.RecipeType;
//#if MC >= 1.21.3
//$$ import net.minecraft.world.item.crafting.SelectableRecipe;
//#endif
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
//#if MC >= 1.20.5
import net.minecraft.core.component.DataComponents;
//#endif

import java.util.ArrayList;
//#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.2
import java.util.ArrayDeque;
import java.util.Deque;
//#endif
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
//#if MC >= 1.21
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//#endif

/** Server-side full-shulker crafting backed by ordinary crafting recipes. */
public final class FullShulkerBoxCraftingManager {
    private static final int VANILLA_SHULKER_SIZE = 27;
    //#if MC >= 1.20.1 && MC <= 26.2
    private static final int AMS_LARGE_SHULKER_SIZE = 54;
    //#endif
    private static final int MAX_OUTPUT_BOXES = 4096;
    private static final Map<CraftingContainer, Plan> PLANS = new WeakHashMap<>();
    private static final Map<CraftingContainer, String> LAST_NOTICE = new WeakHashMap<>();
    private static final Set<CraftingContainer> PROCESSING = new HashSet<>();
//#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.2
    private static final int MAIN_INVENTORY_SIZE = 36;
    private static final ThreadLocal<Deque<QuickResultContext>> QUICK_RESULT_CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);
//#endif
//#if MC >= 1.21
    private static String lastRuleValue = "false";
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/full-shulker-stonecutter");
    private static final Map<Slot, StonecutterMenu> STONECUTTER_MENUS = new WeakHashMap<>();
    private static final Map<Slot, StonecutterMenu> STONECUTTER_INPUT_MENUS = new WeakHashMap<>();
    private static final Map<StonecutterMenu, PreparedStonecutterPlan> PREPARED_STONECUTTER =
            new WeakHashMap<>();
    private static long nextStonecutterToken;
    //#else
    //$$ private static boolean lastRuleValue;
    //#endif

    //#if MC >= 1.21
    private static boolean ruleEnabled() {
        return !"false".equals(FGASettings.fullShulkerBoxCrafting);
    }

    private static boolean only64Mode() {
        return "only64".equals(FGASettings.fullShulkerBoxCrafting);
    }
    //#endif

    /** Returns the authoritative rule state used by click wrappers and result updates. */
    public static boolean enabled() {
        //#if MC >= 1.21
        return ruleEnabled();
        //#else
        //$$ return FGASettings.fullShulkerBoxCrafting;
        //#endif
    }

    private FullShulkerBoxCraftingManager() {
    }

    public static void updateResult(AbstractContainerMenu menu, Level level, Player player,
                                    CraftingContainer crafting, ResultContainer result) {
        if (PROCESSING.contains(crafting)) return;
        //#if MC >= 1.21
        if (FGACompat.isClientSide(level) || !ruleEnabled() || !result.getItem(0).isEmpty()) {
            //#else
            //$$ if (FGACompat.isClientSide(level) || !FGASettings.fullShulkerBoxCrafting || !result.getItem(0).isEmpty()) {
            //#endif
            clear(crafting);
            return;
        }

        Analysis analysis = analyze(crafting, player);
        if (analysis.plan() == null) {
            PLANS.remove(crafting);
            notifyFailure(crafting, player, analysis);
            return;
        }

        Plan plan = analysis.plan();
        PLANS.put(crafting, plan);
        LAST_NOTICE.remove(crafting);
        ItemStack output = plan.outputBoxes().get(0).copy();
        result.setItem(0, output);
        menu.broadcastChanges();
    }

    public static boolean isCustomResult(CraftingContainer crafting) {
        return PLANS.containsKey(crafting);
    }

    public static boolean mayTake(CraftingContainer crafting, Player player) {
        //#if MC >= 1.21
        if (!ruleEnabled() || !PLANS.containsKey(crafting)) return false;
        //#else
        //$$ if (!FGASettings.fullShulkerBoxCrafting || !PLANS.containsKey(crafting)) return false;
        //#endif
        Analysis analysis = analyze(crafting, player);
        if (analysis.plan() == null) {
            notifyFailure(crafting, player, analysis);
            return false;
        }
        PLANS.put(crafting, analysis.plan());
        LAST_NOTICE.remove(crafting);
        return true;
    }

//#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.2
    public static void beginQuickResultClick(Player player) {
        QUICK_RESULT_CONTEXT.get().push(QuickResultContext.capture(player));
    }

    public static void endQuickResultClick() {
        Deque<QuickResultContext> contexts = QUICK_RESULT_CONTEXT.get();
        if (!contexts.isEmpty()) {
            QuickResultContext context = contexts.pop();
            try {
                context.applyBalancedRestock();
            } finally {
                if (contexts.isEmpty()) QUICK_RESULT_CONTEXT.remove();
            }
            return;
        }
        QUICK_RESULT_CONTEXT.remove();
    }

//#endif

    /** Returns true when vanilla ResultSlot consumption must be cancelled. */
    public static boolean take(CraftingContainer crafting, Player player) {
        Plan plan = PLANS.remove(crafting);
        if (plan == null) return false;

        Inventory inventory = FGACompat.inventory(player);
        PROCESSING.add(crafting);
        try {
//#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.2
            recordRestockTargets(crafting, player, plan.sourceSlots());
            //#endif
            consumeEmptyBoxes(inventory, plan.consumedEmptyBoxes());
            for (int slot : plan.sourceSlots()) {
                ItemStack source = crafting.getItem(slot);
                source.shrink(1);
                crafting.setItem(slot, source.isEmpty() ? ItemStack.EMPTY : source);
            }

            // The first output box has already been moved by vanilla. This remains true when
            // shift-click passes an empty, already-decremented stack to ResultSlot.onTake.
            for (int i = 1; i < plan.outputBoxes().size(); i++) {
                giveOrDrop(player, plan.outputBoxes().get(i).copy());
            }
            for (ItemStack emptyBox : plan.returnedEmptyBoxes()) {
                giveOrDrop(player, emptyBox.copy());
            }
        } finally {
            PROCESSING.remove(crafting);
            clear(crafting);
        }

        inventory.setChanged();
        player.containerMenu.slotsChanged(crafting);
        player.containerMenu.broadcastChanges();
        return true;
    }

    public static void refresh(MinecraftServer server) {
        PLANS.clear();
        LAST_NOTICE.clear();
        //#if MC >= 1.21
        PREPARED_STONECUTTER.clear();
        //#endif
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.containerMenu.slotsChanged(FGACompat.inventory(player));
            player.containerMenu.broadcastChanges();
        }
    }

    public static void tick(MinecraftServer server) {
        //#if MC >= 1.21
        if (lastRuleValue.equals(FGASettings.fullShulkerBoxCrafting)) return;
        lastRuleValue = FGASettings.fullShulkerBoxCrafting;
        //#else
        //$$ if (lastRuleValue == FGASettings.fullShulkerBoxCrafting) return;
        //$$ lastRuleValue = FGASettings.fullShulkerBoxCrafting;
        //#endif
        refresh(server);
    }

    public static void clear() {
        PLANS.clear();
        LAST_NOTICE.clear();
        PROCESSING.clear();
        //#if MC >= 1.21
        PREPARED_STONECUTTER.clear();
        STONECUTTER_MENUS.clear();
        STONECUTTER_INPUT_MENUS.clear();
        nextStonecutterToken = 0L;
        //#endif
        //#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.2
        QUICK_RESULT_CONTEXT.remove();
        //#endif
        //#if MC >= 1.21
        lastRuleValue = "false";
        //#else
        //$$ lastRuleValue = false;
        //#endif
    }

    private static Analysis analyze(CraftingContainer crafting, Player player) {
        int shulkerSize = shulkerSize();
        List<Integer> sourceSlots = new ArrayList<>();
        List<ItemStack> sourceBoxes = new ArrayList<>();
        List<ItemStack> ingredients = new ArrayList<>();
        //#if MC >= 1.21
        long expectedCrafts = -1L;
        //#else
        //$$ long craftsPerBox = -1L;
        //#endif
        NonNullList<ItemStack> recipeItems = NonNullList.withSize(
                crafting.getWidth() * crafting.getHeight(), ItemStack.EMPTY);

        for (int slot = 0; slot < crafting.getContainerSize(); slot++) {
            ItemStack box = crafting.getItem(slot);
            if (box.isEmpty()) continue;
            //#if MC >= 1.21
            long[] contentTotal = new long[1];
            ItemStack content = singleContent(box, shulkerSize, contentTotal);
            if (content.isEmpty()) {
                return isShulkerBox(box) ? Analysis.failure(Failure.INVALID_INPUT_BOX) : Analysis.NONE;
            }
            if (content.getMaxStackSize() <= 1) return Analysis.failure(Failure.UNSTACKABLE_INPUT);
            long craftsPerBox = contentTotal[0];
            if (expectedCrafts < 0L) expectedCrafts = craftsPerBox;
            else if (expectedCrafts != craftsPerBox) {
                return Analysis.failure(Failure.CONTENT_COUNT_MISMATCH);
            }
            //#else
            //$$ ItemStack content = fullSingleContent(box, shulkerSize);
            //$$ if (content.isEmpty()) {
            //$$     return isShulkerBox(box) ? Analysis.failure(Failure.INVALID_INPUT_BOX) : Analysis.NONE;
            //$$ }
            //$$ if (content.getMaxStackSize() <= 1) return Analysis.failure(Failure.UNSTACKABLE_INPUT);
            //$$ long contentPerBox = (long) FGASettings.effectiveContainerStackLimit(content) * shulkerSize;
            //$$ if (craftsPerBox < 0L) craftsPerBox = contentPerBox;
            //$$ else if (craftsPerBox != contentPerBox) return Analysis.failure(Failure.INPUT_CAPACITY_MISMATCH);
            //#endif
            sourceSlots.add(slot);
            sourceBoxes.add(FGACompat.copyWithCount(box, 1));
            ingredients.add(FGACompat.copyWithCount(content, 1));
            recipeItems.set(slot, FGACompat.copyWithCount(content, 1));
        }
        if (sourceBoxes.isEmpty()) return Analysis.NONE;

        //#if MC >= 1.21
        CraftingInput input = CraftingInput.of(crafting.getWidth(), crafting.getHeight(), recipeItems);
        //#if MC >= 1.21.3
        //$$ Optional<RecipeHolder<CraftingRecipe>> match = ((ServerLevel) FGACompat.level(player)).recipeAccess()
        //$$         .getRecipeFor(RecipeType.CRAFTING, input, FGACompat.level(player));
        //#else
        Optional<RecipeHolder<CraftingRecipe>> match = FGACompat.level(player).getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, FGACompat.level(player));
        //#endif
        if (match.isEmpty()) return Analysis.NONE;
        CraftingRecipe recipe = match.get().value();
        //#else
        //$$ CraftingContainer input = legacyInput(player, crafting.getWidth(), crafting.getHeight(), recipeItems);
        //#if MC >= 1.20.4
        //$$ Optional<RecipeHolder<CraftingRecipe>> match = FGACompat.level(player).getRecipeManager()
        //$$         .getRecipeFor(RecipeType.CRAFTING, input, FGACompat.level(player));
        //$$ if (match.isEmpty()) return Analysis.NONE;
        //$$ CraftingRecipe recipe = match.get().value();
        //#else
        //$$ Optional<CraftingRecipe> match = FGACompat.level(player).getRecipeManager()
        //$$         .getRecipeFor(RecipeType.CRAFTING, input, FGACompat.level(player));
        //$$ if (match.isEmpty()) return Analysis.NONE;
        //$$ CraftingRecipe recipe = match.get();
        //#endif
        //#endif
        ItemStack recipeOutput =
                //#if MC >= 26.0
                //$$ recipe.assemble(input);
                //#elseif MC >= 1.19.4
                recipe.assemble(input, FGACompat.level(player).registryAccess());
                //#else
                //$$ recipe.assemble(input);
                //#endif
        if (recipeOutput.isEmpty()
                //#if MC >= 1.20.5
                || !recipeOutput.isItemEnabled(FGACompat.level(player).enabledFeatures())
                //#endif
                || !canFitInsideContainer(recipeOutput)) return Analysis.NONE;
        if (recipeOutput.getMaxStackSize() <= 1) return Analysis.failure(Failure.UNSTACKABLE_OUTPUT);

        //#if MC >= 1.21
        boolean allowPartial = !only64Mode();
        int outputStackLimit = only64Mode()
                ? recipeOutput.getMaxStackSize()
                : FGASettings.effectiveContainerStackLimit(recipeOutput);
        //#else
        //$$ boolean allowPartial = false;
        //$$ int outputStackLimit = FGASettings.effectiveContainerStackLimit(recipeOutput);
        //#endif
        long outputCapacity = (long) outputStackLimit * shulkerSize;
        //#if MC >= 1.21
        long totalOutputItems = expectedCrafts * recipeOutput.getCount();
        //#else
        //$$ long totalOutputItems = craftsPerBox * recipeOutput.getCount();
        //#endif
        if (outputCapacity <= 0 || (!allowPartial && totalOutputItems % outputCapacity != 0)) {
            return Analysis.failure(Failure.NON_WHOLE_OUTPUT);
        }
        long mainOutputBoxCount = allowPartial
                ? (totalOutputItems + outputCapacity - 1) / outputCapacity
                : totalOutputItems / outputCapacity;
        if (mainOutputBoxCount <= 0) return Analysis.failure(Failure.NON_WHOLE_OUTPUT);

        List<OutputGroup> groups = new ArrayList<>();
        groups.add(new OutputGroup(FGACompat.copyWithCount(recipeOutput, 1), outputStackLimit,
                mainOutputBoxCount, totalOutputItems));
        NonNullList<ItemStack> remainingItems = recipe.getRemainingItems(input);
        List<RemainderTotal> remainderTotals = new ArrayList<>();
        for (ItemStack remainder : remainingItems) {
            if (remainder.isEmpty()) continue;
            if (remainder.getMaxStackSize() <= 1 || !canFitInsideContainer(remainder)) {
                return Analysis.failure(Failure.UNSTACKABLE_REMAINDER);
            }
            RemainderTotal total = remainderTotals.stream()
                    .filter(candidate -> FGACompat.isSameItemSameTags(candidate.item(), remainder))
                    .findFirst().orElse(null);
            //#if MC >= 1.21
            long amount = expectedCrafts * remainder.getCount();
            //#else
            //$$ long amount = craftsPerBox * remainder.getCount();
            //#endif
            if (total == null) remainderTotals.add(new RemainderTotal(FGACompat.copyWithCount(remainder, 1), amount));
            else total.add(amount);
        }
        for (RemainderTotal remainder : remainderTotals) {
            //#if MC >= 1.21
            int stackLimit = only64Mode()
                    ? remainder.item().getMaxStackSize()
                    : FGASettings.effectiveContainerStackLimit(remainder.item());
            //#else
            //$$ int stackLimit = FGASettings.effectiveContainerStackLimit(remainder.item());
            //#endif
            long capacity = (long) stackLimit * shulkerSize;
            if (capacity <= 0 || (!allowPartial && remainder.count() % capacity != 0)) {
                return Analysis.failure(Failure.NON_WHOLE_REMAINDER);
            }
            long boxCount = allowPartial
                    ? (remainder.count() + capacity - 1) / capacity
                    : remainder.count() / capacity;
            groups.add(new OutputGroup(remainder.item(), stackLimit, boxCount, remainder.count()));
        }

        long totalBoxCountLong = groups.stream().mapToLong(OutputGroup::boxCount).sum();
        if (totalBoxCountLong <= 0 || totalBoxCountLong > MAX_OUTPUT_BOXES) {
            return Analysis.failure(Failure.TOO_MANY_OUTPUTS);
        }
        int totalBoxCount = (int) totalBoxCountLong;
        if (totalBoxCount == 1 && sourceBoxes.size() == 1 && groups.size() == 1
                && FGACompat.isSameItemSameTags(ingredients.get(0), recipeOutput)) return Analysis.NONE;

        int extraNeeded = Math.max(0, totalBoxCount - sourceBoxes.size());
        List<InventoryBox> emptyBoxes = findEmptyBoxes(FGACompat.inventory(player), extraNeeded, shulkerSize);
        if (emptyBoxes.size() < extraNeeded) {
            return Analysis.failure(Failure.MISSING_EMPTY_BOXES, extraNeeded - emptyBoxes.size());
        }

        List<ItemStack> boxTemplates = new ArrayList<>(sourceBoxes);
        for (InventoryBox emptyBox : emptyBoxes) boxTemplates.add(emptyBox.template());

        List<ItemStack> outputBoxes = new ArrayList<>(totalBoxCount);
        int templateIndex = 0;
        for (OutputGroup group : groups) {
            for (long i = 0; i < group.boxCount(); i++) {
                if (allowPartial && i == group.boxCount() - 1) {
                    long fullItems = (group.boxCount() - 1) * ((long) group.stackLimit() * shulkerSize);
                    long partialItems = group.totalCount() - fullItems;
                    if (partialItems > 0 && partialItems < (long) group.stackLimit() * shulkerSize) {
                        outputBoxes.add(fillPartialBox(boxTemplates.get(templateIndex++), group.item(),
                                group.stackLimit(), shulkerSize, partialItems));
                        continue;
                    }
                }
                outputBoxes.add(fillBox(boxTemplates.get(templateIndex++), group.item(),
                        group.stackLimit(), shulkerSize));
            }
        }

        List<ItemStack> returnedEmptyBoxes = new ArrayList<>();
        for (int i = totalBoxCount; i < sourceBoxes.size(); i++) {
            returnedEmptyBoxes.add(emptyBox(sourceBoxes.get(i)));
        }
        return Analysis.success(new Plan(List.copyOf(sourceSlots),
                List.copyOf(outputBoxes), List.copyOf(returnedEmptyBoxes), List.copyOf(emptyBoxes)));
    }

    //#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.2
    private static void recordRestockTargets(CraftingContainer crafting, Player player,
                                             List<Integer> sourceSlots) {
        Deque<QuickResultContext> contexts = QUICK_RESULT_CONTEXT.get();
        if (contexts.isEmpty()) return;
        QuickResultContext context = contexts.peek();
        if (context == null || context.player() != player) return;
        context.recordTargets(crafting, sourceSlots);
    }

    //#endif

    private static ItemStack fullSingleContent(ItemStack box, int shulkerSize) {
        if (!isShulkerBox(box)) return ItemStack.EMPTY;
        NonNullList<ItemStack> contents = contents(box, shulkerSize);
        ItemStack first = ItemStack.EMPTY;
        for (ItemStack stack : contents) {
            if (stack.isEmpty() || stack.getCount() != FGASettings.effectiveContainerStackLimit(stack)) {
                return ItemStack.EMPTY;
            }
            if (first.isEmpty()) first = stack;
            else if (!FGACompat.isSameItemSameTags(first, stack)) return ItemStack.EMPTY;
        }
        return first.isEmpty() ? ItemStack.EMPTY : FGACompat.copyWithCount(first, 1);
    }

    //#if MC >= 1.21
    /**
     * Mode-aware single-item content check. Returns the content representative (count 1) and writes
     * the total item count into totalOut[0]; EMPTY when the box does not qualify for the current mode.
     */
    private static ItemStack singleContent(ItemStack box, int shulkerSize, long[] totalOut) {
        totalOut[0] = 0L;
        if (!isShulkerBox(box)) return ItemStack.EMPTY;
        boolean only64 = only64Mode();
        NonNullList<ItemStack> contents = contents(box, shulkerSize);
        ItemStack first = ItemStack.EMPTY;
        long total = 0L;
        for (ItemStack stack : contents) {
            if (stack.isEmpty()) continue;
            if (first.isEmpty()) first = stack;
            else if (!FGACompat.isSameItemSameTags(first, stack)) return ItemStack.EMPTY;
            if (only64) {
                if (stack.getCount() != stack.getMaxStackSize()) return ItemStack.EMPTY;
            } else if (stack.getCount() > FGASettings.effectiveContainerStackLimit(stack)) {
                return ItemStack.EMPTY;
            }
            total += stack.getCount();
        }
        totalOut[0] = total;
        return first.isEmpty() ? ItemStack.EMPTY : FGACompat.copyWithCount(first, 1);
    }
    //#endif

    //#if MC >= 1.20.5
    private static ItemStack fillPartialBox(ItemStack template, ItemStack output, int stackLimit,
                                            int shulkerSize, long itemCount) {
        ItemStack box = FGACompat.copyWithCount(template, 1);
        NonNullList<ItemStack> contents = NonNullList.withSize(shulkerSize, ItemStack.EMPTY);
        long remaining = itemCount;
        for (int i = 0; i < shulkerSize && remaining > 0; i++) {
            int count = (int) Math.min(stackLimit, remaining);
            contents.set(i, FGACompat.copyWithCount(output, count));
            remaining -= count;
        }
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        return box;
    }
    //#endif

    //#if MC >= 1.21
    public enum StonecutterPrepareResult { NONE, READY, BLOCKED }

    public record StonecutterCommitResult(boolean intercepted, ItemStack removedInput) {
        private static StonecutterCommitResult passThrough() {
            return new StonecutterCommitResult(false, ItemStack.EMPTY);
        }

        private static StonecutterCommitResult handled(ItemStack removedInput) {
            return new StonecutterCommitResult(true, removedInput);
        }
    }

    /**
     * Recipe list lookup for a full-box input: recipes resolved against the box content item with
     * rule-disabled FGA recipes filtered. The result type follows the vanilla menu field of each
     * version clump ({@code recipes} list on 1.21.1, {@code recipesForInput} set on 1.21.3+).
     */
    //#if MC == 1.21.1
    public static List<RecipeHolder<StonecutterRecipe>> stonecutterRecipesForContent(Level level,
                                                                                     ItemStack content) {
        return level.getRecipeManager()
                .getRecipesFor(RecipeType.STONECUTTING,
                        new SingleRecipeInput(FGACompat.copyWithCount(content, 1)), level)
                .stream()
                .filter(recipe -> !DeepslateStonecuttingRecipes.isDisabledFgaRecipe(recipe))
                .toList();
    }
    //#else
    //$$ public static SelectableRecipe.SingleInputSet<StonecutterRecipe> stonecutterRecipesForContent(
    //$$         Level level, ItemStack content) {
    //$$     return DeepslateStonecuttingRecipes.filter(
    //$$             level.recipeAccess().stonecutterRecipes().selectByInput(FGACompat.copyWithCount(content, 1)));
    //$$ }
    //#endif

    /** Content representative (count 1) when the input qualifies for the current mode, else EMPTY. */
    public static ItemStack stonecutterBoxContent(ItemStack input) {
        if (!ruleEnabled()) return ItemStack.EMPTY;
        return singleContent(input, shulkerSize(), new long[1]);
    }

    private static StonecutterAnalysis analyzeStonecutter(Level level, ItemStack input,
                                                          RecipeHolder<StonecutterRecipe> recipe,
                                                          Inventory inventory,
                                                          boolean allowPreviewPlaceholders) {
        if (!ruleEnabled() || recipe == null || inventory == null) return null;
        int shulkerSize = shulkerSize();
        long[] totalIn = new long[1];
        ItemStack content = singleContent(input, shulkerSize, totalIn);
        if (content.isEmpty() || content.getMaxStackSize() <= 1) return null;
        int required = WoodStonecuttingRecipes.requiredInputCount(recipe);
        if (required <= 0) return null;
        long crafts = totalIn[0] / required;
        if (crafts <= 0) return null;
        long consumedMaterial;
        try {
            consumedMaterial = Math.multiplyExact((long) required, crafts);
        } catch (ArithmeticException exception) {
            return null;
        }
        long remainingMaterial = totalIn[0] - consumedMaterial;
        if (remainingMaterial < 0) return null;

        ItemStack recipeOutput = recipe.value().assemble(new SingleRecipeInput(content)
                //#if MC < 26.0
                , level.registryAccess()
                //#endif
        );
        if (recipeOutput.isEmpty()
                || !recipeOutput.isItemEnabled(level.enabledFeatures())
                || !canFitInsideContainer(recipeOutput)) return null;
        if (recipeOutput.getMaxStackSize() <= 1) return null;

        int outputStackLimit = only64Mode()
                ? recipeOutput.getMaxStackSize()
                : FGASettings.effectiveContainerStackLimit(recipeOutput);
        long outputCapacity = (long) outputStackLimit * shulkerSize;
        long totalOutputItems;
        try {
            totalOutputItems = Math.multiplyExact(crafts, (long) recipeOutput.getCount());
        } catch (ArithmeticException exception) {
            return null;
        }
        if (outputCapacity <= 0 || totalOutputItems <= 0) return null;
        long boxCount;
        if (only64Mode()) {
            if (totalOutputItems % outputCapacity != 0) return null;
            boxCount = totalOutputItems / outputCapacity;
        } else {
            boxCount = (totalOutputItems + outputCapacity - 1) / outputCapacity;
        }
        if (boxCount <= 0 || boxCount > MAX_OUTPUT_BOXES) return null;

        boolean retainsSource = remainingMaterial > 0;
        int outputBoxCount = (int) boxCount;
        int requiredEmptyBoxes = outputBoxCount - (retainsSource ? 0 : 1);
        if (requiredEmptyBoxes < 0) return null;
        List<InventoryBox> emptyBoxes = findEmptyBoxes(inventory, requiredEmptyBoxes, shulkerSize);
        if (emptyBoxes.size() < requiredEmptyBoxes) {
            if (!allowPreviewPlaceholders) return null;
            emptyBoxes = new ArrayList<>(emptyBoxes);
            while (emptyBoxes.size() < requiredEmptyBoxes) {
                emptyBoxes.add(new InventoryBox(-1, FGACompat.copyWithCount(input, 1)));
            }
        }
        StonecutterBoxAllocation.Plan allocation = StonecutterBoxAllocation.allocate(
                outputBoxCount, retainsSource, emptyBoxes.size());
        if (allocation == null || allocation.requiredEmptyBoxes() != emptyBoxes.size()) return null;

        ItemStack sourceBox = FGACompat.copyWithCount(input, 1);
        ItemStack remainderBox = retainsSource
                ? stonecutterRemainder(sourceBox, shulkerSize, consumedMaterial)
                : ItemStack.EMPTY;
        if (retainsSource && remainderBox.isEmpty()) return null;

        List<ItemStack> outputBoxes = new ArrayList<>(outputBoxCount);
        long outputItemsRemaining = totalOutputItems;
        for (StonecutterBoxAllocation.Source source : allocation.outputSources()) {
            ItemStack template = source.kind() == StonecutterBoxAllocation.Kind.SOURCE
                    ? sourceBox
                    : emptyBoxes.get(source.emptyBoxIndex()).template();
            long items = Math.min(outputCapacity, outputItemsRemaining);
            outputBoxes.add(fillPartialBox(template, recipeOutput, outputStackLimit, shulkerSize, items));
            outputItemsRemaining -= items;
        }
        if (outputItemsRemaining != 0L || outputBoxes.size() != outputBoxCount) return null;

        Map<Integer, ItemStack> emptySlotSnapshots = new HashMap<>();
        for (InventoryBox emptyBox : emptyBoxes) {
            if (emptyBox.slot() < 0) continue;
            emptySlotSnapshots.computeIfAbsent(emptyBox.slot(), slot -> inventory.getItem(slot).copy());
        }
        return new StonecutterAnalysis(recipe, required, crafts, consumedMaterial,
                sourceBox, remainderBox, List.copyOf(outputBoxes), List.copyOf(emptyBoxes),
                Map.copyOf(emptySlotSnapshots), allocation.retainsSource(),
                allocation.requiredEmptyBoxes());
    }

    private static ItemStack stonecutterRemainder(ItemStack sourceBox, int shulkerSize,
                                                  long consumedMaterial) {
        NonNullList<ItemStack> remainingContents = contents(sourceBox, shulkerSize);
        long remainingToConsume = consumedMaterial;
        for (int i = 0; i < remainingContents.size() && remainingToConsume > 0; i++) {
            ItemStack stack = remainingContents.get(i);
            if (stack.isEmpty()) continue;
            int taken = (int) Math.min(stack.getCount(), remainingToConsume);
            remainingToConsume -= taken;
            if (taken >= stack.getCount()) remainingContents.set(i, ItemStack.EMPTY);
            else remainingContents.set(i, FGACompat.copyWithCount(stack, stack.getCount() - taken));
        }
        if (remainingToConsume != 0L || remainingContents.stream().allMatch(ItemStack::isEmpty)) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = FGACompat.copyWithCount(sourceBox, 1);
        remainder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(remainingContents));
        return remainder;
    }

    public static void registerStonecutterMenu(StonecutterMenu menu) {
        STONECUTTER_MENUS.put(menu.getSlot(1), menu);
        STONECUTTER_INPUT_MENUS.put(menu.getSlot(0), menu);
    }

    public static void unregisterStonecutterMenu(StonecutterMenu menu) {
        PREPARED_STONECUTTER.remove(menu);
        STONECUTTER_MENUS.remove(menu.getSlot(1));
        STONECUTTER_INPUT_MENUS.remove(menu.getSlot(0));
    }

    public static void clearStonecutterPlan(StonecutterMenu menu) {
        PREPARED_STONECUTTER.remove(menu);
    }

    public static ItemStack stonecutterPreview(StonecutterMenu menu,
                                               RecipeHolder<StonecutterRecipe> recipe) {
        Player player = stonecutterPlayer(menu);
        if (player == null) return ItemStack.EMPTY;
        StonecutterAnalysis analysis = analyzeStonecutter(player.level(), menu.container.getItem(0),
                recipe, FGACompat.inventory(player), true);
        return analysis == null ? ItemStack.EMPTY : analysis.outputBoxes().get(0).copy();
    }

    public static StonecutterPrepareResult prepareStonecutterTake(Slot resultSlot, Player player) {
        StonecutterMenu menu = STONECUTTER_MENUS.get(resultSlot);
        if (menu == null) return StonecutterPrepareResult.NONE;
        if (stonecutterPlayer(menu) != player) {
            PREPARED_STONECUTTER.remove(menu);
            return StonecutterPrepareResult.BLOCKED;
        }
        ItemStack input = menu.container.getItem(0);
        if (!ruleEnabled() || !isShulkerBox(input)) {
            PREPARED_STONECUTTER.remove(menu);
            return StonecutterPrepareResult.NONE;
        }

        RecipeHolder<StonecutterRecipe> selectedRecipe = selectedStonecutterRecipe(menu);
        if (selectedRecipe == null || !resultUsesRecipe(resultSlot, selectedRecipe)) {
            PREPARED_STONECUTTER.remove(menu);
            return StonecutterPrepareResult.BLOCKED;
        }
        StonecutterAnalysis analysis = analyzeStonecutter(player.level(), input, selectedRecipe,
                FGACompat.inventory(player), false);
        if (analysis == null) {
            PREPARED_STONECUTTER.remove(menu);
            return StonecutterPrepareResult.BLOCKED;
        }

        ItemStack preview = analysis.outputBoxes().get(0);
        if (!ItemStack.matches(resultSlot.getItem(), preview)) {
            PREPARED_STONECUTTER.remove(menu);
            resultSlot.set(preview.copy());
            menu.broadcastChanges();
            return StonecutterPrepareResult.BLOCKED;
        }

        // Mirror the gate on an installed client to avoid a transient ghost take. Only the
        // authoritative server stores and later commits a prepared transaction.
        if (FGACompat.isClientSide(player.level())) return StonecutterPrepareResult.READY;

        long token = ++nextStonecutterToken;
        PREPARED_STONECUTTER.put(menu, new PreparedStonecutterPlan(token, menu, resultSlot,
                menu.getSlot(0), player, FGASettings.fullShulkerBoxCrafting,
                menu.getSelectedRecipeIndex(), input.copy(), analysis));
        return StonecutterPrepareResult.READY;
    }

    public static StonecutterCommitResult commitPreparedStonecutterTake(Slot inputSlot, Player player,
                                                                        ItemStack takenResult) {
        StonecutterMenu menu = STONECUTTER_INPUT_MENUS.get(inputSlot);
        if (menu == null) return StonecutterCommitResult.passThrough();
        PreparedStonecutterPlan prepared = PREPARED_STONECUTTER.remove(menu);
        boolean activeBoxInput = ruleEnabled() && isShulkerBox(inputSlot.getItem());
        if (prepared == null) {
            if (!activeBoxInput) return StonecutterCommitResult.passThrough();
            return recoverStonecutterInvariant(menu, inputSlot, player, takenResult, null,
                    "missing prepared plan");
        }

        String invalid = validatePreparedStonecutter(prepared, inputSlot, player, takenResult);
        if (invalid != null) {
            return recoverStonecutterInvariant(menu, inputSlot, player, takenResult, prepared, invalid);
        }

        ItemStack removedSource = inputSlot.remove(1);
        if (!ItemStack.matches(removedSource, prepared.analysis().sourceBox())) {
            return recoverStonecutterInvariant(menu, inputSlot, player, takenResult, prepared,
                    "prepared source removal receipt changed");
        }
        StonecutterAnalysis analysis = prepared.analysis();
        Inventory inventory = FGACompat.inventory(player);
        consumeEmptyBoxes(inventory, analysis.consumedEmptyBoxes());

        if (analysis.retainsSource()) {
            ItemStack remainder = analysis.remainderBox().copy();
            if (inputSlot.getItem().isEmpty()) inputSlot.set(remainder);
            else giveOrDrop(player, remainder);
        }
        for (int index = 1; index < analysis.outputBoxes().size(); index++) {
            giveOrDrop(player, analysis.outputBoxes().get(index).copy());
        }

        menu.container.setChanged();
        inventory.setChanged();
        return StonecutterCommitResult.handled(removedSource);
    }

    private static String validatePreparedStonecutter(PreparedStonecutterPlan prepared, Slot inputSlot,
                                                       Player player, ItemStack takenResult) {
        if (prepared.menu() != STONECUTTER_INPUT_MENUS.get(inputSlot)
                || prepared.inputSlot() != inputSlot || prepared.player() != player
                || prepared.resultSlot() != prepared.menu().getSlot(1)
                || stonecutterPlayer(prepared.menu()) != player || prepared.token() <= 0L) {
            return "menu/player/token identity changed";
        }
        if (!prepared.ruleValue().equals(FGASettings.fullShulkerBoxCrafting)) return "rule value changed";
        if (prepared.selectedRecipeIndex() != prepared.menu().getSelectedRecipeIndex()) {
            return "selected recipe index changed";
        }
        RecipeHolder<StonecutterRecipe> selected = selectedStonecutterRecipe(prepared.menu());
        // awardUsedRecipes clears ResultContainer.recipeUsed before vanilla reaches Slot.remove,
        // so the commit-side identity check must use the selected recipe list captured at prepare.
        if (selected == null || !selected.equals(prepared.analysis().recipe())) {
            return "recipe identity changed";
        }
        if (!ItemStack.matches(inputSlot.getItem(), prepared.inputSnapshot())) return "input changed";
        Inventory inventory = FGACompat.inventory(player);
        for (Map.Entry<Integer, ItemStack> entry : prepared.analysis().emptySlotSnapshots().entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() >= inventory.getContainerSize()
                    || !ItemStack.matches(inventory.getItem(entry.getKey()), entry.getValue())) {
                return "reserved empty-box slot changed";
            }
        }
        StonecutterAnalysis analysis = prepared.analysis();
        int retained = analysis.retainsSource() ? 1 : 0;
        if (1 + analysis.requiredEmptyBoxes() != analysis.outputBoxes().size() + retained) {
            return "container conservation invariant failed";
        }
        if (!takenResult.isEmpty() && !ItemStack.matches(takenResult, analysis.outputBoxes().get(0))) {
            return "taken result changed";
        }
        return null;
    }

    private static StonecutterCommitResult recoverStonecutterInvariant(StonecutterMenu menu,
                                                                        Slot inputSlot, Player player,
                                                                        ItemStack takenResult,
                                                                        PreparedStonecutterPlan prepared,
                                                                        String reason) {
        LOGGER.error("Full-shulker stonecutter invariant failed for {}: {}; applying loss-biased recovery",
                player.getScoreboardName(), reason);

        ItemStack currentInput = inputSlot.getItem();
        if (isShulkerBox(currentInput)) {
            ItemStack sacrificedSource = inputSlot.remove(1);
            menu.container.setChanged();
            FGACompat.inventory(player).setChanged();
            return StonecutterCommitResult.handled(sacrificedSource);
        }

        ItemStack expected = prepared == null || prepared.analysis().outputBoxes().isEmpty()
                ? ItemStack.EMPTY : prepared.analysis().outputBoxes().get(0);
        boolean removed = removeTransferredStonecutterBox(takenResult, player, expected, true);
        if (!removed) removed = removeTransferredStonecutterBox(takenResult, player, expected, false);
        if (!removed) {
            LOGGER.error("Full-shulker stonecutter recovery found no transferred shulker box; "
                    + "the result was already absent and no further items were changed");
        }
        menu.getSlot(1).set(ItemStack.EMPTY);
        menu.container.setChanged();
        FGACompat.inventory(player).setChanged();
        menu.broadcastChanges();
        return StonecutterCommitResult.handled(ItemStack.EMPTY);
    }

    private static boolean removeTransferredStonecutterBox(ItemStack takenResult, Player player,
                                                            ItemStack expected, boolean exactOnly) {
        if (matchesRecoveryBox(takenResult, expected, exactOnly)) {
            takenResult.shrink(1);
            return true;
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (carried != takenResult && matchesRecoveryBox(carried, expected, exactOnly)) {
            carried.shrink(1);
            if (carried.isEmpty()) player.containerMenu.setCarried(ItemStack.EMPTY);
            return true;
        }
        Inventory inventory = FGACompat.inventory(player);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!matchesRecoveryBox(stack, expected, exactOnly)) continue;
            stack.shrink(1);
            if (stack.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    private static boolean matchesRecoveryBox(ItemStack candidate, ItemStack expected,
                                               boolean exactOnly) {
        if (candidate.isEmpty() || !isShulkerBox(candidate)) return false;
        return !exactOnly || (!expected.isEmpty() && FGACompat.isSameItemSameTags(candidate, expected));
    }

    private static Player stonecutterPlayer(StonecutterMenu menu) {
        if (!(menu.getSlot(2).container instanceof Inventory inventory)) return null;
        return inventory.player;
    }

    @SuppressWarnings("unchecked")
    private static RecipeHolder<StonecutterRecipe> selectedStonecutterRecipe(StonecutterMenu menu) {
        int index = menu.getSelectedRecipeIndex();
        //#if MC == 1.21.1
        List<RecipeHolder<StonecutterRecipe>> recipes =
                ((StonecutterMenuAccessor) menu).carpetFga$getRecipes();
        return index >= 0 && recipes != null && index < recipes.size() ? recipes.get(index) : null;
        //#else
        //$$ List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> entries =
        //$$         menu.getVisibleRecipes().entries();
        //$$ return index >= 0 && index < entries.size()
        //$$         ? entries.get(index).recipe().recipe().orElse(null) : null;
        //#endif
    }

    private static boolean resultUsesRecipe(Slot resultSlot, RecipeHolder<StonecutterRecipe> recipe) {
        if (!(resultSlot.container instanceof ResultContainer resultContainer)) return false;
        RecipeHolder<?> used = resultContainer.getRecipeUsed();
        return used != null && used.equals(recipe);
    }
    //#endif

    //#if MC < 1.21
    //$$ private static CraftingContainer legacyInput(Player player, int width, int height,
    //$$                                               NonNullList<ItemStack> recipeItems) {
    //#if MC < 1.20.1
    //$$     CraftingContainer input = new CraftingContainer(new RecipeMenu(), width, height);
    //$$     for (int slot = 0; slot < recipeItems.size(); slot++) input.setItem(slot, recipeItems.get(slot));
    //$$     return input;
    //#else
    //$$     return new TransientCraftingContainer(player.containerMenu, width, height, recipeItems);
    //#endif
    //$$ }

    //#if MC < 1.20.1
    //$$ private static final class RecipeMenu extends AbstractContainerMenu {
    //$$     private RecipeMenu() {
    //$$         super(null, -1);
    //$$     }
    //$$
    //$$     @Override
    //$$     public boolean stillValid(Player player) {
    //$$         return true;
    //$$     }
    //$$
    //$$     @Override
    //$$     public ItemStack quickMoveStack(Player player, int slot) {
    //$$         return ItemStack.EMPTY;
    //$$     }
    //$$ }
    //#endif
    //#endif

    private static List<InventoryBox> findEmptyBoxes(Inventory inventory, int needed, int shulkerSize) {
        if (needed <= 0) return List.of();
        List<InventoryBox> result = new ArrayList<>(needed);
        for (int slot = 0; slot < inventory.getContainerSize() && result.size() < needed; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isEmptyShulkerBox(stack, shulkerSize)) continue;
            int available = Math.min(stack.getCount(), needed - result.size());
            for (int i = 0; i < available; i++) {
                result.add(new InventoryBox(slot, FGACompat.copyWithCount(stack, 1)));
            }
        }
        return result;
    }

    private static void consumeEmptyBoxes(Inventory inventory, List<InventoryBox> boxes) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (InventoryBox box : boxes) counts.merge(box.slot(), 1, Integer::sum);
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            ItemStack stack = inventory.getItem(entry.getKey());
            stack.shrink(entry.getValue());
            if (stack.isEmpty()) inventory.setItem(entry.getKey(), ItemStack.EMPTY);
        }
    }

    private static ItemStack fillBox(ItemStack template, ItemStack output, int stackLimit, int shulkerSize) {
        ItemStack box = FGACompat.copyWithCount(template, 1);
        NonNullList<ItemStack> contents = NonNullList.withSize(shulkerSize, ItemStack.EMPTY);
        for (int i = 0; i < shulkerSize; i++) {
            contents.set(i, FGACompat.copyWithCount(output, stackLimit));
        }
        //#if MC >= 1.20.5
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        //#else
        //$$ CompoundTag blockEntityTag = box.getOrCreateTagElement("BlockEntityTag");
        //$$ blockEntityTag.remove("Items");
        //$$ ContainerHelper.saveAllItems(blockEntityTag, contents);
        //#endif
        return box;
    }

    private static ItemStack emptyBox(ItemStack template) {
        ItemStack box = FGACompat.copyWithCount(template, 1);
        //#if MC >= 1.20.5
        box.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        //#else
        //$$ CompoundTag blockEntityTag = box.getTagElement("BlockEntityTag");
        //$$ if (blockEntityTag != null) blockEntityTag.remove("Items");
        //#endif
        return box;
    }

    private static NonNullList<ItemStack> contents(ItemStack box, int shulkerSize) {
        NonNullList<ItemStack> contents = NonNullList.withSize(shulkerSize, ItemStack.EMPTY);
        //#if MC >= 1.20.5
        ItemContainerContents component = box.get(DataComponents.CONTAINER);
        if (component != null) component.copyInto(contents);
        //#else
        //$$ CompoundTag blockEntityTag = box.getTagElement("BlockEntityTag");
        //$$ if (blockEntityTag != null) ContainerHelper.loadAllItems(blockEntityTag, contents);
        //#endif
        return contents;
    }

    private static boolean isEmptyShulkerBox(ItemStack stack, int shulkerSize) {
        if (!isShulkerBox(stack)) return false;
        return contents(stack, shulkerSize).stream().allMatch(ItemStack::isEmpty);
    }

    private static int shulkerSize() {
        //#if MC >= 1.20.1 && MC <= 26.2
        return AmsLargeShulkerBoxCompat.isEnabled() ? AMS_LARGE_SHULKER_SIZE : VANILLA_SHULKER_SIZE;
        //#else
        //$$ return VANILLA_SHULKER_SIZE;
        //#endif
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static boolean canFitInsideContainer(ItemStack stack) {
        //#if MC >= 1.17
        return stack.getItem().canFitInsideContainerItems();
        //#else
        //$$ return !isShulkerBox(stack);
        //#endif
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!FGACompat.inventory(player).add(stack) && !stack.isEmpty()) player.drop(stack, false);
    }

    private static void notifyFailure(CraftingContainer crafting, Player player, Analysis analysis) {
        if (analysis.failure() == Failure.NONE) {
            LAST_NOTICE.remove(crafting);
            return;
        }
        String signature = analysis.failure().name() + ":" + analysis.detail();
        if (signature.equals(LAST_NOTICE.put(crafting, signature))) return;
        String key = "carpet-fga-addition.fullShulkerBoxCrafting." + analysis.failure().translationKey;
        FGACompat.displayClientMessage(player, analysis.detail() > 0
                ? FGACompat.translatable(key, analysis.detail())
                : FGACompat.translatable(key), true);
    }

    private static void clear(CraftingContainer crafting) {
        PLANS.remove(crafting);
        LAST_NOTICE.remove(crafting);
    }

    private record InventoryBox(int slot, ItemStack template) {
    }

    //#if MC >= 1.21
    private record StonecutterAnalysis(RecipeHolder<StonecutterRecipe> recipe,
                                       int requiredInput, long crafts, long consumedMaterial,
                                       ItemStack sourceBox, ItemStack remainderBox,
                                       List<ItemStack> outputBoxes,
                                       List<InventoryBox> consumedEmptyBoxes,
                                       Map<Integer, ItemStack> emptySlotSnapshots,
                                       boolean retainsSource, int requiredEmptyBoxes) {
    }

    private record PreparedStonecutterPlan(long token, StonecutterMenu menu, Slot resultSlot,
                                            Slot inputSlot, Player player, String ruleValue,
                                            int selectedRecipeIndex, ItemStack inputSnapshot,
                                            StonecutterAnalysis analysis) {
    }
    //#endif

    private record Plan(List<Integer> sourceSlots, List<ItemStack> outputBoxes,
                        List<ItemStack> returnedEmptyBoxes, List<InventoryBox> consumedEmptyBoxes) {
    }

    //#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.2
    private record RestockTarget(int craftingSlot, ItemStack template) {
    }

    private record RestockGroup(ItemStack template, List<RestockTarget> targets) {
    }

    private record RestockApplication(CraftingContainer crafting,
                                      Map<Integer, Integer> inventoryConsumption,
                                      Map<Integer, ItemStack> craftingContents) {
    }

    private static final class QuickResultContext {
        private final Player player;
        private final List<ItemStack> initialInventory;
        private CraftingContainer crafting;
        private final List<RestockTarget> targets = new ArrayList<>();

        private QuickResultContext(Player player, List<ItemStack> initialInventory) {
            this.player = player;
            this.initialInventory = initialInventory;
        }

        private static QuickResultContext capture(Player player) {
            Inventory inventory = FGACompat.inventory(player);
            int size = Math.min(MAIN_INVENTORY_SIZE, inventory.getContainerSize());
            List<ItemStack> snapshot = new ArrayList<>(size);
            for (int slot = 0; slot < size; slot++) snapshot.add(inventory.getItem(slot).copy());
            return new QuickResultContext(player, List.copyOf(snapshot));
        }

        private Player player() {
            return player;
        }

        private void recordTargets(CraftingContainer crafting, List<Integer> sourceSlots) {
            if (this.crafting == null) this.crafting = crafting;
            if (this.crafting != crafting) return;
            for (int craftingSlot : sourceSlots) {
                ItemStack source = crafting.getItem(craftingSlot);
                if (source.isEmpty()) continue;
                boolean recorded = targets.stream()
                        .anyMatch(target -> target.craftingSlot() == craftingSlot);
                if (!recorded) {
                    targets.add(new RestockTarget(craftingSlot,
                            FGACompat.copyWithCount(source, 1)));
                }
            }
        }

        private void applyBalancedRestock() {
            RestockApplication application = createRestockApplication();
            if (application == null) return;

            Inventory inventory = FGACompat.inventory(player);
            for (Map.Entry<Integer, Integer> entry : application.inventoryConsumption().entrySet()) {
                ItemStack stack = inventory.getItem(entry.getKey());
                stack.shrink(entry.getValue());
                if (stack.isEmpty()) inventory.setItem(entry.getKey(), ItemStack.EMPTY);
            }
            for (Map.Entry<Integer, ItemStack> entry : application.craftingContents().entrySet()) {
                application.crafting().setItem(entry.getKey(), entry.getValue());
            }
            inventory.setChanged();
            player.containerMenu.slotsChanged(application.crafting());
            player.containerMenu.broadcastChanges();
        }

        private RestockApplication createRestockApplication() {
            if (crafting == null || targets.isEmpty()) return null;
            Inventory inventory = FGACompat.inventory(player);
            List<RestockGroup> groups = groupTargets();
            Map<Integer, Integer> inventoryConsumption = new HashMap<>();
            Map<Integer, ItemStack> craftingContents = new HashMap<>();
            boolean changesCraftingGrid = false;

            for (RestockGroup group : groups) {
                int existing = 0;
                for (RestockTarget target : group.targets()) {
                    ItemStack current = crafting.getItem(target.craftingSlot());
                    if (!current.isEmpty()
                            && !FGACompat.isSameItemSameTags(current, group.template())) {
                        return null;
                    }
                    existing += current.getCount();
                }

                List<Integer> inventorySlots = new ArrayList<>();
                int available = 0;
                int size = Math.min(initialInventory.size(), inventory.getContainerSize());
                for (int slot = 0; slot < size; slot++) {
                    ItemStack initial = initialInventory.get(slot);
                    ItemStack current = inventory.getItem(slot);
                    if (!FGACompat.isSameItemSameTags(initial, group.template())
                            || !FGACompat.isSameItemSameTags(current, group.template())) {
                        continue;
                    }
                    int count = Math.min(initial.getCount(), current.getCount());
                    if (count <= 0) continue;
                    inventorySlots.add(slot);
                    available += count;
                }

                int slotLimit = Math.max(1, FGASettings.effectiveContainerStackLimit(group.template()));
                int capacity = slotLimit * group.targets().size();
                int finalTotal = Math.min(capacity, existing + available);
                if (finalTotal < group.targets().size()) return null;

                int remaining = finalTotal - existing;
                for (int slot : inventorySlots) {
                    if (remaining <= 0) break;
                    int usable = Math.min(initialInventory.get(slot).getCount(), inventory.getItem(slot).getCount());
                    int consumed = Math.min(usable, remaining);
                    if (consumed > 0) inventoryConsumption.merge(slot, consumed, Integer::sum);
                    remaining -= consumed;
                }
                if (remaining > 0) return null;

                int base = finalTotal / group.targets().size();
                int extra = finalTotal % group.targets().size();
                for (int index = 0; index < group.targets().size(); index++) {
                    RestockTarget target = group.targets().get(index);
                    ItemStack replacement = FGACompat.copyWithCount(
                            group.template(), base + (index < extra ? 1 : 0));
                    ItemStack current = crafting.getItem(target.craftingSlot());
                    if (!FGACompat.isSameItemSameTags(current, replacement)
                            || current.getCount() != replacement.getCount()) {
                        changesCraftingGrid = true;
                    }
                    craftingContents.put(target.craftingSlot(), replacement);
                }
            }
            return inventoryConsumption.isEmpty() && !changesCraftingGrid ? null
                    : new RestockApplication(crafting, inventoryConsumption, craftingContents);
        }

        private List<RestockGroup> groupTargets() {
            List<RestockGroup> groups = new ArrayList<>();
            for (RestockTarget target : targets) {
                RestockGroup group = groups.stream()
                        .filter(candidate -> FGACompat.isSameItemSameTags(
                                candidate.template(), target.template()))
                        .findFirst().orElse(null);
                if (group == null) {
                    List<RestockTarget> groupTargets = new ArrayList<>();
                    groupTargets.add(target);
                    groups.add(new RestockGroup(target.template(), groupTargets));
                } else {
                    group.targets().add(target);
                }
            }
            return groups;
        }
    }
    //#endif

    private record OutputGroup(ItemStack item, int stackLimit, long boxCount, long totalCount) {
    }

    private static final class RemainderTotal {
        private final ItemStack item;
        private long count;

        private RemainderTotal(ItemStack item, long count) {
            this.item = item;
            this.count = count;
        }

        private ItemStack item() { return item; }
        private long count() { return count; }
        private void add(long amount) { count += amount; }
    }

    private enum Failure {
        NONE(""),
        INVALID_INPUT_BOX("invalidInputBox"),
        UNSTACKABLE_INPUT("unstackableInput"),
        INPUT_CAPACITY_MISMATCH("inputCapacityMismatch"),
        //#if MC >= 1.21
        CONTENT_COUNT_MISMATCH("contentCountMismatch"),
        //#endif
        UNSTACKABLE_OUTPUT("unstackableOutput"),
        NON_WHOLE_OUTPUT("nonWholeOutput"),
        UNSTACKABLE_REMAINDER("unstackableRemainder"),
        NON_WHOLE_REMAINDER("nonWholeRemainder"),
        TOO_MANY_OUTPUTS("tooManyOutputs"),
        MISSING_EMPTY_BOXES("missingBoxes");

        private final String translationKey;

        Failure(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private record Analysis(Plan plan, Failure failure, int detail) {
        private static final Analysis NONE = new Analysis(null, Failure.NONE, 0);

        private static Analysis success(Plan plan) {
            return new Analysis(plan, Failure.NONE, 0);
        }

        private static Analysis failure(Failure failure) {
            return new Analysis(null, failure, 0);
        }

        private static Analysis failure(Failure failure, int detail) {
            return new Analysis(null, failure, detail);
        }
    }
}
//#endif
