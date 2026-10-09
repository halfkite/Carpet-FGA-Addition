//#if MC == 26.3
//$$ package carpet.fga.smoke;
//$$
//$$ import carpet.CarpetServer;
//$$ import carpet.api.settings.CarpetRule;
//$$ import carpet.fga.FGASettings;
//$$ import carpet.fga.FullShulkerBoxCraftingManager;
//$$ import com.mojang.authlib.GameProfile;
//$$ import net.fabricmc.api.DedicatedServerModInitializer;
//$$ import net.fabricmc.loader.api.FabricLoader;
//$$ import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
//$$ import net.minecraft.core.BlockPos;
//$$ import net.minecraft.core.Direction;
//$$ import net.minecraft.core.NonNullList;
//$$ import net.minecraft.core.component.DataComponents;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.server.level.ClientInformation;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.world.entity.player.Player;
//$$ import net.minecraft.world.InteractionHand;
//$$ import net.minecraft.world.InteractionResult;
//$$ import net.minecraft.world.inventory.AbstractContainerMenu;
//$$ import net.minecraft.world.inventory.ResultContainer;
//$$ import net.minecraft.world.inventory.TransientCraftingContainer;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.item.Items;
//$$ import net.minecraft.world.item.BlockItem;
//$$ import net.minecraft.world.item.context.BlockPlaceContext;
//$$ import net.minecraft.world.item.component.ItemContainerContents;
//$$ import net.minecraft.world.level.block.Blocks;
//$$ import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
//$$ import net.minecraft.world.phys.BlockHitResult;
//$$ import net.minecraft.world.phys.Vec3;
//$$
//$$ import java.lang.reflect.Method;
//$$ import java.util.UUID;
//$$
//$$ /** Test-only probe for actual AMS state and full-box material conservation. */
//$$ public final class AmsShulkerCompatProbe implements DedicatedServerModInitializer {
//$$     private static int checks;
//$$
//$$     @Override
//$$     public void onInitializeServer() {
//$$         // Load the helper before world/rule initialization to exercise its lifecycle.
//$$         try { enabled(); }
//$$         catch (Exception failure) { throw new IllegalStateException(failure); }
//$$         ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> {
//$$             try {
//$$                 run(server);
//$$                 System.out.println("FGA_AMS_SHULKER_PASS scenario="
//$$                         + System.getenv("FGA_AMS_SMOKE_SCENARIO") + " checks=" + checks);
//$$             } catch (Throwable failure) {
//$$                 System.out.println("FGA_AMS_SHULKER_FAIL " + failure);
//$$                 failure.printStackTrace();
//$$             } finally {
//$$                 server.halt(false);
//$$             }
//$$         }));
//$$     }
//$$
//$$     private static void run(MinecraftServer server) throws Exception {
//$$         String scenario = System.getenv("FGA_AMS_SMOKE_SCENARIO");
//$$         boolean installed = FabricLoader.getInstance().isModLoaded("carpet-ams-addition");
//$$         CarpetRule<?> rule = CarpetServer.settingsManager.getCarpetRule("largeShulkerBox");
//$$         check(installed == !scenario.equals("none"), "expected AMS installation");
//$$         if (!installed) {
//$$             check(rule == null, "no AMS rule without AMS");
//$$             verify(server, 27);
//$$             return;
//$$         }
//$$         check(rule != null, "AMS rule registered");
//$$         boolean initial = scenario.equals("legacy-on");
//$$         check(Boolean.TRUE.equals(rule.value()) == initial, "startup configuration");
//$$         verify(server, initial ? 54 : 27);
//$$         rule.set(server.createCommandSourceStack(), Boolean.toString(!initial));
//$$         check(Boolean.TRUE.equals(rule.value()) == !initial, "Carpet rule value changed");
//$$         if (scenario.startsWith("legacy")) {
//$$             // FGA follows the live rule; old AMS retains its capacity until restart.
//$$             check(enabled() == !initial, "FGA reads changed legacy rule directly");
//$$             Method size = FullShulkerBoxCraftingManager.class.getDeclaredMethod("shulkerSize");
//$$             size.setAccessible(true);
//$$             check((int) size.invoke(null) == 0, "FGA rejects mismatched rule and block capacity");
//$$             ShulkerBoxBlockEntity block = new ShulkerBoxBlockEntity(
//$$                     BlockPos.ZERO, Blocks.SHULKER_BOX.defaultBlockState());
//$$             check(block.getContainerSize() == (initial ? 54 : 27), "legacy AMS requires restart");
//$$             rejectMismatch(server, initial ? 54 : 27);
//$$         } else {
//$$             verify(server, 54);
//$$         }
//$$         rule.set(server.createCommandSourceStack(), Boolean.toString(initial));
//$$         verify(server, initial ? 54 : 27);
//$$         if (scenario.equals("beta")) {
//$$             rule.set(server.createCommandSourceStack(), "true");
//$$             verify(server, 54);
//$$         }
//$$     }
//$$
//$$     private static void verify(MinecraftServer server, int expected) throws Exception {
//$$         check(enabled() == (expected == 54), "FGA effective rule state " + expected);
//$$         ShulkerBoxBlockEntity block = new ShulkerBoxBlockEntity(
//$$                 BlockPos.ZERO, Blocks.SHULKER_BOX.defaultBlockState());
//$$         check(block.getContainerSize() == expected, "actual AMS block capacity " + expected);
//$$         Method size = FullShulkerBoxCraftingManager.class.getDeclaredMethod("shulkerSize");
//$$         size.setAccessible(true);
//$$         check((int) size.invoke(null) == expected, "FGA crafting capacity " + expected);
//$$         craft(server, expected);
//$$     }
//$$
//$$     private static void rejectMismatch(MinecraftServer server, int actualCapacity) {
//$$         ServerPlayer player = new ServerPlayer(server, server.overworld(),
//$$                 new GameProfile(UUID.randomUUID(), "AmsMismatchProbe"), ClientInformation.createDefault());
//$$         AbstractContainerMenu menu = new AbstractContainerMenu(null, 0) {
//$$             @Override public ItemStack quickMoveStack(Player actor, int slot) { return ItemStack.EMPTY; }
//$$             @Override public boolean stillValid(Player actor) { return true; }
//$$         };
//$$         player.containerMenu = menu;
//$$         TransientCraftingContainer input = new TransientCraftingContainer(menu, 2, 2);
//$$         NonNullList<ItemStack> materials = NonNullList.withSize(actualCapacity, ItemStack.EMPTY);
//$$         for (int slot = 0; slot < actualCapacity; slot++) {
//$$             materials.set(slot, new ItemStack(Items.OAK_LOG, 64));
//$$         }
//$$         ItemStack box = new ItemStack(Items.SHULKER_BOX);
//$$         box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(materials));
//$$         input.setItem(0, box);
//$$         ResultContainer result = new ResultContainer();
//$$         String original = FGASettings.fullShulkerBoxCrafting;
//$$         try {
//$$             FGASettings.fullShulkerBoxCrafting = "any";
//$$             FullShulkerBoxCraftingManager.updateResult(menu, server.overworld(), player, input, result);
//$$             check(result.getItem(0).isEmpty(), "mismatched capacity has no custom crafting result");
//$$             check(!FullShulkerBoxCraftingManager.mayTake(input, player), "mismatched craft cannot commit");
//$$             check(input.getItem(0).getOrDefault(DataComponents.CONTAINER,
//$$                     ItemContainerContents.EMPTY).equals(box.get(DataComponents.CONTAINER)),
//$$                     "rejected craft preserves source contents");
//$$             check(FullShulkerBoxCraftingManager.stonecutterBoxContent(server.overworld(), box).isEmpty(),
//$$                     "mismatched capacity has no stonecutter result");
//$$         } finally {
//$$             FGASettings.fullShulkerBoxCrafting = original;
//$$             FullShulkerBoxCraftingManager.clear();
//$$         }
//$$     }
//$$
//$$     private static void craft(MinecraftServer server, int capacity) {
//$$         ServerPlayer player = new ServerPlayer(server, server.overworld(),
//$$                 new GameProfile(UUID.randomUUID(), "AmsCraftProbe"), ClientInformation.createDefault());
//$$         AbstractContainerMenu menu = new AbstractContainerMenu(null, 0) {
//$$             @Override public ItemStack quickMoveStack(Player actor, int slot) { return ItemStack.EMPTY; }
//$$             @Override public boolean stillValid(Player actor) { return true; }
//$$         };
//$$         player.containerMenu = menu;
//$$         TransientCraftingContainer input = new TransientCraftingContainer(menu, 2, 2);
//$$         NonNullList<ItemStack> materials = NonNullList.withSize(capacity, ItemStack.EMPTY);
//$$         for (int slot = 0; slot < capacity; slot++) materials.set(slot, new ItemStack(Items.OAK_LOG, 64));
//$$         ItemStack box = new ItemStack(Items.SHULKER_BOX);
//$$         box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(materials));
//$$         input.setItem(0, box);
//$$         for (int slot = 0; slot < 3; slot++) player.getInventory().setItem(slot, new ItemStack(Items.SHULKER_BOX));
//$$         ResultContainer result = new ResultContainer();
//$$         String original = FGASettings.fullShulkerBoxCrafting;
//$$         try {
//$$             FGASettings.fullShulkerBoxCrafting = "false";
//$$             FullShulkerBoxCraftingManager.updateResult(menu, server.overworld(), player, input, result);
//$$             check(result.getItem(0).isEmpty(), "disabled FGA produces no custom result");
//$$             FGASettings.fullShulkerBoxCrafting = "any";
//$$             FullShulkerBoxCraftingManager.updateResult(menu, server.overworld(), player, input, result);
//$$             check(!result.getItem(0).isEmpty(), "log-to-plank recipe available");
//$$             check(FullShulkerBoxCraftingManager.mayTake(input, player), "craft can commit");
//$$             player.getInventory().setItem(4, result.getItem(0).copy());
//$$             result.setItem(0, ItemStack.EMPTY);
//$$             check(FullShulkerBoxCraftingManager.take(input, player), "craft committed");
//$$             check(input.getItem(0).isEmpty(), "source box consumed");
//$$             int boxes = 0;
//$$             long total = 0;
//$$             for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
//$$                 ItemStack output = player.getInventory().getItem(slot);
//$$                 if (output.isEmpty()) continue;
//$$                 check(output.is(Items.SHULKER_BOX), "only output boxes remain");
//$$                 NonNullList<ItemStack> contents = NonNullList.withSize(54, ItemStack.EMPTY);
//$$                 output.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(contents);
//$$                 int occupied = 0;
//$$                 for (ItemStack item : contents) {
//$$                     if (item.isEmpty()) continue;
//$$                     check(item.is(Items.OAK_PLANKS) && item.getCount() == 64, "output item and stack count");
//$$                     occupied++;
//$$                     total += item.getCount();
//$$                 }
//$$                 check(occupied == capacity, "output preserves capacity including slot 53");
//$$                 placeAndCheck(server, player, output, capacity, slot);
//$$                 boxes += output.getCount();
//$$             }
//$$             check(boxes == 4, "one log box yields four plank boxes");
//$$             check(total == (long) capacity * 64 * 4, "all material conserved");
//$$         } finally {
//$$             FGASettings.fullShulkerBoxCrafting = original;
//$$             FullShulkerBoxCraftingManager.clear();
//$$         }
//$$     }
//$$
//$$     private static void placeAndCheck(MinecraftServer server, ServerPlayer player,
//$$                                       ItemStack output, int capacity, int slot) {
//$$         BlockPos support = new BlockPos(slot + 4, 80, 4);
//$$         BlockPos target = support.above();
//$$         server.overworld().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
//$$         server.overworld().setBlock(target, Blocks.AIR.defaultBlockState(), 3);
//$$         BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(support).add(0, 0.5, 0),
//$$                 Direction.UP, support, false);
//$$         InteractionResult placement = ((BlockItem) output.getItem()).place(
//$$                 new BlockPlaceContext(player, InteractionHand.MAIN_HAND, output.copy(), hit));
//$$         check(placement.consumesAction(), "crafted output can be placed");
//$$         ShulkerBoxBlockEntity placed = (ShulkerBoxBlockEntity) server.overworld().getBlockEntity(target);
//$$         check(placed != null && placed.getContainerSize() == capacity,
//$$                 "placed output uses expected capacity");
//$$         long placedTotal = 0;
//$$         for (int index = 0; index < capacity; index++) {
//$$             ItemStack item = placed.getItem(index);
//$$             check(item.is(Items.OAK_PLANKS) && item.getCount() == 64,
//$$                     "placed output retains slot " + index);
//$$             placedTotal += item.getCount();
//$$         }
//$$         check(placedTotal == (long) capacity * 64, "placed output retains all items");
//$$     }
//$$
//$$     private static boolean enabled() throws Exception {
//$$         Method method = Class.forName("carpet.fga.AmsLargeShulkerBoxCompat").getDeclaredMethod("isEnabled");
//$$         method.setAccessible(true);
//$$         return (boolean) method.invoke(null);
//$$     }
//$$
//$$     private static void check(boolean value, String name) {
//$$         if (!value) throw new AssertionError(name);
//$$         checks++;
//$$     }
//$$ }
//#endif
