//#if MC == 26.3
//$$ package carpet.fga.smoke;
//$$
//$$ import carpet.fga.FGASettings;
//$$ import com.mojang.authlib.GameProfile;
//$$ import io.netty.buffer.ByteBuf;
//$$ import io.netty.buffer.Unpooled;
//$$ import net.fabricmc.api.DedicatedServerModInitializer;
//$$ import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
//$$ import net.minecraft.commands.CommandSourceStack;
//$$ import net.minecraft.commands.Commands;
//$$ import net.minecraft.network.RegistryFriendlyByteBuf;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.network.chat.contents.ObjectContents;
//$$ import net.minecraft.network.chat.contents.objects.PlayerSprite;
//$$ import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
//$$ import net.minecraft.world.inventory.MenuType;
//$$ import net.minecraft.world.item.component.ResolvableProfile;
//$$
//$$ import java.util.UUID;
//$$
//$$ /** Isolated-server probe for player profile data embedded in an inventory screen title. */
//$$ public final class UnicodePlayerInventoryScreenProbe implements DedicatedServerModInitializer {
//$$     private static final String CHINESE_NAME = "睡觉假人";
//$$
//$$     @Override
//$$     public void onInitializeServer() {
//$$         CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
//$$                 dispatcher.register(Commands.literal("fgaUnicodeScreenProbe")
//$$                         .executes(context -> probe(context.getSource()))));
//$$     }
//$$
//$$     private static int probe(CommandSourceStack source) {
//$$         if (!FGASettings.fgaUnicodeArgumentsSupport) {
//$$             return fail("fgaUnicodeArgumentsSupport is not enabled");
//$$         }
//$$
//$$         GameProfile profile = new GameProfile(
//$$                 UUID.fromString("a1b2c3d4-e5f6-4711-8223-445566778899"), CHINESE_NAME);
//$$         Component playerSprite = Component.object(
//$$                 new PlayerSprite(ResolvableProfile.createResolved(profile), true),
//$$                 Component.literal(CHINESE_NAME));
//$$         Component title = Component.literal(CHINESE_NAME + " 背包 ").append(playerSprite);
//$$
//$$         ByteBuf byteBuf = Unpooled.buffer();
//$$         try {
//$$             RegistryFriendlyByteBuf packetBuffer = new RegistryFriendlyByteBuf(
//$$                     byteBuf, source.getServer().registryAccess());
//$$             ClientboundOpenScreenPacket packet = new ClientboundOpenScreenPacket(1, MenuType.GENERIC_9x3, title);
//$$             ClientboundOpenScreenPacket.STREAM_CODEC.encode(packetBuffer, packet);
//$$             packetBuffer.readerIndex(0);
//$$             ClientboundOpenScreenPacket decoded = ClientboundOpenScreenPacket.STREAM_CODEC.decode(packetBuffer);
//$$             if (!decoded.getTitle().getString().contains(CHINESE_NAME)) {
//$$                 return fail("the decoded title no longer contains the original Chinese fake-player name");
//$$             }
//$$             if (decoded.getTitle().getSiblings().isEmpty()
//$$                     || !(decoded.getTitle().getSiblings().get(0).getContents() instanceof ObjectContents objectContents)
//$$                     || !(objectContents.contents() instanceof PlayerSprite decodedPlayerSprite)) {
//$$                 return fail("the decoded title lost the embedded player sprite");
//$$             }
//$$             String safeName = decodedPlayerSprite.player().name().orElse("");
//$$             if (!safeName.matches("[A-Za-z0-9_]{1,16}")) {
//$$                 return fail("the player sprite profile still contains an invalid network name: " + safeName);
//$$             }
//$$             if (!profile.id().equals(decodedPlayerSprite.player().partialProfile().id())) {
//$$                 return fail("the sanitized screen profile did not preserve the original UUID");
//$$             }
//$$         } catch (RuntimeException exception) {
//$$             return fail("open-screen packet encode/decode failed: " + exception);
//$$         } finally {
//$$             byteBuf.release();
//$$         }
//$$
//$$         source.sendSuccess(() -> Component.literal(
//$$                 "FGA_UNICODE_OPEN_SCREEN_PROBE_PASS: Chinese title preserved and packet round-tripped"), false);
//$$         System.out.println("FGA_UNICODE_OPEN_SCREEN_PROBE_PASS: Chinese title preserved and packet round-tripped");
//$$         return 1;
//$$     }
//$$
//$$     private static int fail(String reason) {
//$$         System.err.println("FGA_UNICODE_OPEN_SCREEN_PROBE_FAIL: " + reason);
//$$         return 0;
//$$     }
//$$ }
//#endif
