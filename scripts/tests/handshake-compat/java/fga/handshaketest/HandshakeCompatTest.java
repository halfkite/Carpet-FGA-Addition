package fga.handshaketest;

import carpet.fga.FGAPayloads;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Regression for the reported client handshake failure (issue #24).
 *
 * The first check is the discriminating one: the handshake must be registered with Fabric's payload
 * registry. FGA used to rely only on the payload list of CustomPacketPayload.codec; when another mod
 * rebuilds that list from a copy (Carpet does, to add its own payload) the handshake id is missing,
 * vanilla answers with DiscardedPayload and the client disconnects with
 * "Failed to encode packet 'serverbound/minecraft:custom_payload' (carpet-fga-addition:handshake)".
 * Fabric's codec asks its registry before vanilla's fallback provider, so a registered channel survives
 * the list being rebuilt.
 *
 * Fabric's registry is queried through its implementation class because the public API only writes.
 */
public class HandshakeCompatTest implements ModInitializer {
    private static int checks;

    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                checkFabricRegistration();
                checkRealPacketRoundTrip(server);
                System.out.println("FGA_HANDSHAKE_COMPAT_PASS checks=" + checks);
            } catch (Throwable failure) {
                System.out.println("FGA_HANDSHAKE_COMPAT_FAIL " + failure);
                failure.printStackTrace();
            } finally {
                server.halt(false);
            }
        });
    }

    private static void checkFabricRegistration() {
        Object registered = PayloadTypeRegistryImpl.PLAY_C2S.get(FGAPayloads.HANDSHAKE_CHANNEL);
        check(registered != null, "handshake registered in Fabric play C2S registry");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void checkRealPacketRoundTrip(net.minecraft.server.MinecraftServer server) {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        ServerboundCustomPayloadPacket packet =
                new ServerboundCustomPayloadPacket(new FGAPayloads.HandshakePayload(1));
        StreamCodec<FriendlyByteBuf, ServerboundCustomPayloadPacket> codec =
                ServerboundCustomPayloadPacket.STREAM_CODEC;
        codec.encode(buffer, packet);
        check(buffer.readableBytes() > 0, "handshake packet encoded");
        buffer.readerIndex(0);
        ServerboundCustomPayloadPacket decoded = codec.decode(buffer);
        check(decoded.payload() instanceof FGAPayloads.HandshakePayload, "handshake packet decoded as payload");
        check(((FGAPayloads.HandshakePayload) decoded.payload()).version() == 1, "handshake version kept");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
}
