package carpet.fga;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class FGAPayloads {
    public static final ResourceLocation HANDSHAKE_CHANNEL =
            ResourceLocation.fromNamespaceAndPath("carpet-fga-addition", "handshake");

    private FGAPayloads() {
    }

    public record HandshakePayload(int version) implements CustomPacketPayload {
        public static final Type<HandshakePayload> TYPE = new Type<>(HANDSHAKE_CHANNEL);
        public static final StreamCodec<FriendlyByteBuf, HandshakePayload> STREAM_CODEC =
                CustomPacketPayload.codec(HandshakePayload::write, HandshakePayload::new);

        public HandshakePayload(FriendlyByteBuf buffer) {
            this(buffer.readVarInt());
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(version);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
