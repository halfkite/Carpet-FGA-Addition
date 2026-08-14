package carpet.fga;

//#if MC >= 1.20.2
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#if MC >= 1.20.5
import net.minecraft.network.codec.StreamCodec;
//#endif
//#endif
import net.minecraft.resources.ResourceLocation;

public final class FGAPayloads {
    public static final ResourceLocation HANDSHAKE_CHANNEL =
            //#if MC >= 1.21
            ResourceLocation.fromNamespaceAndPath("carpet-fga-addition", "handshake");
            //#else
            //$$ new ResourceLocation("carpet-fga-addition", "handshake");
            //#endif

    private FGAPayloads() {
    }

//#if MC >= 1.20.5
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
//#else
//#if MC >= 1.20.2
//$$ public record HandshakePayload() implements CustomPacketPayload {
//$$     @Override
//$$     public ResourceLocation id() {
//$$         return HANDSHAKE_CHANNEL;
//$$     }
//$$
//$$     @Override
//$$     public void write(FriendlyByteBuf buffer) {
//$$         buffer.writeVarInt(1);
//$$     }
//$$ }
//#endif
//#endif
}
