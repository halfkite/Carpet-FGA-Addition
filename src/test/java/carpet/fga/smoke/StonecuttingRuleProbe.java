//#if MC == 26.3
//$$ package carpet.fga.smoke;
//$$
//$$ import carpet.fga.FGASettings;
//$$ import carpet.patches.EntityPlayerMPFake;
//$$ import io.netty.buffer.Unpooled;
//$$ import net.fabricmc.api.DedicatedServerModInitializer;
//$$ import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
//$$ import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//$$ import net.minecraft.network.RegistryFriendlyByteBuf;
//$$ import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.players.NameAndId;
//$$ import net.minecraft.world.inventory.ContainerLevelAccess;
//$$ import net.minecraft.world.inventory.StonecutterMenu;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.item.Items;
//$$ import net.minecraft.world.level.GameType;
//$$ import net.minecraft.world.level.Level;
//$$ import net.minecraft.world.phys.Vec3;
//$$ import java.util.concurrent.CompletableFuture;
//$$
//$$ public final class StonecuttingRuleProbe implements DedicatedServerModInitializer {
//$$     private boolean done;
//$$     private int ticks;
//$$     public void onInitializeServer() {
//$$         ServerLifecycleEvents.SERVER_STARTED.register(server -> {
//$$             server.services().nameToIdCache().add(NameAndId.createOffline("FgaRecipeProbe"));
//$$             if (!EntityPlayerMPFake.createFake("FgaRecipeProbe", server, new Vec3(0, 120, 0), 0, 0,
//$$                     Level.OVERWORLD, GameType.SURVIVAL, false)) throw new AssertionError("could not create test player");
//$$         });
//$$         ServerTickEvents.END_SERVER_TICK.register(server -> {
//$$             if (done) return;
//$$             ServerPlayer player = server.getPlayerList().getPlayerByName("FgaRecipeProbe");
//$$             if (player == null && ++ticks < 200) return;
//$$             done = true;
//$$             try {
//$$                 if (player == null) throw new AssertionError("test player did not join");
//$$                 run(server);
//$$                 testMenus(server, player);
//$$                 System.out.println("FGA_ISSUE26_PASS: recipe filtering, packet round trips and live server menus");
//$$             } catch (Throwable error) {
//$$                 System.err.println("FGA_ISSUE26_FAIL: " + error);
//$$                 error.printStackTrace();
//$$             } finally {
//$$                 server.halt(false);
//$$             }
//$$         });
//$$     }
//$$
//$$     private static void testMenus(MinecraftServer server, ServerPlayer player) {
//$$         String[] rules = {"lightSourceStonecuttingRecipes", "woodStonecuttingRecipes"};
//$$         var inputs = new net.minecraft.world.item.Item[]{Items.GLOWSTONE, Items.OAK_LOG};
//$$         var access = ContainerLevelAccess.create(player.level(), player.blockPosition());
//$$         for (int index = 0; index < rules.length; index++) {
//$$             String rule = rules[index];
//$$             var input = inputs[index];
//$$             server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "carpet " + rule + " false");
//$$             var disabled = new StonecutterMenu(10, player.getInventory(), access);
//$$             disabled.getSlot(0).set(new ItemStack(input));
//$$             if (disabled.getVisibleRecipes().size() != 0) throw new AssertionError("disabled server menu still contains " + rule);
//$$             server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "carpet " + rule + " true");
//$$             var enabled = new StonecutterMenu(11, player.getInventory(), access);
//$$             player.containerMenu = enabled;
//$$             enabled.getSlot(0).set(new ItemStack(input));
//$$             if (enabled.getVisibleRecipes().isEmpty()) throw new AssertionError("enabled server menu is empty: " + rule);
//$$             // Pick planks explicitly: barrel/chest recipes can require multiple logs.
//$$             int selection = index == 0 ? 0 : java.util.stream.IntStream.range(0, enabled.getVisibleRecipes().size())
//$$                     .filter(i -> enabled.getVisibleRecipes().entries().get(i).recipe().recipe().orElseThrow()
//$$                             .id().identifier().getPath().contains("_to_oak_planks_"))
//$$                     .findFirst().orElseThrow(() -> new AssertionError("oak plank recipe missing"));
//$$             enabled.clickMenuButton(player, selection);
//$$             if (enabled.getSlot(1).getItem().isEmpty()) throw new AssertionError("enabled recipe has no output: " + rule);
//$$             int before = player.getInventory().countItem(input);
//$$             server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "carpet " + rule + " false");
//$$             if (player.containerMenu != player.inventoryMenu) throw new AssertionError("stale menu after disabling " + rule);
//$$             if (player.getInventory().countItem(input) != before + 1) throw new AssertionError("input was not returned: " + rule);
//$$             if (!enabled.getSlot(0).getItem().isEmpty() || !enabled.getSlot(1).getItem().isEmpty()) throw new AssertionError("stale input or output: " + rule);
//$$             System.out.println("FGA_ISSUE26_CASE: live menu toggle, output cleared and input returned: " + rule);
//$$         }
//$$         var vanilla = new StonecutterMenu(12, player.getInventory(), access);
//$$         vanilla.getSlot(0).set(new ItemStack(Items.STONE));
//$$         if (vanilla.getVisibleRecipes().isEmpty()) throw new AssertionError("vanilla stonecutting disappeared");
//$$         vanilla.clickMenuButton(player, 0);
//$$         if (vanilla.getSlot(1).getItem().isEmpty()) throw new AssertionError("vanilla stonecutting output disappeared");
//$$     }
//$$
//$$     private static void run(MinecraftServer server) {
//$$         int disabledSize = -1;
//$$         long vanillaCount = server.getRecipeManager().getSynchronizedStonecutterRecipes().entries().stream()
//$$                 .filter(entry -> entry.recipe().recipe().orElseThrow().id().identifier().getNamespace().equals("minecraft")).count();
//$$         for (boolean enabled : new boolean[]{false, true, false, true, false}) {
//$$             for (String rule : new String[]{"woodStonecuttingRecipes", "lightSourceStonecuttingRecipes"}) {
//$$                 server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "carpet " + rule + " " + enabled);
//$$             }
//$$             if (FGASettings.woodStonecuttingRecipes != enabled || FGASettings.lightSourceStonecuttingRecipes != enabled) {
//$$                 throw new AssertionError("rule observer did not finish");
//$$             }
//$$             var packet = new ClientboundUpdateRecipesPacket(server.getRecipeManager().getSynchronizedItemProperties(),
//$$                     server.getRecipeManager().getSynchronizedStonecutterRecipes());
//$$             int size = packet.stonecutterRecipes().size();
//$$             long packetVanillaCount = packet.stonecutterRecipes().entries().stream()
//$$                     .filter(entry -> entry.recipe().recipe().orElseThrow().id().identifier().getNamespace().equals("minecraft")).count();
//$$             if (packetVanillaCount != vanillaCount) throw new AssertionError("vanilla recipes changed");
//$$             if (disabledSize < 0) disabledSize = size;
//$$             if (enabled ? size <= disabledSize : size != disabledSize) throw new AssertionError("incorrect recipe count " + size);
//$$             var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
//$$             try {
//$$                 ClientboundUpdateRecipesPacket.STREAM_CODEC.encode(buffer, packet);
//$$                 var decoded = CompletableFuture.supplyAsync(() -> ClientboundUpdateRecipesPacket.STREAM_CODEC.decode(buffer)).join();
//$$                 if (decoded.stonecutterRecipes().size() != size || buffer.isReadable()) throw new AssertionError("packet round trip mismatch");
//$$                 if (decoded.stonecutterRecipes().entries().stream().anyMatch(entry -> entry.recipe().recipe().isPresent())) {
//$$                     throw new AssertionError("wire recipes unexpectedly retain server recipe holders");
//$$                 }
//$$             } finally {
//$$                 buffer.release();
//$$             }
//$$             System.out.println("FGA_ISSUE26_CASE: enabled=" + enabled + ", recipes=" + size);
//$$         }
//$$     }
//$$ }
//#endif
