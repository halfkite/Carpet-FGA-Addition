package fga.handshaketest;

import carpet.fga.FGAPayloads;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * Regression for the reported client handshake encode failure.
 *
 * The first check builds the payload codec the way the reporting client experienced it: the handshake id
 * resolves to DiscardedPayload instead of FGA's payload, which vanilla does whenever the payload list
 * reaches it without our entry. Encoding then threw
 * "ClassCastException: HandshakePayload cannot be cast to DiscardedPayload" and the client was disconnected
 * with "Failed to encode packet 'serverbound/minecraft:custom_payload'". The handshake must be answered by
 * the codec itself, independently of that list and of the fallback provider.
 */
public class HandshakeCompatTest implements ModInitializer {
    private static int checks;

    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                checkSabotageActive();
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

    /**
     * The interference mixin must really have removed the handshake from the vanilla payload list, otherwise
     * the round trip below proves nothing.
     */
    private static void checkSabotageActive() {
        check(HandshakeSabotage.stripped, "handshake was stripped from the vanilla payload list");
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
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
}
