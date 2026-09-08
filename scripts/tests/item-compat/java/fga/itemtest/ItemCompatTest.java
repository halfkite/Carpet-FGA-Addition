package fga.itemtest;

import carpet.fga.DroppedItemStackLimitConfig;
import carpet.fga.FGASettings;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.nbt.CompoundTag;
import java.lang.reflect.Method;

public class ItemCompatTest implements ModInitializer {
    public static boolean veto;
    public static int cap, argument;
    public static AABB customBox, seenBox;
    private static int checks;

    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                Method eligible = ItemEntity.class.getDeclaredMethod("isMergable");
                eligible.setAccessible(true);
                Method neighbours = ItemEntity.class.getDeclaredMethod("mergeWithNeighbours");
                neighbours.setAccessible(true);
                Method merge = ItemEntity.class.getDeclaredMethod("merge", ItemEntity.class, ItemStack.class, ItemStack.class);
                merge.setAccessible(true);
                ItemEntity entity = new ItemEntity(server.overworld(), 0, 100, 0, new ItemStack(Items.STONE, 2));
                for (String rule : new String[]{"false", "true"}) {
                    FGASettings.droppedItemStackLimit = rule;
                    DroppedItemStackLimitConfig.setAllMode(1000);
                    veto = true;
                    check(!(boolean) eligible.invoke(entity), "preserve veto " + rule);
                    veto = false;
                }
                FGASettings.droppedItemStackLimit = "false";
                cap = 13;
                entity.setItem(new ItemStack(Items.STONE, 14));
                check(!(boolean) eligible.invoke(entity), "preserve modified capacity");
                check(!ItemEntity.areMergable(new ItemStack(Items.STONE, 7), new ItemStack(Items.STONE, 7)), "total cap");
                ItemStack merged = ItemEntity.merge(new ItemStack(Items.STONE, 1), new ItemStack(Items.STONE, 20), 1000);
                check(merged.getCount() == 13, "inner merge cap");
                argument = 7;
                merge.invoke(null, entity, new ItemStack(Items.STONE, 1), new ItemStack(Items.STONE, 10));
                check(entity.getItem().getCount() == 7, "preserve passed merge argument");
                FGASettings.droppedItemMergeDistance = -1;
                customBox = new AABB(-3, 40, -7, 9, 50, 11);
                neighbours.invoke(entity);
                check(seenBox == customBox, "pass exact other-mod box");
                customBox = null;
                FGASettings.droppedItemMergeDistance = 2;
                neighbours.invoke(entity);
                AABB expected = entity.getBoundingBox().inflate(2, 0, 2);
                check(seenBox.equals(expected), "distance rule works independently of stack rule");
                FGASettings.droppedItemMergeDistance = -1;
                cap = 0;
                argument = 0;
                FGASettings.droppedItemStackLimit = "true";
                entity.setItem(new ItemStack(Items.STONE, 200));
                check((boolean) eligible.invoke(entity), "active oversized eligibility");
                check(ItemEntity.areMergable(new ItemStack(Items.STONE, 200), new ItemStack(Items.STONE, 200)), "active total");
                merge.invoke(null, entity, new ItemStack(Items.STONE, 200), new ItemStack(Items.STONE, 200));
                check(entity.getItem().getCount() == 400, "active entity merge");
                DroppedItemStackLimitConfig.setWhitelistMode();
                cap = 13;
                entity.setItem(new ItemStack(Items.STONE, 14));
                check(!(boolean) eligible.invoke(entity), "unconfigured item preserves other-mod cap");
                FGASettings.droppedItemStackLimit = "false";
                entity.setItem(new ItemStack(Items.STONE, 200));
                Method save = ItemEntity.class.getDeclaredMethod("addAdditionalSaveData", CompoundTag.class);
                Method load = ItemEntity.class.getDeclaredMethod("readAdditionalSaveData", CompoundTag.class);
                save.setAccessible(true);
                load.setAccessible(true);
                CompoundTag persisted = new CompoundTag();
                save.invoke(entity, persisted);
                check(persisted.contains("carpet-fga-addition:ExtendedCount"), "off preserves extended count");
                ItemEntity restored = new ItemEntity(server.overworld(), 0, 100, 0, ItemStack.EMPTY);
                load.invoke(restored, persisted);
                check(restored.getItem().getCount() == 200, "extended count round trip while off");
                System.out.println("FGA_ITEM_COMPAT_PASS checks=" + checks);
            } catch (Throwable failure) {
                System.out.println("FGA_ITEM_COMPAT_FAIL " + failure);
                failure.printStackTrace();
            } finally {
                FGASettings.droppedItemStackLimit = "false";
                FGASettings.droppedItemMergeDistance = -1;
                server.halt(false);
            }
        });
    }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
}
