package com.yiyihehe.quickcraft.litematica;

//#if MC >= 1.21 && MC <= 26.2
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#if MC >= 1.21.11
//$$ import net.minecraft.resources.Identifier;
//#else
import net.minecraft.resources.ResourceLocation;
//#endif
import net.minecraft.world.phys.Vec3;

/**
 * QuickCraft entity placement wire types shared with the client mod.
 * The binary name and record layouts must remain identical in both repositories because
 * Fabric uses one payload registry for the client and integrated server in single-player.
 */
public final class QuickLitematicaEntityPlacementPayloads {
    public static final int PROTOCOL_VERSION = 2;
    public static final int CLIENT_FEATURES = 0;
    public static final int MAX_CLIENT_NBT_BYTES = 262_144;

    private QuickLitematicaEntityPlacementPayloads() {
    }

    public record HelloPayload(int version, int features, int maxNbtBytes) implements CustomPacketPayload {
        //#if MC >= 1.21.11
        //$$ public static final Type<HelloPayload> ID = new Type<>(
        //$$         Identifier.fromNamespaceAndPath("quickcraft", "entity_place_hello"));
        //#else
        public static final Type<HelloPayload> ID = new Type<>(
                ResourceLocation.fromNamespaceAndPath("quickcraft", "entity_place_hello"));
        //#endif
        public static final StreamCodec<FriendlyByteBuf, HelloPayload> CODEC =
                CustomPacketPayload.codec(HelloPayload::write, HelloPayload::new);

        public HelloPayload(FriendlyByteBuf buffer) {
            this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(version);
            buffer.writeVarInt(features);
            buffer.writeVarInt(maxNbtBytes);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record CapabilityPayload(
            int version,
            boolean enabled,
            double reach,
            int maxNbtBytes,
            int features,
            String sessionToken
    ) implements CustomPacketPayload {
        //#if MC >= 1.21.11
        //$$ public static final Type<CapabilityPayload> ID = new Type<>(
        //$$         Identifier.fromNamespaceAndPath("quickcraft", "entity_place_capability"));
        //#else
        public static final Type<CapabilityPayload> ID = new Type<>(
                ResourceLocation.fromNamespaceAndPath("quickcraft", "entity_place_capability"));
        //#endif
        public static final StreamCodec<FriendlyByteBuf, CapabilityPayload> CODEC =
                CustomPacketPayload.codec(CapabilityPayload::write, CapabilityPayload::new);

        public CapabilityPayload(FriendlyByteBuf buffer) {
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
            return ID;
        }
    }

    public record RequestPayload(
            String sessionToken,
            long nonce,
            //#if MC >= 1.21.11
            //$$ Identifier dimension,
            //#else
            ResourceLocation dimension,
            //#endif
            Vec3 target,
            //#if MC >= 1.21.11
            //$$ Identifier entityType,
            //#else
            ResourceLocation entityType,
            //#endif
            String region,
            int entityIndex,
            float yaw,
            float pitch,
            Vec3 velocity,
            boolean creativeMaterialBypass,
            CompoundTag entityNbt
    ) implements CustomPacketPayload {
        //#if MC >= 1.21.11
        //$$ public static final Type<RequestPayload> ID = new Type<>(
        //$$         Identifier.fromNamespaceAndPath("quickcraft", "entity_place_request"));
        //#else
        public static final Type<RequestPayload> ID = new Type<>(
                ResourceLocation.fromNamespaceAndPath("quickcraft", "entity_place_request"));
        //#endif
        public static final StreamCodec<FriendlyByteBuf, RequestPayload> CODEC =
                CustomPacketPayload.codec(RequestPayload::write, RequestPayload::new);

        public RequestPayload(FriendlyByteBuf buffer) {
            //#if MC >= 1.21.11
            //$$ this(buffer.readUtf(128), buffer.readLong(), buffer.readIdentifier(), readVec3(buffer),
            //$$         buffer.readIdentifier(), buffer.readUtf(256), buffer.readVarInt(), buffer.readFloat(),
            //$$         buffer.readFloat(), readVec3(buffer), buffer.readBoolean(), buffer.readNbt());
            //#else
            this(buffer.readUtf(128), buffer.readLong(), buffer.readResourceLocation(), readVec3(buffer),
                    buffer.readResourceLocation(), buffer.readUtf(256), buffer.readVarInt(), buffer.readFloat(),
                    buffer.readFloat(), readVec3(buffer), buffer.readBoolean(), buffer.readNbt());
            //#endif
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(sessionToken, 128);
            buffer.writeLong(nonce);
            //#if MC >= 1.21.11
            //$$ buffer.writeIdentifier(dimension);
            //#else
            buffer.writeResourceLocation(dimension);
            //#endif
            writeVec3(buffer, target);
            //#if MC >= 1.21.11
            //$$ buffer.writeIdentifier(entityType);
            //#else
            buffer.writeResourceLocation(entityType);
            //#endif
            buffer.writeUtf(region, 256);
            buffer.writeVarInt(entityIndex);
            buffer.writeFloat(yaw);
            buffer.writeFloat(pitch);
            writeVec3(buffer, velocity);
            buffer.writeBoolean(creativeMaterialBypass);
            buffer.writeNbt(entityNbt);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record ResultPayload(long nonce, String status, String entityUuid, String messageKey)
            implements CustomPacketPayload {
        //#if MC >= 1.21.11
        //$$ public static final Type<ResultPayload> ID = new Type<>(
        //$$         Identifier.fromNamespaceAndPath("quickcraft", "entity_place_result"));
        //#else
        public static final Type<ResultPayload> ID = new Type<>(
                ResourceLocation.fromNamespaceAndPath("quickcraft", "entity_place_result"));
        //#endif
        public static final StreamCodec<FriendlyByteBuf, ResultPayload> CODEC =
                CustomPacketPayload.codec(ResultPayload::write, ResultPayload::new);

        public ResultPayload(FriendlyByteBuf buffer) {
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
            return ID;
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
}
//#endif
