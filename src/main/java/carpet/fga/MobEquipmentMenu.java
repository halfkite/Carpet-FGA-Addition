package carpet.fga;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
//#if MC >= 26.1.2
//$$ import net.minecraft.world.inventory.ContainerInput;
//#else
import net.minecraft.world.inventory.ClickType;
//#endif
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class MobEquipmentMenu extends ChestMenu {
    private static final int ROWS = 4;

    public MobEquipmentMenu(int containerId, Inventory inventory, Container container) {
        super(MenuType.GENERIC_9x4, containerId, inventory, container, ROWS);
        for (int index = 0; index < MobEquipmentContainer.CONTAINER_SIZE; index++) {
            if (!MobEquipmentContainer.isEquipmentIndex(index)) {
                Slot original = slots.get(index);
                Slot unavailable = new UnavailableSlot(container, index, original.x, original.y);
                unavailable.index = original.index;
                slots.set(index, unavailable);
            }
        }
    }

    //#if MC >= 26.1.2
    //$$ @Override
    //$$ public void clicked(int slotId, int button, ContainerInput input, Player player) {
    //$$     if (isUnavailableMenuSlot(slotId)) {
    //$$         broadcastChanges();
    //$$         return;
    //$$     }
    //$$     super.clicked(slotId, button, input, player);
    //$$ }
    //#elseif MC >= 1.17
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (isUnavailableMenuSlot(slotId)) {
            broadcastChanges();
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }
    //#else
    //$$ @Override
    //$$ public ItemStack clicked(int slotId, int button, ClickType clickType, Player player) {
    //$$     if (isUnavailableMenuSlot(slotId)) {
    //$$         broadcastChanges();
    //$$         return ItemStack.EMPTY;
    //$$     }
    //$$     return super.clicked(slotId, button, clickType, player);
    //$$ }
    //#endif

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < MobEquipmentContainer.CONTAINER_SIZE
                && !MobEquipmentContainer.isEquipmentIndex(index)) {
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, index);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !isUnavailableMenuSlot(slot.index) && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !isUnavailableMenuSlot(slot.index) && super.canTakeItemForPickAll(stack, slot);
    }

    private static boolean isUnavailableMenuSlot(int slotId) {
        return slotId >= 0
                && slotId < MobEquipmentContainer.CONTAINER_SIZE
                && !MobEquipmentContainer.isEquipmentIndex(slotId);
    }

    private static final class UnavailableSlot extends Slot {
        private UnavailableSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        //#if MC >= 1.17
        @Override
        public boolean allowModification(Player player) {
            return false;
        }
        //#endif
    }
}
