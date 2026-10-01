//#if MC == 26.3
//$$ package carpet.fga.smoke;
//$$
//$$ import carpet.fga.FGASettings;
//$$ import carpet.patches.EntityPlayerMPFake;
//$$ import com.mojang.brigadier.exceptions.CommandSyntaxException;
//$$ import net.fabricmc.api.DedicatedServerModInitializer;
//$$ import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
//$$ import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//$$ import net.fabricmc.loader.api.FabricLoader;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.server.permissions.PermissionSet;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.players.NameAndId;
//$$ import net.minecraft.world.level.GameType;
//$$ import net.minecraft.world.level.Level;
//$$ import net.minecraft.world.phys.Vec3;
//$$
//$$ /** Runs only from the isolated test-mod JAR created by flat-experience-smoke-26.3.ps1. */
//$$ public final class FlatExperienceProbe implements DedicatedServerModInitializer {
//$$     private int ticks;
//$$     private boolean done;
//$$
//$$     public void onInitializeServer() {
//$$         ServerLifecycleEvents.SERVER_STARTED.register(server -> {
//$$             spawnFake(server, "FgaXpFrom", new Vec3(0, 120, 0));
//$$             spawnFake(server, "FgaXpTo", new Vec3(0, 120, 2));
//$$         });
//$$         ServerTickEvents.END_SERVER_TICK.register(server -> {
//$$             if (done) return;
//$$             var from = server.getPlayerList().getPlayerByName("FgaXpFrom");
//$$             var to = server.getPlayerList().getPlayerByName("FgaXpTo");
//$$             if (from == null || to == null) {
//$$                 if (++ticks < 600) return;
//$$                 done = true;
//$$                 System.err.println("FGA_XP_FAIL: fake players did not spawn");
//$$                 server.halt(false);
//$$                 return;
//$$             }
//$$             done = true;
//$$             long start = System.nanoTime();
//$$             try {
//$$                 run(server, from, to);
//$$                 long millis = (System.nanoTime() - start) / 1_000_000;
//$$                 check(millis < 5000, "suite exceeded 5 seconds: " + millis);
//$$                 System.out.println("FGA_XP_PASS: all assertions, ORG="
//$$                         + FabricLoader.getInstance().isModLoaded("carpet-org-addition") + ", millis=" + millis);
//$$             } catch (Throwable error) {
//$$                 System.err.println("FGA_XP_FAIL: " + error);
//$$                 error.printStackTrace();
//$$             } finally {
//$$                 server.halt(false);
//$$             }
//$$         });
//$$     }
//$$
//$$     private static void run(MinecraftServer server, ServerPlayer from, ServerPlayer to) {
//$$         boolean org = FabricLoader.getInstance().isModLoaded("carpet-org-addition");
//$$         for (String mode : new String[]{"29-30", "0-1"}) {
//$$             command(server, "carpet experienceLevelCost " + mode);
//$$             check(FGASettings.experienceLevelCost.equals(mode), "rule not set");
//$$             for (int level : new int[]{0, 14, 15, 16, 29, 30, 31, 100, 10000000}) {
//$$                 for (int amount : new int[]{1, 107, 108, -1, -107, -108, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
//$$                     set(from, level, 3);
//$$                     long before = total(from, mode);
//$$                     from.giveExperiencePoints(amount);
//$$                     check(total(from, mode) == Math.max(0, before + amount),
//$$                             "award " + mode + " level=" + level + " amount=" + amount);
//$$                     check(from.totalExperience >= 0, "totalExperience overflow");
//$$                 }
//$$             }
//$$             if (!org) continue;
//$$             for (int level : new int[]{10, 29, 30, 31, 100, 1000000000}) {
//$$                 for (String operation : new String[]{"all", "half", "points 17", "level 3", "upgrade 3", "upgradeto 35"}) {
//$$                     set(from, level, 3);
//$$                     set(to, 29, 5);
//$$                     long beforeFrom = total(from, mode);
//$$                     long beforeTo = total(to, mode);
//$$                     long count = switch (operation) {
//$$                         case "all" -> beforeFrom;
//$$                         case "half" -> beforeFrom / 2;
//$$                         case "points 17" -> 17;
//$$                         case "level 3" -> threshold(3, mode);
//$$                         case "upgrade 3" -> threshold(32, mode) - threshold(29, mode);
//$$                         default -> threshold(35, mode) - threshold(29, mode);
//$$                     };
//$$                     if (count > beforeFrom) continue;
//$$                     playerCommand(server, from, "xpTransfer FgaXpFrom FgaXpTo " + operation);
//$$                     check(total(from, mode) == beforeFrom - count, "source " + mode + " " + operation + " level=" + level);
//$$                     check(total(to, mode) == beforeTo + count, "recipient " + mode + " " + operation + " level=" + level);
//$$                 }
//$$             }
//$$             set(from, 100, 3);
//$$             long self = total(from, mode);
//$$             playerCommand(server, from, "xpTransfer FgaXpFrom FgaXpFrom half");
//$$             check(total(from, mode) == self, "self transfer");
//$$             set(from, 0, 3);
//$$             set(to, 31, 5);
//$$             long a = total(from, mode), b = total(to, mode);
//$$             expectFailure(server, from, "xpTransfer FgaXpFrom FgaXpTo points 100");
//$$             check(total(from, mode) == a && total(to, mode) == b, "insufficient changed balances");
//$$             set(from, 100, 3);
//$$             set(to, Integer.MAX_VALUE, 5);
//$$             a = total(from, mode);
//$$             b = total(to, mode);
//$$             expectFailure(server, from, "xpTransfer FgaXpFrom FgaXpTo all");
//$$             check(total(from, mode) == a && total(to, mode) == b, "overflow changed balances");
//$$         }
//$$         command(server, "carpet experienceLevelCost false");
//$$         set(from, 35, 0);
//$$         long vanilla = threshold(35, "false");
//$$         from.giveExperiencePoints(17);
//$$         check(total(from, "false") == vanilla + 17, "disabled vanilla award");
//$$         if (org) {
//$$             set(from, 35, 0);
//$$             set(to, 0, 0);
//$$             playerCommand(server, from, "xpTransfer FgaXpFrom FgaXpTo all");
//$$             check(total(from, "false") == 0 && total(to, "false") == vanilla, "disabled ORG");
//$$         }
//$$     }
//$$
//$$     private static int cost(int level, String mode) {
//$$         if ("0-1".equals(mode)) return 7;
//$$         if ("29-30".equals(mode) && level >= 30) return 107;
//$$         return level >= 30 ? 112 + (level - 30) * 9
//$$                 : level >= 15 ? 37 + (level - 15) * 5 : 7 + 2 * level;
//$$     }
//$$     private static long threshold(int level, String mode) {
//$$         if ("0-1".equals(mode)) return 7L * level;
//$$         if ("29-30".equals(mode) && level >= 30) return 1395L + (level - 30L) * 107;
//$$         long points = 0;
//$$         for (int i = 0; i < level; i++) points += cost(i, mode);
//$$         return points;
//$$     }
//$$     private static long total(ServerPlayer player, String mode) {
//$$         return threshold(player.experienceLevel, mode)
//$$                 + Math.round((double) player.experienceProgress * cost(player.experienceLevel, mode));
//$$     }
//$$     private static void set(ServerPlayer player, int level, int points) {
//$$         player.setExperienceLevels(level);
//$$         player.setExperiencePoints(points);
//$$         player.totalExperience = (int) Math.min(Integer.MAX_VALUE, total(player, FGASettings.experienceLevelCost));
//$$     }
//$$     private static void command(MinecraftServer server, String command) {
//$$         try { server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack()); }
//$$         catch (CommandSyntaxException e) { throw new AssertionError(command, e); }
//$$     }
//$$     private static void spawnFake(MinecraftServer server, String name, Vec3 position) {
//$$         server.services().nameToIdCache().add(NameAndId.createOffline(name));
//$$         boolean created = EntityPlayerMPFake.createFake(name, server, position, 0, 0,
//$$                 Level.OVERWORLD, GameType.SURVIVAL, false);
//$$         check(created, "could not spawn fake player " + name);
//$$     }
//$$     private static void playerCommand(MinecraftServer server, ServerPlayer player, String command) {
//$$         try {
//$$             var source = player.createCommandSourceStack().withMaximumPermission(PermissionSet.ALL_PERMISSIONS);
//$$             server.getCommands().getDispatcher().execute(command, source);
//$$         } catch (CommandSyntaxException e) { throw new AssertionError(command, e); }
//$$     }
//$$     private static void expectFailure(MinecraftServer server, ServerPlayer player, String command) {
//$$         try {
//$$             var source = player.createCommandSourceStack().withMaximumPermission(PermissionSet.ALL_PERMISSIONS);
//$$             server.getCommands().getDispatcher().execute(command, source);
//$$         } catch (RuntimeException | CommandSyntaxException expected) { return; }
//$$         throw new AssertionError("Expected command failure: " + command);
//$$     }
//$$     private static void check(boolean value, String message) {
//$$         if (!value) throw new AssertionError(message);
//$$     }
//$$ }
//#endif
