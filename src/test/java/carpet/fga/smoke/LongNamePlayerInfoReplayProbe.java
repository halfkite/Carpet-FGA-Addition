//#if MC == 26.3
//$$ package carpet.fga.smoke;
//$$
//$$ import carpet.fga.FakePlayerNameAlias;
//$$ import com.google.common.collect.ImmutableMultimap;
//$$ import com.mojang.authlib.properties.Property;
//$$ import com.mojang.authlib.properties.PropertyMap;
//$$ import io.netty.buffer.ByteBuf;
//$$ import io.netty.buffer.Unpooled;
//$$ import io.netty.handler.codec.DecoderException;
//$$ import io.netty.handler.codec.EncoderException;
//$$ import net.fabricmc.api.ClientModInitializer;
//$$ import net.fabricmc.loader.api.FabricLoader;
//$$ import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
//$$ import net.minecraft.core.RegistryAccess;
//$$ import net.minecraft.network.RegistryFriendlyByteBuf;
//$$ import net.minecraft.network.Utf8String;
//$$ import net.minecraft.network.codec.ByteBufCodecs;
//$$ import net.minecraft.network.codec.StreamCodec;
//$$ import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
//$$ import net.minecraft.network.protocol.game.GameProtocols;
//$$
//$$ import java.nio.charset.StandardCharsets;
//$$ import java.nio.file.Files;
//$$ import java.nio.file.Path;
//$$ import java.util.Arrays;
//$$ import java.util.EnumSet;
//$$ import java.util.List;
//$$ import java.util.UUID;
//$$ import java.util.concurrent.Executors;
//$$ import java.util.concurrent.TimeUnit;
//$$ import java.util.function.Consumer;
//$$
//$$ /**
//$$  * Actual client codec regression for replay mods: receive a long-name packet,
//$$  * leave the decode scope, then encode that packet on a different thread.
//$$  * This opt-in test mod never belongs in the distributable FGA jar.
//$$  */
//$$ public final class LongNamePlayerInfoReplayProbe implements ClientModInitializer {
//$$     private static final PropertyMap PROPERTIES = new PropertyMap(
//$$             ImmutableMultimap.of("textures", new Property("textures", "fixture-skin", "fixture-signature")));
//$$
//$$     @Override
//$$     public void onInitializeClient() {
//$$         ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
//$$             try {
//$$                 runProbe();
//$$                 System.out.println("FGA_LONG_NAME_REPLAY_PROBE_PASS: decoded packets re-encode across threads, "
//$$                         + "names/UUIDs/skin properties preserved, unrelated limits unchanged");
//$$             } catch (Exception | AssertionError failure) {
//$$                 System.err.println("FGA_LONG_NAME_REPLAY_PROBE_FAIL: " + failure);
//$$                 failure.printStackTrace();
//$$             } finally {
//$$                 client.stop();
//$$             }
//$$         });
//$$     }
//$$
//$$     private static void runProbe() throws Exception {
//$$         try (var worker = Executors.newSingleThreadExecutor(
//$$                 runnable -> new Thread(runnable, "FGA-Replay-Encode-Probe"))) {
//$$             for (String name : List.of("NormalPlayer", "FGA_LongFake_0001",
//$$                     "121321313213213213123213", "中".repeat(32), "N".repeat(128))) {
//$$                 List<String> names = List.of("NormalPlayer", name);
//$$                 byte[] incoming = fixture(names);
//$$                 require(!FakePlayerNameAlias.fullNamesActive(), "scope active before decode");
//$$                 ClientboundPlayerInfoUpdatePacket received = decode(incoming);
//$$                 checkProfiles(received, names);
//$$                 require(!FakePlayerNameAlias.fullNamesActive(), "decode leaked its scope");
//$$                 byte[] replayBytes = worker.submit(() -> {
//$$                     require(!FakePlayerNameAlias.fullNamesActive(), "worker inherited a full-name scope");
//$$                     byte[] encoded = encode(received);
//$$                     require(!FakePlayerNameAlias.fullNamesActive(), "encode leaked its scope");
//$$                     checkProfiles(decode(encoded), names);
//$$                     checkUnrelatedLimit();
//$$                     return encoded;
//$$                 }).get(15, TimeUnit.SECONDS);
//$$                 require(Arrays.equals(incoming, replayBytes), "replay changed the received packet");
//$$                 System.out.println("FGA_LONG_NAME_REPLAY_CASE_PASS: length=" + name.length());
//$$             }
//$$             try {
//$$                 decode(fixture(List.of("N".repeat(129))));
//$$                 throw new AssertionError("packet decode accepted a name longer than 128 characters");
//$$             } catch (DecoderException expected) {
//$$                 require(!FakePlayerNameAlias.fullNamesActive(), "failed decode leaked its scope");
//$$             }
//$$             checkUnrelatedLimit();
//$$         }
//$$         checkFlashbackSaver();
//$$     }
//$$
//$$     private static void checkFlashbackSaver() throws Exception {
//$$         if (!FabricLoader.getInstance().isModLoaded("flashback")) {
//$$             System.out.println("FGA_FLASHBACK_REPLAY_PROBE_SKIP: optional Flashback jar was not supplied");
//$$             return;
//$$         }
//$$         // Exercise the reporter's installed mod through its public API without adding a production dependency.
//$$         Class<?> saverType = Class.forName("com.moulberry.flashback.io.AsyncReplaySaver");
//$$         Object saver = saverType.getConstructor(RegistryAccess.class).newInstance(RegistryAccess.EMPTY);
//$$         Path directory;
//$$         try {
//$$             Class<?> writerType = Class.forName("com.moulberry.flashback.io.ReplayWriter");
//$$             var startSnapshot = writerType.getMethod("startSnapshot");
//$$             var endSnapshot = writerType.getMethod("endSnapshot");
//$$             saverType.getMethod("submit", Consumer.class).invoke(saver, (Consumer<Object>) writer -> {
//$$                 try {
//$$                     startSnapshot.invoke(writer);
//$$                     endSnapshot.invoke(writer);
//$$                 } catch (ReflectiveOperationException failure) {
//$$                     throw new IllegalStateException("Could not initialize the Flashback snapshot", failure);
//$$                 }
//$$             });
//$$             var codec = GameProtocols.CLIENTBOUND_TEMPLATE.bind(
//$$                     RegistryFriendlyByteBuf.decorator(RegistryAccess.EMPTY)).codec();
//$$             var writePackets = saverType.getMethod("writeGamePackets", StreamCodec.class, List.class);
//$$             for (String name : List.of("FGA_LongFake_0001", "121321313213213213123213",
//$$                     "中".repeat(32), "N".repeat(128))) {
//$$                 writePackets.invoke(saver, codec, List.of(decode(fixture(List.of(name)))));
//$$             }
//$$             saverType.getMethod("writeReplayChunk", String.class, String.class)
//$$                     .invoke(saver, "long-name-smoke.fb", "{}");
//$$         } finally {
//$$             directory = (Path) saverType.getMethod("finish").invoke(saver);
//$$         }
//$$         require(directory.toAbsolutePath().normalize().startsWith(
//$$                 FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize()),
//$$                 "Flashback recording was not stored inside the isolated game directory");
//$$         require(Files.size(directory.resolve("long-name-smoke.fb")) > 0, "Flashback did not save its packet chunk");
//$$         System.out.println("FGA_FLASHBACK_REPLAY_PROBE_PASS: AsyncReplaySaver saved long-name packets: " + directory);
//$$     }
//$$
//$$     private static byte[] fixture(List<String> names) {
//$$         ByteBuf bytes = Unpooled.buffer();
//$$         try {
//$$             RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(bytes, RegistryAccess.EMPTY);
//$$             buffer.writeEnumSet(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER),
//$$                     ClientboundPlayerInfoUpdatePacket.Action.class);
//$$             buffer.writeVarInt(names.size());
//$$             for (String name : names) {
//$$                 buffer.writeUUID(profileId(name));
//$$                 // Write wire input independently of the production PLAYER_NAME codec, including the 129-character negative case.
//$$                 Utf8String.write(buffer, name, Math.max(16, name.length()));
//$$                 ByteBufCodecs.GAME_PROFILE_PROPERTIES.encode(buffer, PROPERTIES);
//$$             }
//$$             byte[] result = new byte[bytes.readableBytes()];
//$$             bytes.getBytes(bytes.readerIndex(), result);
//$$             return result;
//$$         } finally {
//$$             bytes.release();
//$$         }
//$$     }
//$$
//$$     private static ClientboundPlayerInfoUpdatePacket decode(byte[] incoming) {
//$$         ByteBuf bytes = Unpooled.wrappedBuffer(incoming);
//$$         try {
//$$             return ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(
//$$                     new RegistryFriendlyByteBuf(bytes, RegistryAccess.EMPTY));
//$$         } finally {
//$$             bytes.release();
//$$         }
//$$     }
//$$
//$$     private static byte[] encode(ClientboundPlayerInfoUpdatePacket packet) {
//$$         ByteBuf bytes = Unpooled.buffer();
//$$         try {
//$$             ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.encode(
//$$                     new RegistryFriendlyByteBuf(bytes, RegistryAccess.EMPTY), packet);
//$$             byte[] result = new byte[bytes.readableBytes()];
//$$             bytes.getBytes(bytes.readerIndex(), result);
//$$             return result;
//$$         } finally {
//$$             bytes.release();
//$$         }
//$$     }
//$$
//$$     private static void checkProfiles(ClientboundPlayerInfoUpdatePacket packet, List<String> names) {
//$$         require(packet.entries().size() == names.size(), "player entry count changed");
//$$         for (int index = 0; index < names.size(); index++) {
//$$             var profile = packet.entries().get(index).profile();
//$$             String name = names.get(index);
//$$             require(profile != null && name.equals(profile.name()), "full player name changed");
//$$             require(profileId(name).equals(profile.id()), "profile UUID changed");
//$$             require(profile.properties().get("textures").containsAll(PROPERTIES.get("textures")),
//$$                     "skin properties changed");
//$$         }
//$$     }
//$$
//$$     private static UUID profileId(String name) {
//$$         return UUID.nameUUIDFromBytes(("FGA-Replay-Probe:" + name).getBytes(StandardCharsets.UTF_8));
//$$     }
//$$
//$$     private static void checkUnrelatedLimit() {
//$$         ByteBuf bytes = Unpooled.buffer();
//$$         try {
//$$             Utf8String.write(bytes, "N".repeat(17), 16);
//$$             throw new AssertionError("unrelated 16-character string limit was widened");
//$$         } catch (EncoderException expected) {
//$$             require(!FakePlayerNameAlias.fullNamesActive(), "unrelated write inherited a scope");
//$$         } finally {
//$$             bytes.release();
//$$         }
//$$     }
//$$
//$$     private static void require(boolean condition, String message) {
//$$         if (!condition) {
//$$             throw new AssertionError(message);
//$$         }
//$$     }
//$$ }
//#endif
