package fga.inventorytest;

import carpet.fga.DroppedItemStackLimitConfig;
import carpet.fga.FGASettings;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class InventoryCompatTest implements ModInitializer {
    public static int cap;
    private static int checks;
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                var constructor = Inventory.class.getConstructors()[0];
                Inventory inventory = (Inventory) constructor.newInstance(new Object[constructor.getParameterCount()]);
                for (boolean retained : new boolean[]{false, true}) {
                    if (retained) DroppedItemStackLimitConfig.setInventoryLimit(1000);
                    FGASettings.droppedItemStackLimit = "false";
                    for (int syntheticCap : new int[]{0, 37}) {
                        cap = syntheticCap;
                        for (int maximum : new int[]{1, 16, 64, 128, 1000}) {
                            ItemStack stack = new ItemStack(Items.STONE);
                            stack.set(DataComponents.MAX_STACK_SIZE, maximum);
                            int expected = Math.min(cap == 0 ? 99 : cap, maximum);
                            check(inventory.getMaxStackSize(stack) == expected, "off capacity " + maximum);
                            check(new Slot(inventory, 0, 0, 0).getMaxStackSize(stack) == expected, "slot capacity");
                        }
                    }
                }
                cap = 0;
                FGASettings.droppedItemStackLimit = "true";
                ItemStack stone = new ItemStack(Items.STONE);
                check(inventory.getMaxStackSize(stone) == 1000, "active scope still expands inventory");
                check(new Slot(inventory, 0, 0, 0).getMaxStackSize(stone) == 1000, "active normal slot");
                Slot special = new Slot(inventory, 0, 0, 0) {
                    @Override public int getMaxStackSize() { return 1; }
                };
                check(special.getMaxStackSize(stone) == 1, "active special slot remains limited");
                System.out.println("FGA_INVENTORY_COMPAT_PASS checks=" + checks);
            } catch (Throwable failure) {
                System.out.println("FGA_INVENTORY_COMPAT_FAIL " + failure);
                failure.printStackTrace();
            } finally {
                FGASettings.droppedItemStackLimit = "false";
                server.halt(false);
            }
        });
    }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
}
