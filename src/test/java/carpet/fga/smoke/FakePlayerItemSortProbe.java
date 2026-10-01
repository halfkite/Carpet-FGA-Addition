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
//$$ import net.minecraft.core.NonNullList;
//$$ import net.minecraft.core.UUIDUtil;
//$$ import net.minecraft.core.component.DataComponents;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.players.NameAndId;
//$$ import net.minecraft.server.players.OldUsersConverter;
//$$ import net.minecraft.world.item.Item;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.item.Items;
//$$ import net.minecraft.world.item.component.ItemContainerContents;
//$$ import net.minecraft.world.level.storage.LevelResource;
//$$
//$$ import java.nio.charset.StandardCharsets;
//$$ import java.nio.file.Files;
//$$ import java.nio.file.Path;
//$$ import java.lang.reflect.Field;
//$$ import java.lang.reflect.Method;
//$$ import java.util.Set;
//$$ import java.util.UUID;
//$$
//$$ /** Test-only inventory conservation probe; never package into the distributable mod. */
//$$ public final class FakePlayerItemSortProbe implements DedicatedServerModInitializer {
//$$     private static final int MAIN_SLOTS = 36;
//$$     private static Run run;
//$$     private static PerfRun perf;
//$$     private static DepotRound depotRound;
//$$     private static DepotBoxed depotBoxed;
//$$     private static long tickStarted;
//$$     private static String blockedSource;
//$$     private static long blockedPause;
//$$     private static int blockedAttempts;
//$$
//$$     @Override
//$$     public void onInitializeServer() {
//$$         ServerTickEvents.START_SERVER_TICK.register(server -> tickStarted = System.nanoTime());
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
//$$     private static int checkGroupedPermissions(MinecraftServer server, String sourceName) {
//$$         ServerPlayer player = server.getPlayerList().getPlayerByName(sourceName);
//$$         if (player == null) return fail("permission fixture player missing");
//$$         var source = player.createCommandSourceStack();
//$$         if (carpet.fga.FGACompat.hasPermission(source, 2)) return fail("permission fixture must not be OP");
//$$         String previousSpeed = FakePlayerItemSortConfig.snapshot().speed();
//$$         try {
//$$             FakePlayerItemSortConfig.setPermission("speed", sourceName, false);
//$$             FakePlayerItemSortConfig.setPermission("settings", sourceName, true);
//$$             if (server.getCommands().getDispatcher().execute("fga playersort set speed 7", source) != 1
//$$                     || !FakePlayerItemSortConfig.snapshot().speed().equals("7"))
//$$                 return fail("legacy settings grant no longer permits speed editing");
//$$             FakePlayerItemSortConfig.setPermission("settings", sourceName, false);
//$$             boolean rejected = false;
//$$             try { server.getCommands().getDispatcher().execute("fga playersort set speed 9", source); }
//$$             catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { rejected = true; }
//$$             if (!rejected || !FakePlayerItemSortConfig.snapshot().speed().equals("7"))
//$$                 return fail("revoked settings grant still permits editing");
//$$             System.out.println("FGA_SORT_PROBE_PASS: permission-layout legacy-grant=true revoke=true");
//$$             return 1;
//$$         } catch (Exception exception) { return fail("grouped permission check: " + exception); }
//$$         finally {
//$$             try { FakePlayerItemSortConfig.setOption("speed", previousSpeed); }
//$$             catch (Exception exception) { fail("could not restore permission fixture speed: " + exception); }
//$$         }
//$$     }
//$$
//$$     private static int checkCommandLayout(MinecraftServer server) {
//$$         var dispatcher = server.getCommands().getDispatcher();
//$$         var source = server.createCommandSourceStack();
//$$         var root = dispatcher.getCompletionSuggestions(dispatcher.parse("fga playersort ", source)).join()
//$$                 .getList().stream().map(suggestion -> suggestion.getText()).toList();
//$$         if (!root.containsAll(java.util.List.of("set", "language", "whitelist", "stock", "help")))
//$$             return fail("missing root commands: " + root);
//$$         for (String hidden : java.util.List.of("setting", "mode", "prefix", "quickShulker", "autoCraft",
//$$                 "summonNotices", "cleanOpenedTarget", "speed", "cpu", "dashboard", "format", "workers"))
//$$             if (root.contains(hidden)) return fail("setting still visible at root: " + hidden);
//$$         var settings = dispatcher.getCompletionSuggestions(dispatcher.parse("fga playersort set ", source)).join()
//$$                 .getList().stream().map(suggestion -> suggestion.getText()).toList();
//$$         if (!settings.containsAll(java.util.List.of("language", "mode", "summonNotices", "prefix", "quickShulker",
//$$                 "autoCraft", "whitelistMode", "cleanOpenedTarget", "speed", "cpu", "dashboard")))
//$$             return fail("missing grouped settings: " + settings);
//$$         var speeds = dispatcher.getCompletionSuggestions(dispatcher.parse("fga playersort set speed ", source)).join()
//$$                 .getList().stream().map(suggestion -> suggestion.getText()).toList();
//$$         if (!speeds.equals(java.util.List.of("4", "8", "16")) && !Set.copyOf(speeds).equals(Set.of("4", "8", "16")))
//$$             return fail("unexpected speed suggestions: " + speeds);
//$$         System.out.println("FGA_SORT_PROBE_PASS: command-layout root-clean=true settings=11");
//$$         return 1;
//$$     }
//$$
//$$     private static int begin(MinecraftServer server, String mode, String sourceName) {
//$$         if (mode.equals("inventory-discovery")) return FakePlayerItemSortProbeDiscovery.start(server,sourceName);
//$$         if (mode.equals("inventory-api")) return FakePlayerItemSortProbeApi.start(server,sourceName);
//$$         if (mode.equals("command-layout")) return checkCommandLayout(server);
//$$         if (mode.equals("permission-layout")) return checkGroupedPermissions(server, sourceName);
//$$         if (mode.equals("blocked-retry") || mode.equals("blocked-cost")) return beginBlocked(server, sourceName, mode.equals("blocked-cost"));
//$$         if (mode.equals("verify-quick-login")) {
//$$             ServerPlayer target = server.getPlayerList().getPlayerByName(sourceName);
//$$             if (target == null || count(target, Items.EMERALD) != 448) return fail("quickopen vanilla login lost inventory");
//$$             System.out.println("FGA_SORT_PROBE_PASS: quickopen-vanilla-login items=448");
//$$             return 1;
//$$         }
//$$         if (mode.startsWith("perf")) return beginPerf(server, mode, sourceName);
//$$         if (mode.equals("profile-control")) return checkProfileLookup(server, sourceName, false);
//$$         if (mode.equals("profile-seed")) return checkProfileLookup(server, sourceName, true);
//$$         if (run != null) return fail("previous probe is still active");
//$$         if (mode.equals("loose-speed")) return beginLooseSpeed(server, sourceName);
//$$         if (mode.equals("depot-restock")) return beginDepotRestock(server, sourceName);
//$$         if (mode.equals("depot-round")) return beginDepotRound(server, sourceName);
//$$         if (mode.equals("depot-boxed")) return beginDepotBoxed(server, sourceName);
//$$         if (mode.equals("depot-check")) return checkDepotCleanup(server, sourceName);
//$$         if (mode.endsWith("-box")) return beginBox(server, mode, sourceName);
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
//$$     /**
//$$      * Reproduces and then clears the blocking Mojang profile lookup carpet performs before it creates a
//$$      * fake player. {@code profile-control} measures the unseeded lookup, {@code profile-seed} lets the
//$$      * sorter seed the deterministic offline profile first and measures the same lookup again.
//$$      * The name must stay within 16 characters, otherwise the vanilla helper skips the lookup entirely.
//$$      */
//$$     private static int checkProfileLookup(MinecraftServer server, String name, boolean seed) {
//$$         if (seed) {
//$$             try {
//$$                 Method method = FakePlayerItemSortManager.class.getDeclaredMethod(
//$$                         "seedSorterTargetProfile", MinecraftServer.class, String.class);
//$$                 method.setAccessible(true);
//$$                 method.invoke(null, server, name);
//$$             } catch (ReflectiveOperationException exception) {
//$$                 return fail("sorter profile seeding is unavailable: " + exception);
//$$             }
//$$         }
//$$         long started = System.nanoTime();
//$$         UUID resolved;
//$$         try {
//$$             resolved = OldUsersConverter.convertMobOwnerIfNecessary(server, name);
//$$         } catch (RuntimeException exception) {
//$$             return fail("profile lookup failed for " + name + ": " + exception);
//$$         }
//$$         double ms = (System.nanoTime() - started) / 1_000_000.0;
//$$         String elapsed = String.format(java.util.Locale.ROOT, "%.3f", ms);
//$$         if (!seed) {
//$$             if (server.usesAuthentication() && resolved != null) {
//$$                 return fail("unseeded profile lookup unexpectedly resolved " + name);
//$$             }
//$$             System.out.println("FGA_SORT_PROBE_PASS: profile-control name=" + name
//$$                     + " auth=" + server.usesAuthentication() + " resolved=" + resolved + " blockedMs=" + elapsed);
//$$             return 1;
//$$         }
//$$         UUID offline = UUIDUtil.createOfflinePlayerUUID(name);
//$$         if (resolved == null || !resolved.equals(offline)) {
//$$             return fail("seeded profile lookup resolved " + resolved + " instead of " + offline + " for " + name);
//$$         }
//$$         if (ms > 250.0) return fail("seeded profile lookup still blocked for " + elapsed + "ms");
//$$         System.out.println("FGA_SORT_PROBE_PASS: profile-seed name=" + name
//$$                 + " auth=" + server.usesAuthentication() + " resolved=" + resolved + " lookupMs=" + elapsed);
//$$         return 1;
//$$     }
//$$
//$$     private static int beginLooseSpeed(MinecraftServer server, String sourceName) {
//$$         ServerPlayer source = server.getPlayerList().getPlayerByName(sourceName);
//$$         if (!(source instanceof EntityPlayerMPFake)) return fail("source is not an online fake player");
//$$         String target = "fga_probe_speed_emerald";
//$$         if (!source.getInventory().getItem(0).isEmpty() || server.getPlayerList().getPlayerByName(target) != null
//$$                 || offlineCount(server, target, Items.EMERALD) != 0) return fail("loose-speed source/target is not empty");
//$$         try {
//$$             FakePlayerItemSortConfig.setMode("quickopen");
//$$             FakePlayerItemSortConfig.setOption("targetLanguage", "english");
//$$             FakePlayerItemSortConfig.setOption("quickShulker", "false");
//$$             FakePlayerItemSortConfig.setOption("speed", "16");
//$$             FakePlayerItemSortConfig.setPrefix("fga_probe_speed_");
//$$         } catch (Exception exception) {
//$$             return fail("could not configure loose-speed probe: " + exception);
//$$         }
//$$         FGASettings.fakePlayerItemSort = true;
//$$         source.getInventory().setItem(0, new ItemStack(Items.EMERALD, 64));
//$$         source.getInventory().setChanged();
//$$         server.services().nameToIdCache().add(NameAndId.createOffline(target));
//$$         StringBuilder error = new StringBuilder();
//$$         if (!FakePlayerItemSortManager.start(source, false, null, error)) return fail(error.toString());
//$$         run = new Run("loose-speed", sourceName, target, Items.STONE, server.getTickCount(), false);
//$$         System.out.println("FGA_SORT_PROBE_BEGIN: loose-speed");
//$$         return 1;
//$$     }
//$$
//$$     private static int beginDepotRestock(MinecraftServer server, String sourceName) {
//$$         ServerPlayer source = server.getPlayerList().getPlayerByName(sourceName);
//$$         if (!(source instanceof EntityPlayerMPFake)) return fail("source is not an online fake player");
//$$         if (server.getPlayerList().getPlayerByName("box_restock") != null) return fail("depot fake is already online");
//$$         try {
//$$             FakePlayerItemSortConfig.setOption("targetLanguage", "english");
//$$             FakePlayerItemSortConfig.setOption("shulkerRestock", "true");
//$$             server.services().nameToIdCache().add(NameAndId.createOffline("box_restock"));
//$$             Method restock = FakePlayerItemSortManager.class.getDeclaredMethod("restockDepot", UUID.class, UUID.class);
//$$             restock.setAccessible(true);
//$$             restock.invoke(null, source.getUUID(), null);
//$$         } catch (Exception exception) {
//$$             return fail("could not exercise depot restock lifecycle: " + exception);
//$$         }
//$$         System.out.println("FGA_SORT_PROBE_PASS: depot-restock-requested");
//$$         return 1;
//$$     }
//$$
//$$     /**
//$$      * Verifies the round-scoped depot lifetime: an auto-summoned depot must survive its restock
//$$      * attempt while a sorting job is still running, and log out once that round is over.
//$$      */
//$$     private static int beginDepotRound(MinecraftServer server, String sourceName) {
//$$         ServerPlayer source = server.getPlayerList().getPlayerByName(sourceName);
//$$         if (!(source instanceof EntityPlayerMPFake)) return fail("source is not an online fake player");
//$$         if (server.getPlayerList().getPlayerByName("box_restock") != null) return fail("depot fake is already online");
//$$         for (int slot = 0; slot < source.getInventory().getContainerSize(); slot++) {
//$$             if (!source.getInventory().getItem(slot).isEmpty()) return fail("depot-round source is not empty");
//$$         }
//$$         try {
//$$             FakePlayerItemSortConfig.setMode("quickopen");
//$$             FakePlayerItemSortConfig.setOption("targetLanguage", "english");
//$$             FakePlayerItemSortConfig.setOption("shulkerRestock", "true");
//$$             FakePlayerItemSortConfig.setOption("speed", "120");
//$$             FakePlayerItemSortConfig.setPrefix("fga_round_");
//$$             server.services().nameToIdCache().add(NameAndId.createOffline("box_restock"));
//$$             server.services().nameToIdCache().add(NameAndId.createOffline("fga_round_cobblestone"));
//$$             source.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
//$$             source.getInventory().setChanged();
//$$             FGASettings.fakePlayerItemSort = true;
//$$             StringBuilder error = new StringBuilder();
//$$             if (!FakePlayerItemSortManager.start(source, false, null, error)) return fail(error.toString());
//$$             Method restock = FakePlayerItemSortManager.class.getDeclaredMethod("restockDepot", UUID.class, UUID.class);
//$$             restock.setAccessible(true);
//$$             restock.invoke(null, source.getUUID(), null);
//$$         } catch (Exception exception) {
//$$             return fail("could not exercise the round-scoped depot: " + exception);
//$$         }
//$$         depotRound = new DepotRound(sourceName, server.getTickCount());
//$$         System.out.println("FGA_SORT_PROBE_BEGIN: depot-round");
//$$         return 1;
//$$     }
//$$
//$$     private static void checkDepotRound(MinecraftServer server) {
//$$         DepotRound round = depotRound;
//$$         ServerPlayer depot = server.getPlayerList().getPlayerByName("box_restock");
//$$         if (round.phase() == 0) {
//$$             if (depot == null) {
//$$                 if (server.getTickCount() - round.started() > 200) {
//$$                     depotRound = null;
//$$                     finishFailure("depot-round: depot never logged in");
//$$                 }
//$$                 return;
//$$             }
//$$             if (server.getTickCount() - round.started() < 60) return;
//$$             if (!(depot instanceof EntityPlayerMPFake)) {
//$$                 depotRound = null;
//$$                 finishFailure("depot-round: depot is not an auto-summoned fake player");
//$$                 return;
//$$             }
//$$             System.out.println("FGA_SORT_PROBE_PASS: depot-round online-while-sorting");
//$$             ServerPlayer source = server.getPlayerList().getPlayerByName(round.source());
//$$             if (source == null) {
//$$                 depotRound = null;
//$$                 finishFailure("depot-round: source went offline");
//$$                 return;
//$$             }
//$$             FakePlayerItemSortManager.stop(source);
//$$             for (int slot = 0; slot < source.getInventory().getContainerSize(); slot++) {
//$$                 source.getInventory().setItem(slot, ItemStack.EMPTY);
//$$             }
//$$             source.getInventory().setChanged();
//$$             depotRound = round.next(server.getTickCount());
//$$             return;
//$$         }
//$$         if (depot != null) {
//$$             if (server.getTickCount() - round.started() > 200) {
//$$                 depotRound = null;
//$$                 finishFailure("depot-round: depot stayed online after the round ended");
//$$             }
//$$             return;
//$$         }
//$$         depotRound = null;
//$$         System.out.println("FGA_SORT_PROBE_PASS: depot-round closed-after-round");
//$$     }
//$$
//$$     /**
//$$      * Reproduces the real depot layout: a restock fake whose 36 slots are all boxes full of logs
//$$      * and shells, with no loose material and no free slot. Taking an empty box must still work by
//$$      * consuming boxed material on demand instead of reporting a material shortage.
//$$      */
//$$     private static int beginDepotBoxed(MinecraftServer server, String sourceName) {
//$$         ServerPlayer source = server.getPlayerList().getPlayerByName(sourceName);
//$$         if (!(source instanceof EntityPlayerMPFake)) return fail("source is not an online fake player");
//$$         if (server.getPlayerList().getPlayerByName("box_restock") != null) return fail("depot fake is already online");
//$$         try {
//$$             FakePlayerItemSortConfig.setOption("targetLanguage", "english");
//$$             FakePlayerItemSortConfig.setOption("shulkerRestock", "true");
//$$             server.services().nameToIdCache().add(NameAndId.createOffline("box_restock"));
//$$             // A previous spawn may still hold the sorter's own retry marker, and a stale
//$$             // close-after-login marker would log the freshly spawned depot straight out again.
//$$             resetDepotSpawnState();
//$$             for (String field : new String[]{"AUTO_SPAWNED_DEPOTS", "AUTO_SPAWNED_DEPOT_NAMES", "DEPOT_CLOSE_AFTER_LOGIN"}) {
//$$                 Field tracked = FakePlayerItemSortManager.class.getDeclaredField(field);
//$$                 tracked.setAccessible(true);
//$$                 ((java.util.Collection<?>) tracked.get(null)).clear();
//$$             }
//$$             Method openCraft = FakePlayerItemSortManager.class.getDeclaredMethod(
//$$                     "openOnlineDepotForCraft", MinecraftServer.class, UUID.class, UUID.class);
//$$             openCraft.setAccessible(true);
//$$             openCraft.invoke(null, server, source.getUUID(), null);
//$$         } catch (Exception exception) {
//$$             return fail("depot-boxed probe: " + exception);
//$$         }
//$$         depotBoxed = new DepotBoxed(sourceName, server.getTickCount());
//$$         System.out.println("FGA_SORT_PROBE_BEGIN: depot-boxed");
//$$         return 1;
//$$     }
//$$
//$$     private static void checkDepotBoxed(MinecraftServer server) {
//$$         DepotBoxed state = depotBoxed;
//$$         ServerPlayer depot = server.getPlayerList().getPlayerByName("box_restock");
//$$         if (!(depot instanceof EntityPlayerMPFake)) {
//$$             if (server.getTickCount() - state.started() > 200) {
//$$                 depotBoxed = null;
//$$                 finishFailure("depot-boxed: depot never logged in");
//$$             }
//$$             return;
//$$         }
//$$         ServerPlayer source = server.getPlayerList().getPlayerByName(state.source());
//$$         if (source == null) {
//$$             depotBoxed = null;
//$$             finishFailure("depot-boxed: source went offline");
//$$             return;
//$$         }
//$$         depotBoxed = null;
//$$         try {
//$$             for (int slot = 0; slot < depot.getInventory().getContainerSize(); slot++) {
//$$                 depot.getInventory().setItem(slot, slot < MAIN_SLOTS
//$$                         ? materialBox(slot < 18 ? Items.OAK_LOG : Items.SHULKER_SHELL)
//$$                         : ItemStack.EMPTY);
//$$             }
//$$             depot.getInventory().setChanged();
//$$             int logsBefore = count(depot, Items.OAK_LOG);
//$$             int shellsBefore = count(depot, Items.SHULKER_SHELL);
//$$             if (logsBefore != 18 * 27 * 64 || shellsBefore != 18 * 27 * 64) {
//$$                 fail("boxed depot fixture is wrong: logs=" + logsBefore + " shells=" + shellsBefore);
//$$                 return;
//$$             }
//$$             Method take = FakePlayerItemSortManager.class.getDeclaredMethod(
//$$                     "takeEmptyShulkerFromDepot", UUID.class, UUID.class);
//$$             take.setAccessible(true);
//$$             ItemStack first = (ItemStack) take.invoke(null, source.getUUID(), null);
//$$             ItemStack second = (ItemStack) take.invoke(null, source.getUUID(), null);
//$$             if (!first.is(Items.SHULKER_BOX) || first.getCount() != 1) {
//$$                 fail("boxed depot material produced no box: " + first);
//$$                 return;
//$$             }
//$$             if (!second.is(Items.SHULKER_BOX) || second.getCount() != 1) {
//$$                 fail("second on-demand craft failed: " + second);
//$$                 return;
//$$             }
//$$             int logsUsed = logsBefore - count(depot, Items.OAK_LOG);
//$$             int shellsUsed = shellsBefore - count(depot, Items.SHULKER_SHELL);
//$$             if (logsUsed != 4 || shellsUsed != 4) {
//$$                 fail("boxed craft consumed logs=" + logsUsed + " shells=" + shellsUsed);
//$$                 return;
//$$             }
//$$             System.out.println("FGA_SORT_PROBE_PASS: depot-boxed crafted=2 logsUsed=" + logsUsed
//$$                     + " shellsUsed=" + shellsUsed);
//$$         } catch (Exception exception) {
//$$             fail("depot-boxed probe: " + exception);
//$$         } finally {
//$$             resetDepotSpawnState();
//$$             ServerPlayer online = server.getPlayerList().getPlayerByName("box_restock");
//$$             if (online instanceof EntityPlayerMPFake fake) {
//$$                 fake.kill(net.minecraft.network.chat.Component.literal("FGA depot probe finished"));
//$$             }
//$$         }
//$$     }
//$$
//$$     private static void resetDepotSpawnState() {
//$$         try {
//$$             Field pending = FakePlayerItemSortManager.class.getDeclaredField("PENDING_SORTER_SPAWNS");
//$$             pending.setAccessible(true);
//$$             ((java.util.Map<?, ?>) pending.get(null)).remove("box_restock");
//$$         } catch (ReflectiveOperationException ignored) {
//$$             // Test-only cleanup; a missing field must never hide the probe's real assertion.
//$$         }
//$$     }
//$$
//$$     private static ItemStack materialBox(Item item) {
//$$         NonNullList<ItemStack> contents = NonNullList.withSize(27, ItemStack.EMPTY);
//$$         for (int slot = 0; slot < contents.size(); slot++) contents.set(slot, new ItemStack(item, 64));
//$$         ItemStack box = new ItemStack(Items.SHULKER_BOX);
//$$         box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
//$$         return box;
//$$     }
//$$
//$$     private static int checkDepotCleanup(MinecraftServer server, String depotName) {
//$$         try {
//$$             if (server.getPlayerList().getPlayerByName(depotName) != null) return fail("restock fake remained online");
//$$             Field depotIds = FakePlayerItemSortManager.class.getDeclaredField("AUTO_SPAWNED_DEPOTS");
//$$             Field depotNames = FakePlayerItemSortManager.class.getDeclaredField("AUTO_SPAWNED_DEPOT_NAMES");
//$$             Field closeAfterLogin = FakePlayerItemSortManager.class.getDeclaredField("DEPOT_CLOSE_AFTER_LOGIN");
//$$             depotIds.setAccessible(true);
//$$             depotNames.setAccessible(true);
//$$             closeAfterLogin.setAccessible(true);
//$$             String key = depotName.toLowerCase(java.util.Locale.ROOT);
//$$             if (!((Set<?>) depotIds.get(null)).isEmpty() || ((Set<?>) depotNames.get(null)).contains(key)
//$$                     || ((Set<?>) closeAfterLogin.get(null)).contains(key)) return fail("restock lifecycle tracking was not cleared");
//$$         } catch (Exception exception) {
//$$             return fail("could not inspect depot cleanup: " + exception);
//$$         }
//$$         System.out.println("FGA_SORT_PROBE_PASS: depot-cleanup offline=true tracked=false");
//$$         return 1;
//$$     }
//$$
//$$     private static int beginBox(MinecraftServer server, String mode, String sourceName) {
//$$         if (!mode.equals("mixed-box") && !mode.equals("full-box") && !mode.equals("split-box")) {
//$$             return fail("unknown box scenario " + mode);
//$$         }
//$$         ServerPlayer source = server.getPlayerList().getPlayerByName(sourceName);
//$$         if (!(source instanceof EntityPlayerMPFake)) return fail("source is not an online fake player");
//$$         if (!source.getInventory().getItem(0).isEmpty()) return fail("source slot 0 is not empty");
//$$         String prefix = switch (mode) {
//$$             case "mixed-box" -> "fga_probe_";
//$$             case "full-box" -> "fga_probe_full_";
//$$             default -> "fga_probe_split_";
//$$         };
//$$         String target = switch (mode) {
//$$             case "mixed-box" -> "fga_probe_mixed_box";
//$$             case "full-box" -> "fga_probe_full_stone_box";
//$$             default -> "fga_probe_split_diamond";
//$$         };
//$$         if (server.getPlayerList().getPlayerByName(target) != null
//$$                 || offlineCount(server, target, Items.SHULKER_BOX) != 0) {
//$$             return fail("box target is not empty before test: " + target);
//$$         }
//$$         try {
//$$             FakePlayerItemSortConfig.setMode("quickopen");
//$$             FakePlayerItemSortConfig.setOption("targetLanguage", "english");
//$$             FakePlayerItemSortConfig.setOption("quickShulker", Boolean.toString(mode.equals("split-box")));
//$$             FakePlayerItemSortConfig.setOption("shulkerRestock", "false");
//$$             FakePlayerItemSortConfig.setPrefix(prefix);
//$$         } catch (Exception exception) {
//$$             return fail("could not configure box probe: " + exception);
//$$         }
//$$         FGASettings.fakePlayerItemSort = true;
//$$         FGASettings.fakePlayerNameLength = 64;
//$$         FGASettings.fgaUnicodeArgumentsSupport = true;
//$$         ItemStack box = makeBox(mode);
//$$         source.getInventory().setItem(0, box);
//$$         source.getInventory().setChanged();
//$$         for (String fakeName : mode.equals("split-box")
//$$                 ? new String[]{"fga_probe_split_diamond", "fga_probe_split_dirt", "fga_probe_split_shulker_box"}
//$$                 : new String[]{target}) {
//$$             server.services().nameToIdCache().add(NameAndId.createOffline(fakeName));
//$$         }
//$$         StringBuilder error = new StringBuilder();
//$$         if (!FakePlayerItemSortManager.start(source, false, null, error)) return fail(error.toString());
//$$         run = new Run(mode, sourceName, target, Items.SHULKER_BOX, server.getTickCount(), false);
//$$         System.out.println("FGA_SORT_PROBE_BEGIN: " + mode);
//$$         return 1;
//$$     }
//$$
//$$     private static ItemStack makeBox(String mode) {
//$$         NonNullList<ItemStack> contents = NonNullList.withSize(27, ItemStack.EMPTY);
//$$         if (mode.equals("full-box")) {
//$$             for (int slot = 0; slot < contents.size(); slot++) contents.set(slot, new ItemStack(Items.STONE, 64));
//$$         } else if (mode.equals("split-box")) {
//$$             contents.set(0, new ItemStack(Items.DIAMOND, 32));
//$$             contents.set(1, new ItemStack(Items.DIRT, 32));
//$$         } else {
//$$             contents.set(0, new ItemStack(Items.STONE, 32));
//$$             contents.set(1, new ItemStack(Items.DIRT, 32));
//$$         }
//$$         ItemStack box = new ItemStack(Items.SHULKER_BOX);
//$$         box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
//$$         return box;
//$$     }
//$$
//$$     private static void check(MinecraftServer server) {
//$$         FakePlayerItemSortProbeDiscovery.tick(server);
//$$         FakePlayerItemSortProbeApi.tick(server);
//$$         if (blockedSource != null) { checkBlocked(server); return; }
//$$         if (perf != null) { checkPerf(server); return; }
//$$         if (depotRound != null) { checkDepotRound(server); return; }
//$$         if (depotBoxed != null) { checkDepotBoxed(server); return; }
//$$         Run current = run;
//$$         if (current == null) return;
//$$         ServerPlayer source = server.getPlayerList().getPlayerByName(current.source());
//$$         if (source == null) { finishFailure("source went offline"); return; }
//$$         ServerPlayer target = server.getPlayerList().getPlayerByName(current.target());
//$$         if (current.mode().endsWith("-box")) {
//$$             checkBoxScenario(server, source, current);
//$$             return;
//$$         }
//$$         if (current.mode().equals("summon") && target instanceof EntityPlayerMPFake
//$$                 && !FakePlayerItemSortConfig.snapshot().summonNotices()
//$$                 && !FakePlayerItemSortManager.shouldSuppressSorterFakeNotice(target)) {
//$$             finishFailure("sorter fake join/leave public notices were not suppressed while disabled");
//$$             return;
//$$         }
//$$         boolean sawOnline = current.sawOnline() || target instanceof EntityPlayerMPFake;
//$$         if (sawOnline != current.sawOnline()) {
//$$             run = current = new Run(current.mode(), current.source(), current.target(), current.item(), current.started(), true);
//$$         }
//$$         int sourceCount = count(source, current.item());
//$$         int onlineCount = target == null ? 0 : count(target, current.item());
//$$         int offlineCount = target == null ? offlineCount(server, current.target(), current.item()) : 0;
//$$         if (current.mode().equals("loose-speed")) {
//$$             long sortedCount = FakePlayerItemSortManager.stockEntries().stream()
//$$                     .filter(entry -> entry.itemId().equals("minecraft:emerald"))
//$$                     .mapToLong(FakePlayerItemSortManager.StockEntry::count).sum();
//$$             long elapsed = server.getTickCount() - current.started();
//$$             long maxAllowed = 4L * (1L + elapsed / 16L);
//$$             if (sortedCount > maxAllowed) {
//$$                 finishFailure("loose items exceeded the configured 16-tick batch rate: target=" + sortedCount
//$$                         + " allowed=" + maxAllowed);
//$$                 return;
//$$             }
//$$             if (sourceCount == 0 && sortedCount == 64) {
//$$                 System.out.println("FGA_SORT_PROBE_PASS: mode=loose-speed items=64 maxBatch=4 speed=16");
//$$                 run = null;
//$$                 return;
//$$             }
//$$             if (elapsed > 1200) finishFailure("loose-speed timeout source=" + sourceCount + " target=" + sortedCount);
//$$             return;
//$$         }
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
//$$     private static void checkBoxScenario(MinecraftServer server, ServerPlayer source, Run current) {
//$$         boolean passed;
//$$         String observed;
//$$         if (current.mode().equals("mixed-box")) {
//$$             int boxes = offlineCount(server, current.target(), Items.SHULKER_BOX);
//$$             int stone = offlineCount(server, current.target(), Items.STONE);
//$$             int dirt = offlineCount(server, current.target(), Items.DIRT);
//$$             passed = count(source, Items.SHULKER_BOX) == 0 && boxes == 1 && stone == 32 && dirt == 32;
//$$             observed = " boxes=" + boxes + " stone=" + stone + " dirt=" + dirt;
//$$         } else if (current.mode().equals("full-box")) {
//$$             int boxes = offlineCount(server, current.target(), Items.SHULKER_BOX);
//$$             int stone = offlineCount(server, current.target(), Items.STONE);
//$$             passed = count(source, Items.SHULKER_BOX) == 0 && boxes == 1 && stone == 1728;
//$$             observed = " boxes=" + boxes + " stone=" + stone;
//$$         } else {
//$$             int boxes = offlineCount(server, "fga_probe_split_shulker_box", Items.SHULKER_BOX);
//$$             int diamonds = offlineCount(server, "fga_probe_split_diamond", Items.DIAMOND);
//$$             int dirt = offlineCount(server, "fga_probe_split_dirt", Items.DIRT);
//$$             passed = count(source, Items.SHULKER_BOX) == 0 && boxes == 1 && diamonds == 32 && dirt == 32;
//$$             observed = " boxes=" + boxes + " diamond=" + diamonds + " dirt=" + dirt;
//$$         }
//$$         if (passed) {
//$$             System.out.println("FGA_SORT_PROBE_PASS: mode=" + current.mode() + observed);
//$$             run = null;
//$$         } else if (server.getTickCount() - current.started() > 1200) {
//$$             finishFailure("timeout mode=" + current.mode() + observed
//$$                     + " sourceBoxes=" + count(source, Items.SHULKER_BOX)
//$$                     + " status=" + FakePlayerItemSortManager.status());
//$$         }
//$$     }
//$$
//$$     private static int count(ServerPlayer player, Item item) {
//$$         int result = 0;
//$$         for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
//$$             ItemStack stack = player.getInventory().getItem(slot);
//$$             result += count(stack, item);
//$$         }
//$$         return result;
//$$     }
//$$
//$$     private static int count(ItemStack stack, Item item) {
//$$         if (stack.isEmpty()) return 0;
//$$         int total = stack.is(item) ? stack.getCount() : 0;
//$$         ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
//$$         if (contents != null) {
//$$             NonNullList<ItemStack> nested = NonNullList.withSize(27, ItemStack.EMPTY);
//$$             contents.copyInto(nested);
//$$             for (ItemStack inner : nested) total += count(inner, item);
//$$         }
//$$         return total;
//$$     }
//$$
//$$     private static int offlineCount(MinecraftServer server, String name, Item item) {
//$$         UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
//$$         Path path = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat");
//$$         if (!Files.isRegularFile(path)) return 0;
//$$         try {
//$$             CompoundTag data = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
//$$             int total = 0;
//$$             for (int index = 0; index < data.getListOrEmpty("Inventory").size(); index++) {
//$$                 ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(
//$$                         server.registryAccess().createSerializationContext(NbtOps.INSTANCE),
//$$                         data.getListOrEmpty("Inventory").getCompoundOrEmpty(index)).result().orElse(ItemStack.EMPTY);
//$$                 total += count(stack, item);
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
//$$
//$$     private record DepotRound(String source, int phase, int started) {
//$$         DepotRound(String source, int started) { this(source, 0, started); }
//$$         DepotRound next(int started) { return new DepotRound(source, phase + 1, started); }
//$$     }
//$$
//$$     private record DepotBoxed(String source, int started) {}
//$$
//$$     private static int beginBlocked(MinecraftServer server, String sourceName, boolean measureOnly) {
//$$         try {
//$$             ServerPlayer source = server.getPlayerList().getPlayerByName(sourceName);
//$$             if (source == null || !source.getInventory().getItem(0).isEmpty()) return fail("blocked fixture source not empty");
//$$             FakePlayerItemSortConfig.setMode("quickopen");
//$$             FakePlayerItemSortConfig.setOption("quickShulker", "true");
//$$             FakePlayerItemSortConfig.setOption("shulkerRestock", "false");
//$$             FakePlayerItemSortConfig.setOption("speed", "40");
//$$             String target = "fga_blocked_quartz";
//$$             Class<?> type = Class.forName("carpet.fga.FakePlayerItemSortManager$OfflineInventory");
//$$             Method open = type.getDeclaredMethod("open", MinecraftServer.class, String.class);
//$$             Method set = type.getDeclaredMethod("setMain", int.class, ItemStack.class);
//$$             Method save = type.getDeclaredMethod("save");
//$$             open.setAccessible(true); set.setAccessible(true); save.setAccessible(true);
//$$             Object inventory = open.invoke(null, server, target);
//$$             for (int slot = 9; slot < 36; slot++) set.invoke(inventory, slot, new ItemStack(Items.QUARTZ, 64));
//$$             if (!(boolean) save.invoke(inventory)) return fail("blocked fixture could not save target");
//$$             Field routes = FakePlayerItemSortManager.class.getDeclaredField("ROUTES");
//$$             Method key = FakePlayerItemSortManager.class.getDeclaredMethod("itemKey", ItemStack.class);
//$$             routes.setAccessible(true); key.setAccessible(true);
//$$             @SuppressWarnings("unchecked") java.util.Map<String,String> map = (java.util.Map<String,String>) routes.get(null);
//$$             map.put((String) key.invoke(null, new ItemStack(Items.QUARTZ)), target);
//$$             source.getInventory().setItem(0, new ItemStack(Items.QUARTZ, 64));
//$$             Class<?> kindType = Class.forName("carpet.fga.FakePlayerItemSortManager$MoveKind");
//$$             Object normal = java.util.Arrays.stream(kindType.getEnumConstants()).filter(v -> v.toString().equals("NORMAL")).findFirst().orElseThrow();
//$$             Method move = FakePlayerItemSortManager.class.getDeclaredMethod("moveQuickopen", ItemStack.class,
//$$                     String.class, String.class, kindType, UUID.class, UUID.class);
//$$             move.setAccessible(true);
//$$             double total = 0, maximum = 0;
//$$             for (int attempt = 0; attempt < 3; attempt++) {
//$$                 long before = System.nanoTime();
//$$                 int moved = (int) move.invoke(null, new ItemStack(Items.QUARTZ, 64), target,
//$$                         key.invoke(null, new ItemStack(Items.QUARTZ)), normal, source.getUUID(), null);
//$$                 double elapsed = (System.nanoTime() - before) / 1_000_000.0;
//$$                 if (moved != 0) return fail("blocked fixture unexpectedly accepted items");
//$$                 total += elapsed; maximum = Math.max(maximum, elapsed);
//$$             }
//$$             System.out.println(String.format(java.util.Locale.ROOT,
//$$                     "FGA_SORT_BLOCKED_COST: calls=3 moved=0 meanMs=%.3f maxMs=%.3f", total/3, maximum));
//$$             if (measureOnly) return 1;
//$$             StringBuilder error = new StringBuilder();
//$$             if (!FakePlayerItemSortManager.start(source, false, null, error)) return fail(error.toString());
//$$             blockedSource = sourceName; blockedPause = 0; blockedAttempts = 0;
//$$             return 1;
//$$         } catch (Exception e) { return fail("blocked fixture setup: " + e); }
//$$     }
//$$
//$$     private static void checkBlocked(MinecraftServer server) {
//$$         try {
//$$             ServerPlayer source = server.getPlayerList().getPlayerByName(blockedSource);
//$$             if (source == null || count(source, Items.QUARTZ) != 64) throw new IllegalStateException("blocked source changed");
//$$             Field jobs = FakePlayerItemSortManager.class.getDeclaredField("JOBS");
//$$             jobs.setAccessible(true);
//$$             Object job = ((java.util.Map<?,?>) jobs.get(null)).get(source.getUUID());
//$$             Field pause = job.getClass().getDeclaredField("pauseUntilTick");
//$$             pause.setAccessible(true);
//$$             long next = pause.getLong(job);
//$$             if (next > blockedPause) {
//$$                 if (blockedPause != 0 && next - blockedPause < 40) throw new IllegalStateException("failed move retried too early");
//$$                 blockedPause = next;
//$$                 if (++blockedAttempts == 3) {
//$$                     if (offlineCount(server, "fga_blocked_quartz", Items.QUARTZ) != 1728)
//$$                         throw new IllegalStateException("blocked target changed");
//$$                     FakePlayerItemSortManager.stop(source);
//$$                     blockedSource = null;
//$$                     System.out.println("FGA_SORT_PROBE_PASS: blocked-retry attempts=3 interval=40 source=64 target=1728");
//$$                 }
//$$             }
//$$         } catch (Exception e) { blockedSource = null; fail("blocked retry: " + e); }
//$$     }
//$$
//$$     /** Eight full-slot mixed boxes; sample before fixture checks, read playerdata only at completion. */
//$$     private static int beginPerf(MinecraftServer server, String mode, String sourceName) {
//$$         if (run != null || perf != null) return fail("previous probe is still active");
//$$         ServerPlayer source = server.getPlayerList().getPlayerByName(sourceName);
//$$         if (!(source instanceof EntityPlayerMPFake)) return fail("benchmark source missing");
//$$         int speed = mode.contains("40") ? 40 : 10;
//$$         String prefix = mode + "_";
//$$         try {
//$$             FakePlayerItemSortConfig.setMode(mode.contains("quick") ? "quickopen" : "summon");
//$$             FakePlayerItemSortConfig.setOption("targetLanguage", "english");
//$$             FakePlayerItemSortConfig.setOption("quickShulker", "true");
//$$             FakePlayerItemSortConfig.setOption("cleanOpenedTarget", "false");
//$$             FakePlayerItemSortConfig.setOption("shulkerRestock", "false");
//$$             FakePlayerItemSortConfig.setOption("dashboard", "false");
//$$             FakePlayerItemSortConfig.setOption("speed", Integer.toString(speed));
//$$             FakePlayerItemSortConfig.setPrefix(prefix);
//$$             // Different item types per case keep the legacy route cache from sharing fixtures.
//$$             Item a = speed == 40 ? Items.GOLD_INGOT : Items.IRON_INGOT;
//$$             Item b = speed == 40 ? Items.COPPER_INGOT : Items.DIAMOND;
//$$             if (mode.contains("quick")) { a = Items.EMERALD; b = Items.LAPIS_LAZULI; }
//$$             // Use cached routes with fixed English names, independent of client/server language.
//$$             Field routes = FakePlayerItemSortManager.class.getDeclaredField("ROUTES");
//$$             routes.setAccessible(true);
//$$             Method key = FakePlayerItemSortManager.class.getDeclaredMethod("itemKey", ItemStack.class);
//$$             key.setAccessible(true);
//$$             @SuppressWarnings("unchecked") java.util.Map<String,String> map = (java.util.Map<String,String>) routes.get(null);
//$$             Item[] items = {a, b, Items.SHULKER_BOX};
//$$             String[] names = {prefix + "a", prefix + "b", prefix + "shulker_box"};
//$$             for (int i = 0; i < items.length; i++) {
//$$                 if (server.getPlayerList().getPlayerByName(names[i]) != null || offlineCount(server, names[i], items[i]) != 0)
//$$                     return fail("benchmark target is not empty");
//$$                 map.put((String) key.invoke(null, new ItemStack(items[i])), names[i]);
//$$                 server.services().nameToIdCache().add(NameAndId.createOffline(names[i]));
//$$             }
//$$             ItemStack emptiedBox = new ItemStack(Items.SHULKER_BOX);
//$$             emptiedBox.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
//$$             map.put((String) key.invoke(null, emptiedBox), names[2]);
//$$             for (int slot = 0; slot < source.getInventory().getContainerSize(); slot++)
//$$                 if (!source.getInventory().getItem(slot).isEmpty()) return fail("benchmark source is not empty");
//$$             int boxes = mode.contains("dense") ? 1 : 8;
//$$             int stackCount = mode.contains("dense") ? 64 : 4;
//$$             for (int boxIndex = 0; boxIndex < boxes; boxIndex++) {
//$$                 NonNullList<ItemStack> contents = NonNullList.withSize(27, ItemStack.EMPTY);
//$$                 for (int slot = 0; slot < 27; slot++) contents.set(slot, new ItemStack(slot % 2 == 0 ? a : b, stackCount));
//$$                 ItemStack box = new ItemStack(Items.SHULKER_BOX);
//$$                 box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
//$$                 source.getInventory().setItem(boxIndex, box);
//$$             }
//$$             FGASettings.fakePlayerItemSort = true;
//$$             FGASettings.fakePlayerNameLength = 64;
//$$             FGASettings.fakePlayerProfilePreload = "always";
//$$             FGASettings.fgaUnicodeArgumentsSupport = true;
//$$             StringBuilder error = new StringBuilder();
//$$             if (!FakePlayerItemSortManager.start(source, false, null, error)) return fail(error.toString());
//$$             perf = new PerfRun(mode, sourceName, items, names, server.getTickCount(), boxes, stackCount);
//$$             System.out.println("FGA_SORT_PERF_BEGIN: mode=" + mode + " boxes=" + boxes + " items=" + (boxes * 27 * stackCount)
//$$                     + " speed=" + speed + " implementation=" + FakePlayerItemSortManager.class.getProtectionDomain().getCodeSource().getLocation());
//$$             return 1;
//$$         } catch (Exception exception) { return fail("benchmark setup: " + exception); }
//$$     }
//$$
//$$     private static void checkPerf(MinecraftServer server) {
//$$         PerfRun p = perf;
//$$         double ms = (System.nanoTime() - tickStarted) / 1_000_000.0;
//$$         p.samples.add(ms);
//$$         ServerPlayer source = server.getPlayerList().getPlayerByName(p.source);
//$$         if (source == null) { perf = null; fail("benchmark source disconnected"); return; }
//$$         int remaining = count(source, p.items[0]) + count(source, p.items[1]);
//$$         if (p.mode.contains("dense") && p.previousRemaining - remaining > 64) {
//$$             perf = null; fail("dense split exceeded 64 items in one tick"); return;
//$$         }
//$$         p.previousRemaining = remaining;
//$$         if (count(source, Items.SHULKER_BOX) != 0) {
//$$             if (server.getTickCount() - p.started > 20000) { perf = null; fail("benchmark timeout"); }
//$$             return;
//$$         }
//$$         int[] expected = {p.boxes * 14 * p.stackCount, p.boxes * 13 * p.stackCount, p.boxes};
//$$         for (int i = 0; i < p.items.length; i++) {
//$$             ServerPlayer target = server.getPlayerList().getPlayerByName(p.names[i]);
//$$             int actual = target == null ? offlineCount(server, p.names[i], p.items[i]) : count(target, p.items[i]);
//$$             if (actual != expected[i]) { perf = null; fail("benchmark conservation " + p.names[i] + " actual=" + actual); return; }
//$$         }
//$$         double[] samples = p.samples.stream().mapToDouble(Double::doubleValue).sorted().toArray();
//$$         System.out.println(String.format(java.util.Locale.ROOT,
//$$                 "FGA_SORT_PERF_PASS: mode=%s boxes=%d items=%d ticks=%d seconds=%.3f meanMs=%.3f p95Ms=%.3f maxMs=%.3f",
//$$                 p.mode, p.boxes, p.boxes * 27 * p.stackCount, samples.length, (System.nanoTime() - p.wallStarted) / 1_000_000_000.0,
//$$                 java.util.Arrays.stream(samples).average().orElse(0), samples[(int)((samples.length-1)*0.95)], samples[samples.length-1]));
//$$         perf = null;
//$$     }
//$$
//$$     private static final class PerfRun {
//$$         final String mode, source;
//$$         final Item[] items;
//$$         final String[] names;
//$$         final int started;
//$$         final int boxes, stackCount;
//$$         int previousRemaining;
//$$         final long wallStarted = System.nanoTime();
//$$         final java.util.List<Double> samples = new java.util.ArrayList<>();
//$$         PerfRun(String mode, String source, Item[] items, String[] names, int started, int boxes, int stackCount) {
//$$             this.mode=mode; this.source=source; this.items=items; this.names=names; this.started=started;
//$$             this.boxes=boxes; this.stackCount=stackCount; this.previousRemaining=boxes*27*stackCount;
//$$         }
//$$     }
//$$ }
//#endif
