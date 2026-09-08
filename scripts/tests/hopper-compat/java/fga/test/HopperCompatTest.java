package fga.test;

import carpet.fga.FGASettings;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

public class HopperCompatTest implements ModInitializer {
    public static int calls, seenCount;
    public static boolean oneAtATime;
    private static int checks;

    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                ServerLevel level = server.overworld();
                for (String rule : new String[]{"false", "true"}) {
                    FGASettings.droppedItemStackLimit = rule;
                    for (boolean cart : new boolean[]{false, true}) {
                        Container target = cart ? newCart(level) : new SimpleContainer(5);
                        oneAtATime = true;
                        calls = 0;
                        ItemEntity entity = new ItemEntity(level, 0, 100, 0, new ItemStack(Items.SHULKER_BOX, 6));
                        boolean complete = HopperBlockEntity.addItem(target, entity);
                        check(calls == 1, "one wrapped call " + rule + "/" + cart);
                        check(!complete && entity.getItem().getCount() == 5, "partial result and count");
                        check(target.getItem(0).getCount() == 1 && target.getItem(1).isEmpty(), "one slot only");
                        if (rule.equals("false")) check(seenCount == 6, "off passes original count");
                    }
                }
                FGASettings.droppedItemStackLimit = "true";
                oneAtATime = false;
                Container target = new SimpleContainer(5);
                ItemEntity entity = new ItemEntity(level, 0, 100, 0, new ItemStack(Items.STONE, 200));
                calls = 0;
                check(!HopperBlockEntity.addItem(target, entity), "oversized transfer is partial");
                check(calls == 1 && target.getItem(0).getCount() == 64 && entity.getItem().getCount() == 136,
                        "one bounded batch without item loss");
                for (int slot = 0; slot < 5; slot++) target.setItem(slot, new ItemStack(Items.STONE, 64));
                calls = 0;
                check(!HopperBlockEntity.addItem(target, entity) && entity.getItem().getCount() == 136 && calls == 1,
                        "full target preserves remainder");
                System.out.println("FGA_HOPPER_COMPAT_PASS checks=" + checks);
            } catch (Throwable failure) {
                System.out.println("FGA_HOPPER_COMPAT_FAIL " + failure);
                failure.printStackTrace();
            } finally {
                FGASettings.droppedItemStackLimit = "false";
                server.halt(false);
            }
        });
    }

    private static Container newCart(Level level) throws Exception {
        Class<?> cart, types;
        try {
            cart = Class.forName("net.minecraft.world.entity.vehicle.MinecartHopper");
            types = EntityType.class;
        } catch (ClassNotFoundException changedPackage) {
            cart = Class.forName("net.minecraft.world.entity.vehicle.minecart.MinecartHopper");
            types = Class.forName("net.minecraft.world.entity.EntityTypes");
        }
        return (Container) cart.getConstructor(EntityType.class, Level.class)
                .newInstance(types.getField("HOPPER_MINECART").get(null), level);
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
}
