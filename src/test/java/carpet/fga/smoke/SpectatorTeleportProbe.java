//#if MC == 26.3
//$$ package carpet.fga.smoke;
//$$
//$$ import carpet.fga.FGASettings;
//$$ import carpet.fga.PlayerPossessionManager;
//$$ import carpet.fga.SpectatorFreeTeleport;
//$$ import com.mojang.brigadier.CommandDispatcher;
//$$ import com.mojang.brigadier.exceptions.CommandSyntaxException;
//$$ import net.fabricmc.api.DedicatedServerModInitializer;
//$$ import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
//$$ import net.minecraft.commands.CommandSourceStack;
//$$ import net.minecraft.commands.Commands;
//$$ import net.minecraft.commands.arguments.EntityArgument;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.server.level.ServerLevel;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.world.level.Level;
//$$ import net.minecraft.world.level.portal.TeleportTransition;
//$$ import net.minecraft.world.level.GameType;
//$$ import net.minecraft.world.phys.Vec3;
//$$
//$$ /** Dedicated-server probe, packaged only by the 26.3 smoke script. */
//$$ public final class SpectatorTeleportProbe implements DedicatedServerModInitializer {
//$$     private static int successfulProbes;
//$$     private static int falseProbes;
//$$
//$$     @Override
//$$     public void onInitializeServer() {
//$$         CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
//$$                 dispatcher.register(Commands.literal("fgaTeleportFalseProbe")
//$$                         .then(Commands.argument("player", EntityArgument.player())
//$$                                 .executes(context -> falseProbe(
//$$                                         context.getSource(), dispatcher,
//$$                                         EntityArgument.getPlayer(context, "player")
//$$                                 )))));
//$$         CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
//$$                 dispatcher.register(Commands.literal("fgaSpectatorProbe")
//$$                         .then(Commands.argument("spectator", EntityArgument.player())
//$$                                 .then(Commands.argument("other", EntityArgument.player())
//$$                                         .executes(context -> probe(
//$$                                                 context.getSource(),
//$$                                                 dispatcher,
//$$                                                 EntityArgument.getPlayer(context, "spectator"),
//$$                                                 EntityArgument.getPlayer(context, "other")
//$$                                         ))))));
//$$         CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
//$$                 dispatcher.register(Commands.literal("fgaSpectatorFullProbe")
//$$                         .then(Commands.argument("player", EntityArgument.player())
//$$                                 .then(Commands.argument("other", EntityArgument.player())
//$$                                         .executes(context -> fullProbe(
//$$                                                 context.getSource(), dispatcher,
//$$                                                 EntityArgument.getPlayer(context, "player"),
//$$                                                 EntityArgument.getPlayer(context, "other")
//$$                                         ))))));
//$$     }
//$$
//$$     private static int falseProbe(
//$$             CommandSourceStack console,
//$$             CommandDispatcher<CommandSourceStack> dispatcher,
//$$             ServerPlayer player) {
//$$         if (!"false".equals(FGASettings.spectatorFreeTeleport)) {
//$$             return fail("spectatorFreeTeleport is not false for the vanilla-permission probe");
//$$         }
//$$         CommandSourceStack playerSource = player.createCommandSourceStack();
//$$         GameType originalMode = player.gameMode.getGameModeForPlayer();
//$$         GameType[] modes = {GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE, GameType.SPECTATOR};
//$$         try {
//$$             for (GameType mode : modes) {
//$$                 player.setGameMode(mode);
//$$                 if (dispatcher.getRoot().getChild("tp").canUse(playerSource)
//$$                         || dispatcher.getRoot().getChild("teleport").canUse(playerSource)) {
//$$                     return fail("non-OP player gained vanilla teleport permission in " + mode + " while rule=false");
//$$                 }
//$$             }
//$$         } finally {
//$$             player.setGameMode(originalMode);
//$$         }
//$$         int sequence = ++falseProbes;
//$$         console.sendSuccess(() -> Component.literal("FGA_TELEPORT_FALSE_PROBE_PASS: non-OP #" + sequence
//$$                 + " denied in all game modes"), false);
//$$         System.out.println("FGA_TELEPORT_FALSE_PROBE_PASS: non-OP #" + sequence);
//$$         return 1;
//$$     }
//$$
//$$     private static int probe(
//$$             CommandSourceStack console,
//$$             CommandDispatcher<CommandSourceStack> dispatcher,
//$$             ServerPlayer spectator,
//$$             ServerPlayer other) {
//$$         if ("false".equals(FGASettings.spectatorFreeTeleport)) {
//$$             return fail("spectatorFreeTeleport is not enabled");
//$$         }
//$$         if (!spectator.isSpectator()) {
//$$             return fail("the probe player is not in spectator mode");
//$$         }
//$$         if (!SpectatorFreeTeleport.isOperatorCheatPreventionEnabled()) {
//$$             return fail("the TIS/AMS anti-cheat rules were not enabled for this probe");
//$$         }
//$$
//$$         CommandSourceStack playerSource = spectator.createCommandSourceStack();
//$$         boolean operator = PlayerPossessionManager.isOp(
//$$                 spectator.level().getServer(), spectator.getGameProfile());
//$$         if (!operator && SpectatorFreeTeleport.isRealOperator(playerSource)) {
//$$             return fail("the non-OP probe player unexpectedly has gamemaster permission");
//$$         }
//$$         if (!dispatcher.getRoot().getChild("tp").canUse(playerSource)
//$$                 || !dispatcher.getRoot().getChild("teleport").canUse(playerSource)) {
//$$             return fail("the spectator cannot see /tp or /teleport in the command tree");
//$$         }
//$$         if (!dispatcher.getRoot().getChild("teleport").getChild("in").canUse(playerSource)) {
//$$             return fail("the spectator cannot see the /tp in <dimension> <position> command branch");
//$$         }
//$$         if (dispatcher.getRoot().getChild("teleport").getChild("location").getChild("dimension") == null) {
//$$             return fail("the /tp <position> <dimension> command branch is missing");
//$$         }
//$$
//$$         try {
//$$             dispatcher.execute("tp @s 100 80 100", playerSource);
//$$             if (spectator.position().distanceToSqr(new Vec3(100.5, 80, 100.5)) > 1.0E-6) {
//$$                 return fail("/tp @s was accepted but did not move the spectator to its destination");
//$$             }
//$$
//$$             dispatcher.execute("teleport @s 120 80 120", playerSource);
//$$             if (spectator.position().distanceToSqr(new Vec3(120.5, 80, 120.5)) > 1.0E-6) {
//$$                 return fail("/teleport @s was accepted but did not move the spectator to its destination");
//$$             }
//$$
//$$             ServerLevel nether = spectator.level().getServer().getLevel(Level.NETHER);
//$$             if (nether == null) {
//$$                 return fail("the isolated server has no Nether dimension");
//$$             }
//$$             other.teleport(new TeleportTransition(nether, new Vec3(25.5, 80, 25.5), other.getDeltaMovement(),
//$$                     other.getYRot(), other.getXRot(), TeleportTransition.DO_NOTHING));
//$$             if (other.level() != nether) {
//$$                 return fail("could not place the target player in the Nether for cross-dimension testing");
//$$             }
//$$             dispatcher.execute("tp " + other.getScoreboardName(), playerSource);
//$$             if (spectator.level() != nether || spectator.position().distanceToSqr(other.position()) > 1.0E-6) {
//$$                 return fail("/tp <player> did not move the spectator to the online player in another dimension");
//$$             }
//$$
//$$             dispatcher.execute("tp in minecraft:overworld 100 80 100", playerSource);
//$$             ServerLevel overworld = spectator.level().getServer().getLevel(Level.OVERWORLD);
//$$             if (spectator.level() != overworld
//$$                     || spectator.position().distanceToSqr(new Vec3(100.5, 80, 100.5)) > 1.0E-6) {
//$$                 return fail("/tp in <dimension> <position> did not move the spectator to the Overworld coordinates");
//$$             }
//$$
//$$             dispatcher.execute("teleport in minecraft:the_nether 120 80 120", playerSource);
//$$             if (spectator.level() != nether
//$$                     || spectator.position().distanceToSqr(new Vec3(120.5, 80, 120.5)) > 1.0E-6) {
//$$                 return fail("/teleport in <dimension> <position> did not move the spectator to Nether coordinates");
//$$             }
//$$
//$$             dispatcher.execute("tp 140 80 140 minecraft:overworld", playerSource);
//$$             if (spectator.level() != overworld
//$$                     || spectator.position().distanceToSqr(new Vec3(140.5, 80, 140.5)) > 1.0E-6) {
//$$                 return fail("/tp <position> <dimension> did not move the spectator to Overworld coordinates");
//$$             }
//$$             dispatcher.execute("teleport ~1 ~ ~ minecraft:the_nether",
//$$                     spectator.createCommandSourceStack());
//$$             if (spectator.level() != nether
//$$                     || spectator.position().distanceToSqr(new Vec3(141.5, 80, 140.5)) > 1.0E-6) {
//$$                 return fail("relative /teleport <position> <dimension> changed the coordinate origin");
//$$             }
//$$         } catch (CommandSyntaxException exception) {
//$$             return fail("cross-dimension self teleport failed: " + exception.getMessage());
//$$         }
//$$
//$$         Vec3 otherPosition = other.position();
//$$         ServerLevel otherLevel = (ServerLevel) other.level();
//$$         try {
//$$             dispatcher.execute("tp " + other.getScoreboardName() + " 200 80 200", playerSource);
//$$             return fail("the spectator was able to teleport another player");
//$$         } catch (CommandSyntaxException expected) {
//$$             if (other.level() != otherLevel || other.position().distanceToSqr(otherPosition) > 1.0E-6) {
//$$                 return fail("a rejected teleport still moved the other player");
//$$             }
//$$         }
//$$
//$$         int sequence = ++successfulProbes;
//$$         console.sendSuccess(() -> Component.literal(
//$$                 "FGA_SPECTATOR_TELEPORT_PROBE_PASS: " + (operator ? "OP" : "non-OP")
//$$                         + " #" + sequence + " spectator can teleport self across dimensions and cannot move others"), false);
//$$         System.out.println("FGA_SPECTATOR_TELEPORT_PROBE_PASS: "
//$$                 + (operator ? "OP" : "non-OP") + " #" + sequence);
//$$         return 1;
//$$     }
//$$
//$$     private static int fullProbe(
//$$             CommandSourceStack console,
//$$             CommandDispatcher<CommandSourceStack> dispatcher,
//$$             ServerPlayer player,
//$$             ServerPlayer other) {
//$$         if (!"full".equals(FGASettings.spectatorFreeTeleport)) {
//$$             return fail("spectatorFreeTeleport is not set to full");
//$$         }
//$$         if (!SpectatorFreeTeleport.isOperatorCheatPreventionEnabled()) {
//$$             return fail("the TIS/AMS anti-cheat rules were not enabled for the full-mode probe");
//$$         }
//$$         boolean operator = PlayerPossessionManager.isOp(player.level().getServer(), player.getGameProfile());
//$$
//$$         GameType originalMode = player.gameMode.getGameModeForPlayer();
//$$         ServerLevel overworld = player.level().getServer().getLevel(Level.OVERWORLD);
//$$         ServerLevel nether = player.level().getServer().getLevel(Level.NETHER);
//$$         if (overworld == null || nether == null) return fail("test dimensions are unavailable");
//$$         player.teleport(new TeleportTransition(overworld, new Vec3(0.5, 80, 0.5), player.getDeltaMovement(),
//$$                 player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
//$$         other.teleport(new TeleportTransition(overworld, new Vec3(10.5, 80, 0.5), other.getDeltaMovement(),
//$$                 other.getYRot(), other.getXRot(), TeleportTransition.DO_NOTHING));
//$$         CommandSourceStack playerSource = player.createCommandSourceStack();
//$$
//$$         try {
//$$             GameType[] modes = {GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE, GameType.SPECTATOR};
//$$             for (int i = 0; i < modes.length; i++) {
//$$                 player.setGameMode(modes[i]);
//$$                 if (!dispatcher.getRoot().getChild("tp").canUse(playerSource)
//$$                         || !dispatcher.getRoot().getChild("teleport").canUse(playerSource)
//$$                         || !dispatcher.getRoot().getChild("teleport").getChild("in").canUse(playerSource)) {
//$$                     return fail("full mode command tree unavailable in " + modes[i]);
//$$                 }
//$$                 int coordinate = 100 + i * 10;
//$$                 dispatcher.execute("tp @s " + coordinate + " 80 " + coordinate, playerSource);
//$$                 if (player.level() != overworld
//$$                         || player.position().distanceToSqr(new Vec3(coordinate + 0.5, 80, coordinate + 0.5)) > 1.0E-6) {
//$$                     return fail("full mode self teleport failed in " + modes[i]);
//$$                 }
//$$
//$$                 dispatcher.execute("teleport " + other.getScoreboardName() + " 200 80 200", playerSource);
//$$                 if (other.level() != overworld
//$$                         || other.position().distanceToSqr(new Vec3(200.5, 80, 200.5)) > 1.0E-6) {
//$$                     return fail("full mode could not teleport another player in " + modes[i]);
//$$                 }
//$$
//$$                 dispatcher.execute("tp @a 210 80 210", playerSource);
//$$                 Vec3 selectorDestination = new Vec3(210.5, 80, 210.5);
//$$                 if (player.position().distanceToSqr(selectorDestination) > 1.0E-6
//$$                         || other.position().distanceToSqr(selectorDestination) > 1.0E-6) {
//$$                     return fail("full mode multi-player selector teleport failed in " + modes[i]);
//$$                 }
//$$
//$$                 dispatcher.execute("tp in minecraft:the_nether 120 80 120", playerSource);
//$$                 if (player.level() != nether
//$$                         || player.position().distanceToSqr(new Vec3(120.5, 80, 120.5)) > 1.0E-6) {
//$$                     return fail("full mode explicit dimension teleport failed in " + modes[i]);
//$$                 }
//$$                 dispatcher.execute("teleport in minecraft:overworld 130 80 130", playerSource);
//$$                 if (player.level() != overworld
//$$                         || player.position().distanceToSqr(new Vec3(130.5, 80, 130.5)) > 1.0E-6) {
//$$                     return fail("full mode explicit dimension return failed in " + modes[i]);
//$$                 }
//$$                 dispatcher.execute("tp 140 80 140 minecraft:the_nether", playerSource);
//$$                 if (player.level() != nether
//$$                         || player.position().distanceToSqr(new Vec3(140.5, 80, 140.5)) > 1.0E-6) {
//$$                     return fail("full mode trailing dimension teleport failed in " + modes[i]);
//$$                 }
//$$                 dispatcher.execute("teleport 150 80 150 minecraft:overworld", playerSource);
//$$                 if (player.level() != overworld
//$$                         || player.position().distanceToSqr(new Vec3(150.5, 80, 150.5)) > 1.0E-6) {
//$$                     return fail("full mode trailing dimension return failed in " + modes[i]);
//$$                 }
//$$             }
//$$
//$$             other.teleport(new TeleportTransition(nether, new Vec3(25.5, 80, 25.5), other.getDeltaMovement(),
//$$                     other.getYRot(), other.getXRot(), TeleportTransition.DO_NOTHING));
//$$             dispatcher.execute("tp @s " + other.getScoreboardName(), playerSource);
//$$             if (player.level() != nether || player.position().distanceToSqr(other.position()) > 1.0E-6) {
//$$                 return fail("full mode could not follow an online player across dimensions");
//$$             }
//$$
//$$             other.teleport(new TeleportTransition(overworld, new Vec3(30.5, 80, 30.5), other.getDeltaMovement(),
//$$                     other.getYRot(), other.getXRot(), TeleportTransition.DO_NOTHING));
//$$             dispatcher.execute("tp " + other.getScoreboardName() + " @s", playerSource);
//$$             if (other.level() != nether || other.position().distanceToSqr(player.position()) > 1.0E-6) {
//$$                 return fail("full mode could not teleport another player across dimensions");
//$$             }
//$$         } catch (CommandSyntaxException exception) {
//$$             return fail("full mode command failed: " + exception.getMessage());
//$$         } finally {
//$$             player.setGameMode(originalMode);
//$$         }
//$$         player.teleport(new TeleportTransition(overworld, new Vec3(0.5, 80, 0.5), player.getDeltaMovement(),
//$$                 player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
//$$
//$$         int sequence = ++successfulProbes;
//$$         console.sendSuccess(() -> Component.literal("FGA_FULL_TELEPORT_PROBE_PASS: "
//$$                 + (operator ? "OP" : "non-OP") + " #" + sequence
//$$                 + " all modes, multi-target and cross-dimension teleport passed"), false);
//$$         System.out.println("FGA_FULL_TELEPORT_PROBE_PASS: " + (operator ? "OP" : "non-OP") + " #" + sequence);
//$$         return 1;
//$$     }
//$$
//$$     private static int fail(String reason) {
//$$         System.err.println("FGA_SPECTATOR_TELEPORT_PROBE_FAIL: " + reason);
//$$         return 0;
//$$     }
//$$ }
//#endif
