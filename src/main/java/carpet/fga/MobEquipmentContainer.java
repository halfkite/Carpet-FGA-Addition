package carpet.fga;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;

public final class MobEquipmentContainer implements Container {
    public static final int CONTAINER_SIZE = 36;
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND
    };
    private static final int[] SLOT_INDICES = {1, 10, 19, 28, 22, 31};

    private final Mob mob;

    public MobEquipmentContainer(Mob mob) {
        this.mob = mob;
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (EquipmentSlot slot : SLOTS) {
            if (!mob.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        for (ItemStack stack : carpetFga$backpackItems()) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        int equipmentIndex = equipmentIndex(index);
        if (equipmentIndex >= 0) {
            return mob.getItemBySlot(SLOTS[equipmentIndex]);
        }
        int backpackIndex = backpackIndex(index);
        if (backpackIndex >= 0) return carpetFga$backpackItems().get(backpackIndex);
        return new ItemStack(Items.BARRIER);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        int equipmentIndex = equipmentIndex(index);
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        if (equipmentIndex >= 0) {
            ItemStack equipped = getItem(index);
            ItemStack removed = equipped.split(count);
            if (equipped.isEmpty()) {
                mob.setItemSlot(SLOTS[equipmentIndex], ItemStack.EMPTY);
            }
            return removed;
        }
        int backpackIndex = backpackIndex(index);
        return backpackIndex >= 0
                ? ContainerHelper.removeItem(carpetFga$backpackItems(), backpackIndex, count)
                : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        int equipmentIndex = equipmentIndex(index);
        if (equipmentIndex >= 0) {
            ItemStack equipped = getItem(index);
            mob.setItemSlot(SLOTS[equipmentIndex], ItemStack.EMPTY);
            return equipped;
        }
        int backpackIndex = backpackIndex(index);
        return backpackIndex >= 0
                ? ContainerHelper.takeItem(carpetFga$backpackItems(), backpackIndex)
                : ItemStack.EMPTY;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        int equipmentIndex = equipmentIndex(index);
        if (equipmentIndex >= 0) {
            ItemStack equipped = stack;
            if (stack.getCount() > 1) {
                equipped = FGACompat.copyWithCount(stack, 1);
            }
            EquipmentSlot slot = SLOTS[equipmentIndex];
            mob.setItemSlot(slot, equipped);
            if (!equipped.isEmpty()) {
                mob.setDropChance(slot, 2.0F);
            }
            return;
        }
        int backpackIndex = backpackIndex(index);
        if (backpackIndex >= 0) carpetFga$backpackItems().set(backpackIndex, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return mob.isAlive() && mob.distanceToSqr(player) <= 64.0D;
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return isEquipmentIndex(index) || isBackpackIndex(index);
    }

    @Override
    public void clearContent() {
        for (EquipmentSlot slot : SLOTS) {
            mob.setItemSlot(slot, ItemStack.EMPTY);
        }
        for (int index = 0; index < MobBackpackData.SIZE; index++) {
            carpetFga$backpackItems().set(index, ItemStack.EMPTY);
        }
    }

    public static boolean isEquipmentIndex(int index) {
        return equipmentIndex(index) >= 0;
    }

    public static boolean isBackpackIndex(int index) {
        return backpackIndex(index) >= 0;
    }

    private static final int[] BACKPACK_INDICES = {4, 5, 6, 7, 13, 14, 15, 16};

    private static int backpackIndex(int index) {
        for (int backpackIndex = 0; backpackIndex < BACKPACK_INDICES.length; backpackIndex++) {
            if (BACKPACK_INDICES[backpackIndex] == index) return backpackIndex;
        }
        return -1;
    }

    private NonNullList<ItemStack> carpetFga$backpackItems() {
        return ((MobBackpackAccess) (Object) mob).carpetFga$getBackpackData().items();
    }

    private static int equipmentIndex(int index) {
        for (int equipmentIndex = 0; equipmentIndex < SLOT_INDICES.length; equipmentIndex++) {
            if (SLOT_INDICES[equipmentIndex] == index) {
                return equipmentIndex;
            }
        }
        return -1;
    }
}
