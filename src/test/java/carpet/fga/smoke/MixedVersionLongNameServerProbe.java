//#if MC == 26.3
//$$ package carpet.fga.smoke;
//$$
//$$ import carpet.fga.FGAModDetector;
//$$ import net.fabricmc.api.DedicatedServerModInitializer;
//$$ import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
//$$ import net.minecraft.commands.Commands;
//$$
//$$ /** Test-only console query for the presence of a client handshake; does not modify capabilities. */
//$$ public final class MixedVersionLongNameServerProbe implements DedicatedServerModInitializer {
//$$     @Override
//$$     public void onInitializeServer() {
//$$         CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
//$$             dispatcher.register(Commands.literal("fgaMixedVersionClientState")
//$$                     .requires(source -> source.getEntity() == null)
//$$                     .executes(context -> {
//$$                         var player = context.getSource().getServer().getPlayerList().getPlayerByName("FGAMixedSmoke");
//$$                         if (player == null) {
//$$                             throw new IllegalStateException("Test client is not online");
//$$                         }
//$$                         System.out.println("FGA_MIXED_VERSION_HANDSHAKE: modded=" + FGAModDetector.hasMod(player));
//$$                         return 1;
//$$                     }));
//$$         });
//$$     }
//$$ }
//#endif
