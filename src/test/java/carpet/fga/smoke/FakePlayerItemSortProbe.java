//#if MC == 26.3
//$$ package carpet.fga.smoke;
//$$
//$$ import carpet.fga.FGASettings;
//$$ import carpet.fga.FakePlayerItemSortConfig;
//$$ import carpet.fga.FakePlayerItemSortManager;
//$$ import carpet.patches.EntityPlayerMPFake;
//$$ import com.mojang.brigadier.arguments.StringArgumentType;
//$$ import net.fabricmc.api.DedicatedServerModInitializer;
//$$ import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
//$$ import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//$$ import net.minecraft.commands.Commands;
//$$ import net.minecraft.nbt.CompoundTag;
//$$ import net.minecraft.nbt.NbtAccounter;
//$$ import net.minecraft.nbt.NbtIo;
//$$ import net.minecraft.nbt.NbtOps;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.players.NameAndId;
//$$ import net.minecraft.world.item.Item;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.item.Items;
//$$ import net.minecraft.world.level.storage.LevelResource;
//$$
//$$ import java.nio.charset.StandardCharsets;
//$$ import java.nio.file.Files;
//$$ import java.nio.file.Path;
//$$ import java.util.UUID;
//$$
//$$ /** Test-only inventory conservation probe; never package into the distributable mod. */
//$$ public final class FakePlayerItemSortProbe implements DedicatedServerModInitializer {
//$$     private static Run run;
//$$
//$$     @Override
//$$     public void onInitializeServer() {
//$$         CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
//$$                 dispatcher.register(Commands.literal("fgaSortProbe")
//$$                         .then(Commands.argument("mode", StringArgumentType.word())
//$$                                 .then(Commands.argument("source", StringArgumentType.word())
//$$                                         .executes(context -> begin(context.getSource().getServer(),
//$$                                                 StringArgumentType.getString(context, "mode"),
//$$                                                 StringArgumentType.getString(context, "source")))))));
//$$         ServerTickEvents.END_SERVER_TICK.register(FakePlayerItemSortProbe::check);
//$$     }
//$$
//$$     private static int begin(MinecraftServer server, String mode, String sourceName) {
//$$         if (run != null) return fail("previous probe is still active");
//$$         if (!mode.equals("quickopen") && !mode.equals("summon")) return fail("unknown mode " + mode);
//$$         ServerPlayer source = server.getPlayerList().getPlayerByName(sourceName);
//$$         if (!(source instanceof EntityPlayerMPFake)) return fail("source is not an online fake player");
//$$         Item item = mode.equals("quickopen") ? Items.STONE : Items.COBBLESTONE;
//$$         String target = mode.equals("quickopen") ? "bulk_stone" : "bulk_cobblestone";
//$$         if (!source.getInventory().getItem(0).isEmpty()) return fail("source slot 0 is not empty");
//$$         if (server.getPlayerList().getPlayerByName(target) != null || offlineCount(server, target, item) != 0) {
//$$             return fail("target is not empty before test: " + target);
//$$         }
//$$         try {
//$$             FakePlayerItemSortConfig.setMode(mode);
//$$         } catch (Exception exception) {
//$$             return fail("could not set mode: " + exception);
//$$         }
//$$         FGASettings.fakePlayerItemSort = true;
//$$         FGASettings.fakePlayerNameLength = 64;
//$$         FGASettings.fakePlayerProfilePreload = "always";
//$$         server.services().nameToIdCache().add(NameAndId.createOffline(target));
//$$         source.getInventory().setItem(0, new ItemStack(item, 16));
//$$         source.getInventory().setChanged();
//$$         StringBuilder error = new StringBuilder();
//$$         if (!FakePlayerItemSortManager.start(source, false, null, error)) return fail(error.toString());
//$$         run = new Run(mode, sourceName, target, item, server.getTickCount(), false);
//$$         System.out.println("FGA_SORT_PROBE_BEGIN: " + mode);
//$$         return 1;
//$$     }
//$$
//$$     private static void check(MinecraftServer server) {
//$$         Run current = run;
//$$         if (current == null) return;
//$$         ServerPlayer source = server.getPlayerList().getPlayerByName(current.source());
//$$         if (source == null) { finishFailure("source went offline"); return; }
//$$         ServerPlayer target = server.getPlayerList().getPlayerByName(current.target());
//$$         boolean sawOnline = current.sawOnline() || target instanceof EntityPlayerMPFake;
//$$         if (sawOnline != current.sawOnline()) {
//$$             run = current = new Run(current.mode(), current.source(), current.target(), current.item(), current.started(), true);
//$$         }
//$$         int sourceCount = count(source, current.item());
//$$         int onlineCount = target == null ? 0 : count(target, current.item());
//$$         int offlineCount = target == null ? offlineCount(server, current.target(), current.item()) : 0;
//$$         if (sourceCount == 0 && onlineCount + offlineCount == 16) {
//$$             if (current.mode().equals("quickopen") && sawOnline) {
//$$                 finishFailure("quickopen unexpectedly summoned a target"); return;
//$$             }
//$$             System.out.println("FGA_SORT_PROBE_PASS: mode=" + current.mode() + " source=" + sourceCount
//$$                     + " target=" + (onlineCount + offlineCount) + " sawOnline=" + sawOnline);
//$$             run = null;
//$$             return;
//$$         }
//$$         if (server.getTickCount() - current.started() > 1200) {
//$$             finishFailure("timeout mode=" + current.mode() + " source=" + sourceCount
//$$                     + " online=" + onlineCount + " offline=" + offlineCount + " sawOnline=" + sawOnline
//$$                     + " status=" + FakePlayerItemSortManager.status());
//$$         }
//$$     }
//$$
//$$     private static int count(ServerPlayer player, Item item) {
//$$         int result = 0;
//$$         for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
//$$             ItemStack stack = player.getInventory().getItem(slot);
//$$             if (stack.is(item)) result += stack.getCount();
//$$         }
//$$         return result;
//$$     }
//$$
//$$     private static int offlineCount(MinecraftServer server, String name, Item item) {
//$$         UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
//$$         Path path = server.getWorldPath(LevelResource.ROOT).resolve("playerdata").resolve(id + ".dat");
//$$         if (!Files.isRegularFile(path)) return 0;
//$$         try {
//$$             CompoundTag data = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
//$$             int total = 0;
//$$             for (int index = 0; index < data.getListOrEmpty("Inventory").size(); index++) {
//$$                 ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(
//$$                         server.registryAccess().createSerializationContext(NbtOps.INSTANCE),
//$$                         data.getListOrEmpty("Inventory").getCompoundOrEmpty(index)).result().orElse(ItemStack.EMPTY);
//$$                 if (stack.is(item)) total += stack.getCount();
//$$             }
//$$             return total;
//$$         } catch (Exception exception) {
//$$             throw new IllegalStateException("could not read isolated sorter playerdata " + path, exception);
//$$         }
//$$     }
//$$
//$$     private static int fail(String reason) {
//$$         System.err.println("FGA_SORT_PROBE_FAIL: " + reason);
//$$         return 0;
//$$     }
//$$
//$$     private static void finishFailure(String reason) {
//$$         fail(reason);
//$$         run = null;
//$$     }
//$$
//$$     private record Run(String mode, String source, String target, Item item, int started, boolean sawOnline) {}
//$$ }
//#endif
