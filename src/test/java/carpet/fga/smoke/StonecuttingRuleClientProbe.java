//#if MC == 26.3
//$$ package carpet.fga.smoke;
//$$
//$$ import net.fabricmc.api.ClientModInitializer;
//$$ import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//$$ import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.world.SimpleMenuProvider;
//$$ import net.minecraft.world.inventory.ContainerLevelAccess;
//$$ import net.minecraft.world.inventory.StonecutterMenu;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.item.Items;
//$$ import net.minecraft.world.level.block.Blocks;
//$$ import java.util.concurrent.CompletableFuture;
//$$
//$$ /** Opens only the copied test save selected by the smoke runner's quick-play arguments. */
//$$ public final class StonecuttingRuleClientProbe implements ClientModInitializer {
//$$     private int ticks;
//$$     private int nextPhase = 60;
//$$     private int phase;
//$$     private int failures;
//$$     private boolean joined;
//$$     private boolean stopping;
//$$     private CompletableFuture<Void> pending;
//$$
//$$     public void onInitializeClient() {
//$$         ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> joined = true);
//$$         ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
//$$             if (!stopping) {
//$$                 fail("unexpected disconnect, phase=" + phase);
//$$                 stopping = true;
//$$                 client.stop();
//$$             }
//$$         });
//$$         ClientTickEvents.END_CLIENT_TICK.register(client -> {
//$$             if (stopping) return;
//$$             if (++ticks > 2400) {
//$$                 fail("client probe timed out");
//$$                 stopping = true;
//$$                 client.stop();
//$$                 return;
//$$             }
//$$             if (!joined || client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
//$$             if (ticks < nextPhase) return;
//$$             if (pending != null) {
//$$                 if (!pending.isDone()) return;
//$$                 try {
//$$                     pending.join();
//$$                     int previous = phase - 1;
//$$                     boolean enabled = previous % 5 == 1 || previous % 5 == 3;
//$$                     var input = previous < 5 ? Items.GLOWSTONE : Items.OAK_LOG;
//$$                     int visible = client.level.recipeAccess().stonecutterRecipes().selectByInput(new ItemStack(input)).size();
//$$                     if (enabled ? visible == 0 : visible != 0) fail("client visible recipes=" + visible + ", phase=" + previous);
//$$                     if (!enabled && client.player.containerMenu instanceof StonecutterMenu) fail("stale client stonecutter menu");
//$$                     System.out.println("FGA_ISSUE26_CLIENT_CASE: phase=" + previous + ", enabled=" + enabled + ", visible=" + visible);
//$$                 } catch (RuntimeException error) {
//$$                     fail("server phase failed: " + error);
//$$                 }
//$$                 pending = null;
//$$             }
//$$             if (phase == 10) {
//$$                 if (failures == 0) System.out.println("FGA_ISSUE26_CLIENT_PASS: integrated server, recipe visibility and ten rule transitions");
//$$                 stopping = true;
//$$                 client.stop();
//$$                 return;
//$$             }
//$$             int current = phase++;
//$$             boolean enabled = current % 5 == 1 || current % 5 == 3;
//$$             var server = client.getSingleplayerServer();
//$$             pending = server.submit(() -> {
//$$                 var player = server.getPlayerList().getPlayers().getFirst();
//$$                 String rule = current < 5 ? "lightSourceStonecuttingRecipes" : "woodStonecuttingRecipes";
//$$                 var input = current < 5 ? Items.GLOWSTONE : Items.OAK_LOG;
//$$                 var previousMenu = player.containerMenu;
//$$                 int beforeInput = player.getInventory().countItem(input);
//$$                 server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "carpet " + rule + " " + enabled);
//$$                 if (!enabled && player.containerMenu instanceof StonecutterMenu) fail("stale server stonecutter menu");
//$$                 if (!enabled && previousMenu instanceof StonecutterMenu) {
//$$                     if (player.getInventory().countItem(input) != beforeInput + 1) fail("input not returned on rule change");
//$$                     if (!previousMenu.getSlot(1).getItem().isEmpty()) fail("stale output survived rule change");
//$$                 }
//$$                 var position = player.blockPosition().below();
//$$                 var level = player.level();
//$$                 var access = ContainerLevelAccess.create(level, position);
//$$                 var serverMenu = new StonecutterMenu(99, player.getInventory(), access);
//$$                 serverMenu.getSlot(0).set(new ItemStack(input));
//$$                 int available = serverMenu.getVisibleRecipes().size();
//$$                 if (enabled ? available == 0 : available != 0) fail("server menu recipes=" + available);
//$$                 if (enabled) {
//$$                     level.setBlockAndUpdate(position, Blocks.STONECUTTER.defaultBlockState());
//$$                     player.openMenu(new SimpleMenuProvider((id, inventory, who) -> new StonecutterMenu(id, inventory,
//$$                             access), Component.literal("FGA issue26 test")));
//$$                     player.containerMenu.getSlot(0).set(new ItemStack(current < 5 ? Items.GLOWSTONE : Items.OAK_LOG));
//$$                     StonecutterMenu menu = (StonecutterMenu) player.containerMenu;
//$$                     int selection = current < 5 ? 0 : java.util.stream.IntStream.range(0, menu.getVisibleRecipes().size())
//$$                             .filter(i -> menu.getVisibleRecipes().entries().get(i).recipe().recipe().orElseThrow()
//$$                                     .id().identifier().getPath().contains("_to_oak_planks_"))
//$$                             .findFirst().orElseThrow(() -> new AssertionError("oak plank recipe missing"));
//$$                     menu.clickMenuButton(player, selection);
//$$                 }
//$$                 return null;
//$$             });
//$$             nextPhase = ticks + 40;
//$$         });
//$$     }
//$$
//$$     private synchronized void fail(String message) {
//$$         failures++;
//$$         System.err.println("FGA_ISSUE26_CLIENT_FAIL: " + message);
//$$     }
//$$ }
//#endif
