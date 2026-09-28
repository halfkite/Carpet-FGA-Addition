//#if MC == 26.3
//$$ package carpet.fga.smoke;
//$$
//$$ import carpet.fga.FGASettings;
//$$ import com.mojang.brigadier.arguments.StringArgumentType;
//$$ import net.fabricmc.api.DedicatedServerModInitializer;
//$$ import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
//$$ import net.minecraft.commands.Commands;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.world.entity.animal.pig.Pig;
//$$ import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
//$$ import net.minecraft.world.phys.AABB;
//$$ import java.util.UUID;
//$$
//$$ /** Test-only probe: staged by scripts/powershell/fake-player-rejoin-smoke-26.3.ps1. */
//$$ public final class FakePlayerRejoinProbe implements DedicatedServerModInitializer {
//$$     private static UUID crossRejoinBoatId;
//$$
//$$     @Override
//$$     public void onInitializeServer() {
//$$         CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
//$$                 dispatcher.register(Commands.literal("fgaRejoinProbe")
//$$                         .then(Commands.argument("stage", StringArgumentType.word())
//$$                                 .then(Commands.argument("player", StringArgumentType.word())
//$$                                         .executes(context -> probe(
//$$                                                 context.getSource().getServer(),
//$$                                                 context.getSource().getServer().getPlayerList().getPlayerByName(
//$$                                                         StringArgumentType.getString(context, "player")),
//$$                                                 StringArgumentType.getString(context, "stage")))))));
//$$     }
//$$
//$$     private static int probe(MinecraftServer server, ServerPlayer player, String stage) {
//$$         if ("offline".equals(stage)) {
//$$             if (player != null) return fail(stage, "fake player is still online");
//$$             var boats = server.overworld().getEntitiesOfClass(AbstractBoat.class,
//$$                     new AABB(-8, 70, -8, 8, 90, 8));
//$$             if (!boats.isEmpty()) return fail(stage, "boat remained loaded after the last player logged out");
//$$             System.out.println("FGA_REJOIN_PROBE_PASS: offline");
//$$             return 1;
//$$         }
//$$         if ("disabled".equals(stage)) {
//$$             if (FGASettings.enhancedFakePlayerRejoin) return fail(stage, "rule remains enabled");
//$$             if (player == null) return fail(stage, "TIS no-argument rejoin did not spawn the fake player");
//$$             System.out.println("FGA_REJOIN_PROBE_PASS: disabled TIS command still works");
//$$             return 1;
//$$         }
//$$         if (!FGASettings.enhancedFakePlayerRejoin) return fail(stage, "rule not enabled");
//$$         if (player == null) return fail(stage, "fake player is offline");
//$$         if ("setup".equals(stage)) {
//$$             AABB nearby = player.getBoundingBox().inflate(8);
//$$             var boats = player.level().getEntitiesOfClass(AbstractBoat.class, nearby);
//$$             var pigs = player.level().getEntitiesOfClass(Pig.class, nearby);
//$$             if (boats.size() != 1 || pigs.size() != 1) {
//$$                 return fail(stage, "expected one nearby boat and pig; found boats="
//$$                         + boats.size() + " pigs=" + pigs.size() + " at " + player.position());
//$$             }
//$$             boats.get(0).ejectPassengers();
//$$             boolean pigMounted = pigs.get(0).startRiding(boats.get(0), true, false);
//$$             boolean playerMounted = player.startRiding(boats.get(0), true, false);
//$$             if (!pigMounted || !playerMounted) {
//$$                 return fail(stage, "could not mount pig and fake player; pig=" + pigMounted
//$$                         + " player=" + playerMounted + " seats=" + boats.get(0).getPassengers().size()
//$$                         + " vehicle=" + player.getVehicle());
//$$             }
//$$             return checkMounted(player, stage, false);
//$$         }
//$$         return checkMounted(player, stage, "cross".equals(stage) || "repeat".equals(stage));
//$$     }
//$$
//$$     private static int checkMounted(ServerPlayer player, String stage, boolean crossDimension) {
//$$         if (player == null) return fail(stage, "fake player is offline");
//$$         if (crossDimension != player.level().dimension().identifier().toString().equals("minecraft:the_nether")) {
//$$             return fail(stage, "wrong dimension " + player.level().dimension().identifier());
//$$         }
//$$         if (!(player.getVehicle() instanceof AbstractBoat boat)) return fail(stage, "fake player is not riding a boat");
//$$         if (boat.getPassengers().size() != 2 || boat.getPassengers().stream().noneMatch(Pig.class::isInstance)) {
//$$             return fail(stage, "boat is missing its pig passenger or has duplicate passengers");
//$$         }
//$$         if (player.level().getEntitiesOfClass(AbstractBoat.class, boat.getBoundingBox().inflate(16)).size() != 1) {
//$$             return fail(stage, "duplicate boat near rejoined fake player");
//$$         }
//$$         if ("cross".equals(stage) && boat.position().distanceToSqr(40.5, 80, 40.5) > 4) {
//$$             return fail(stage, "boat did not reach the target coordinates: " + boat.position());
//$$         }
//$$         if ("cross".equals(stage)) crossRejoinBoatId = boat.getUUID();
//$$         if ("repeat".equals(stage)) {
//$$             if (boat.position().distanceToSqr(42.5, 80, 42.5) > 4) {
//$$                 return fail(stage, "boat did not reach the second target coordinates: " + boat.position());
//$$             }
//$$             if (!boat.getUUID().equals(crossRejoinBoatId)) {
//$$                 return fail(stage, "repeated coordinate rejoin did not restore the same boat");
//$$             }
//$$         }
//$$         System.out.println("FGA_REJOIN_PROBE_PASS: " + stage + " boat=" + boat.getUUID()
//$$                 + " player=" + player.getUUID());
//$$         return 1;
//$$     }
//$$
//$$     private static int fail(String stage, String reason) {
//$$         System.err.println("FGA_REJOIN_PROBE_FAIL: " + stage + " " + reason);
//$$         return 0;
//$$     }
//$$ }
//#endif
