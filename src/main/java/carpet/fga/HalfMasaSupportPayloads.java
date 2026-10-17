package carpet.fga;

//#if MC >= 1.21.1
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#if MC >= 1.21.11
//$$ import net.minecraft.resources.Identifier;
//#else
import net.minecraft.resources.ResourceLocation;
//#endif
import net.minecraft.world.item.ItemStack;

public final class HalfMasaSupportPayloads
{
    private HalfMasaSupportPayloads() {}
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path)
    { return new CustomPacketPayload.Type<>(
//#if MC >= 1.21.11
//$$             Identifier.fromNamespaceAndPath("carpet-fga-addition", "halfmasa/" + path)); }
//#else
            ResourceLocation.fromNamespaceAndPath("carpet-fga-addition", "halfmasa/" + path)); }
//#endif
    public record Eat(int version, long requestId, int slot, ItemStack expected, int target, boolean pauseCombat) implements CustomPacketPayload {
        public static final Type<Eat> TYPE = HalfMasaSupportPayloads.type("eat_v1");
        public static final StreamCodec<RegistryFriendlyByteBuf, Eat> CODEC = CustomPacketPayload.codec(Eat::write, Eat::new);
        public Eat(RegistryFriendlyByteBuf b) { this(b.readVarInt(), b.readLong(), b.readVarInt(), ItemStack.STREAM_CODEC.decode(b), b.readVarInt(), b.readBoolean()); }
        private void write(RegistryFriendlyByteBuf b) { b.writeVarInt(version); b.writeLong(requestId); b.writeVarInt(slot); ItemStack.STREAM_CODEC.encode(b, expected); b.writeVarInt(target); b.writeBoolean(pauseCombat); }
        @Override public Type<Eat> type() { return TYPE; }
    }
    public record EatResult(long requestId, String status) implements CustomPacketPayload {
        public static final Type<EatResult> TYPE = HalfMasaSupportPayloads.type("eat_result_v1");
        public static final StreamCodec<FriendlyByteBuf, EatResult> CODEC = CustomPacketPayload.codec(EatResult::write, EatResult::new);
        public EatResult(FriendlyByteBuf b) { this(b.readLong(), b.readUtf(64)); }
        private void write(FriendlyByteBuf b) { b.writeLong(requestId); b.writeUtf(status, 64); }
        @Override public Type<EatResult> type() { return TYPE; }
    }
    public record HealthQuery(UUID bossId) implements CustomPacketPayload {
        public static final Type<HealthQuery> TYPE = HalfMasaSupportPayloads.type("boss_health_query_v1");
        public static final StreamCodec<FriendlyByteBuf, HealthQuery> CODEC = CustomPacketPayload.codec(HealthQuery::write, HealthQuery::new);
        public HealthQuery(FriendlyByteBuf b) { this(b.readUUID()); }
        private void write(FriendlyByteBuf b) { b.writeUUID(bossId); }
        @Override public Type<HealthQuery> type() { return TYPE; }
    }
    public record HealthResult(UUID bossId, float current, float maximum) implements CustomPacketPayload {
        public static final Type<HealthResult> TYPE = HalfMasaSupportPayloads.type("boss_health_result_v1");
        public static final StreamCodec<FriendlyByteBuf, HealthResult> CODEC = CustomPacketPayload.codec(HealthResult::write, HealthResult::new);
        public HealthResult(FriendlyByteBuf b) { this(b.readUUID(), b.readFloat(), b.readFloat()); }
        private void write(FriendlyByteBuf b) { b.writeUUID(bossId); b.writeFloat(current); b.writeFloat(maximum); }
        @Override public Type<HealthResult> type() { return TYPE; }
    }
}
//#endif
