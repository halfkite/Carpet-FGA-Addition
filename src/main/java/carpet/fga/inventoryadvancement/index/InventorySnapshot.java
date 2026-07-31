//#if MC >= 1.20.5
package carpet.fga.inventoryadvancement.index;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class InventorySnapshot {
    private static final int MAX_CHANGED_RAW_IDS = Inventory.INVENTORY_SIZE * 2 + 1;

    private final ItemStack[] main = new ItemStack[Inventory.INVENTORY_SIZE];
    private final int[] changedRawIds = new int[MAX_CHANGED_RAW_IDS];
    private int changedRawIdCount;
    private int fullSlots;
    private int emptySlots;
    private int occupiedSlots;
    private boolean initialized;

    public boolean updateAndCollectChanges(Inventory inventory, Set<Integer> changedRawIds) {
        boolean first = update(inventory);
        for (int index = 0; index < changedRawIdCount; index++) {
            changedRawIds.add(this.changedRawIds[index]);
        }
        return first;
    }

    boolean update(Inventory inventory) {
        boolean first = !initialized;
        changedRawIdCount = 0;
        fullSlots = 0;
        emptySlots = 0;
        occupiedSlots = 0;

        int containerSize = inventory.getContainerSize();
        for (int slot = 0; slot < containerSize; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current.isEmpty()) {
                emptySlots++;
            } else {
                occupiedSlots++;
                if (current.getCount() >= current.getMaxStackSize()) fullSlots++;
            }

            if (slot < main.length) {
                ItemStack previous = main[slot];
                if (first || previous == null || !ItemStack.matches(previous, current)) {
                    if (!first) addRawId(previous);
                    if (!first) addRawId(current);
                    main[slot] = current.isEmpty() ? ItemStack.EMPTY : current.copy();
                }
            }
        }
        for (int slot = containerSize; slot < main.length; slot++) {
            ItemStack previous = main[slot];
            if (!first && previous != null && !previous.isEmpty()) addRawId(previous);
            main[slot] = ItemStack.EMPTY;
        }
        initialized = true;
        return first;
    }

    void includeChangedStack(ItemStack stack) {
        addRawId(stack);
    }

    int changedRawIdCount() {
        return changedRawIdCount;
    }

    int changedRawIdAt(int index) {
        return changedRawIds[index];
    }

    int fullSlots() {
        return fullSlots;
    }

    int emptySlots() {
        return emptySlots;
    }

    int occupiedSlots() {
        return occupiedSlots;
    }

    public void invalidate() {
        initialized = false;
    }

    private void addRawId(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            int rawId = BuiltInRegistries.ITEM.getId(stack.getItem());
            if (rawId < 0) return;
            for (int index = 0; index < changedRawIdCount; index++) {
                if (changedRawIds[index] == rawId) return;
            }
            if (changedRawIdCount >= changedRawIds.length) {
                throw new IllegalStateException("Inventory change set exceeded its fixed safety bound");
            }
            changedRawIds[changedRawIdCount++] = rawId;
        }
    }
}
//#endif

