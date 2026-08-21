package carpet.fga;

//#if MC >= 1.20.2
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#if MC >= 1.20.5
import net.minecraft.network.codec.StreamCodec;
//#endif
//#endif
import net.minecraft.resources.ResourceLocation;
//#if MC >= 1.21 && MC <= 1.21.1
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
//#endif

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

//#if MC >= 1.21 && MC <= 1.21.1
    public static final int ENTITY_PLACE_PROTOCOL_VERSION = 1;
    public static final int ENTITY_PLACE_MAX_NBT_BYTES = 262_144;
    public static final ResourceLocation ENTITY_PLACE_HELLO_CHANNEL =
            ResourceLocation.fromNamespaceAndPath("quickcraft", "entity_place_hello");
    public static final ResourceLocation ENTITY_PLACE_CAPABILITY_CHANNEL =
            ResourceLocation.fromNamespaceAndPath("quickcraft", "entity_place_capability");
    public static final ResourceLocation ENTITY_PLACE_REQUEST_CHANNEL =
            ResourceLocation.fromNamespaceAndPath("quickcraft", "entity_place_request");
    public static final ResourceLocation ENTITY_PLACE_RESULT_CHANNEL =
            ResourceLocation.fromNamespaceAndPath("quickcraft", "entity_place_result");

    public record EntityPlaceHelloPayload(int version, int features, int maxNbtBytes) implements CustomPacketPayload {
        public static final Type<EntityPlaceHelloPayload> TYPE = new Type<>(ENTITY_PLACE_HELLO_CHANNEL);
        public static final StreamCodec<FriendlyByteBuf, EntityPlaceHelloPayload> STREAM_CODEC =
                CustomPacketPayload.codec(EntityPlaceHelloPayload::write, EntityPlaceHelloPayload::new);

        public EntityPlaceHelloPayload(FriendlyByteBuf buffer) {
            this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(version);
            buffer.writeVarInt(features);
            buffer.writeVarInt(maxNbtBytes);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record EntityPlaceCapabilityPayload(
            int version,
            boolean enabled,
            double reach,
            int maxNbtBytes,
            int features,
            String sessionToken
    ) implements CustomPacketPayload {
        public static final Type<EntityPlaceCapabilityPayload> TYPE = new Type<>(ENTITY_PLACE_CAPABILITY_CHANNEL);
        public static final StreamCodec<FriendlyByteBuf, EntityPlaceCapabilityPayload> STREAM_CODEC =
                CustomPacketPayload.codec(EntityPlaceCapabilityPayload::write, EntityPlaceCapabilityPayload::new);

        public EntityPlaceCapabilityPayload(FriendlyByteBuf buffer) {
            this(buffer.readVarInt(), buffer.readBoolean(), buffer.readDouble(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readUtf(128));
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(version);
            buffer.writeBoolean(enabled);
            buffer.writeDouble(reach);
            buffer.writeVarInt(maxNbtBytes);
            buffer.writeVarInt(features);
            buffer.writeUtf(sessionToken, 128);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record EntityPlaceRequestPayload(
            String sessionToken,
            long nonce,
            ResourceLocation dimension,
            Vec3 target,
            ResourceLocation entityType,
            String region,
            int entityIndex,
            float yaw,
            float pitch,
            Vec3 velocity,
            CompoundTag entityNbt
    ) implements CustomPacketPayload {
        public static final Type<EntityPlaceRequestPayload> TYPE = new Type<>(ENTITY_PLACE_REQUEST_CHANNEL);
        public static final StreamCodec<FriendlyByteBuf, EntityPlaceRequestPayload> STREAM_CODEC =
                CustomPacketPayload.codec(EntityPlaceRequestPayload::write, EntityPlaceRequestPayload::new);

        public EntityPlaceRequestPayload(FriendlyByteBuf buffer) {
            this(buffer.readUtf(128), buffer.readLong(), buffer.readResourceLocation(), readVec3(buffer),
                    buffer.readResourceLocation(), buffer.readUtf(256), buffer.readVarInt(), buffer.readFloat(),
                    buffer.readFloat(), readVec3(buffer), buffer.readNbt());
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(sessionToken, 128);
            buffer.writeLong(nonce);
            buffer.writeResourceLocation(dimension);
            writeVec3(buffer, target);
            buffer.writeResourceLocation(entityType);
            buffer.writeUtf(region, 256);
            buffer.writeVarInt(entityIndex);
            buffer.writeFloat(yaw);
            buffer.writeFloat(pitch);
            writeVec3(buffer, velocity);
            buffer.writeNbt(entityNbt);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record EntityPlaceResultPayload(long nonce, String status, String entityUuid, String messageKey)
            implements CustomPacketPayload {
        public static final Type<EntityPlaceResultPayload> TYPE = new Type<>(ENTITY_PLACE_RESULT_CHANNEL);
        public static final StreamCodec<FriendlyByteBuf, EntityPlaceResultPayload> STREAM_CODEC =
                CustomPacketPayload.codec(EntityPlaceResultPayload::write, EntityPlaceResultPayload::new);

        public EntityPlaceResultPayload(FriendlyByteBuf buffer) {
            this(buffer.readLong(), buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(256));
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeLong(nonce);
            buffer.writeUtf(status, 64);
            buffer.writeUtf(entityUuid, 64);
            buffer.writeUtf(messageKey, 256);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeVec3(FriendlyByteBuf buffer, Vec3 value) {
        buffer.writeDouble(value.x);
        buffer.writeDouble(value.y);
        buffer.writeDouble(value.z);
    }

    private static Vec3 readVec3(FriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
//#endif
}
