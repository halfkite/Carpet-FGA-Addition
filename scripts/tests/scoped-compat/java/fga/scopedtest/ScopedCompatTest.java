package fga.scopedtest;

import carpet.fga.DroppedItemStackLimitConfig;
import carpet.fga.FGASettings;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class ScopedCompatTest implements ModInitializer {
    private static int checks;
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                ItemStack stone = new ItemStack(Items.STONE);
                Path config = server.getWorldPath(LevelResource.ROOT)
                        .resolve("config/carpetfgaaddition/dropped-item-stack-limit.json");
                boolean restarted = Files.exists(config);
                check(FGASettings.droppedItemStackLimit.equals("false"), "default/persisted-off rule");
                check(DroppedItemStackLimitConfig.effectiveInventoryLimit(stone) == 64, "off inventory");
                check(DroppedItemStackLimitConfig.effectiveContainerLimit(stone) == 64, "off container");
                if (restarted) {
                    check(DroppedItemStackLimitConfig.snapshot().inventoryLimit() == 1000
                            && DroppedItemStackLimitConfig.snapshot().containerLimit() == 1000, "restart retains scopes");
                }
                FGASettings.droppedItemStackLimit = "true";
                DroppedItemStackLimitConfig.setInventoryLimit(1000);
                DroppedItemStackLimitConfig.setContainerLimit(1000);
                byte[] saved = Files.readAllBytes(config);
                check(DroppedItemStackLimitConfig.effectiveInventoryLimit(stone) == 1000, "on inventory");
                check(new SimpleContainer(1).getMaxStackSize(stone) == 1000, "on container consumer");
                FGASettings.droppedItemStackLimit = "false";
                check(DroppedItemStackLimitConfig.effectiveInventoryLimit(stone) == 64, "toggle inventory");
                SimpleContainer container = new SimpleContainer(1);
                check(container.getMaxStackSize(stone) == 64, "toggle container");
                check(new Slot(container, 0, 0, 0).getMaxStackSize(stone) == 64, "toggle slot");
                DroppedItemStackLimitConfig.load(server);
                check(DroppedItemStackLimitConfig.effectiveContainerLimit(stone) == 64, "reload while off");
                check(Arrays.equals(saved, Files.readAllBytes(config)), "off/reload preserve file bytes");
                FGASettings.droppedItemStackLimit = "true";
                check(DroppedItemStackLimitConfig.effectiveInventoryLimit(stone) == 1000, "reenable retained scope");
                check(DroppedItemStackLimitConfig.effectiveInventoryLimit(new ItemStack(Items.DIAMOND_SWORD)) == 1,
                        "unstackable stays unstackable");
                FGASettings.droppedItemStackLimit = "false";
                System.out.println("FGA_SCOPED_COMPAT_PASS checks=" + checks + " restarted=" + restarted);
            } catch (Throwable failure) {
                System.out.println("FGA_SCOPED_COMPAT_FAIL " + failure);
                failure.printStackTrace();
            } finally {
                server.halt(false);
            }
        });
    }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
}
