//#if MC == 26.3
//$$ package carpet.fga;
//$$
//$$ import net.minecraft.network.FriendlyByteBuf;
//$$ import net.minecraft.network.codec.StreamCodec;
//$$ import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//$$ import net.minecraft.resources.Identifier;
//$$
//$$ /** Playersort API v1. No server accepts client-supplied stacks or destination identities. */
//$$ public final class PlayerSortInventoryPayloads {
//$$     public static final int VERSION = 1, MAX_JSON_CHARS = 8192;
//$$     private PlayerSortInventoryPayloads() {}
//$$     public interface QueryRequest {
//$$         int version(); long requestId(); int kind(); String target(); int cursor();
//$$         default int amount() { return 0; }
//$$     }
//$$     public interface TakeRequest {
//$$         int version(); long requestId(); String itemId();
//$$         default String token() { return ""; }
//$$         default int amount() { return 0; }
//$$     }
//$$     public record AmountQuery(int version,long requestId,String itemId,int cursor,int amount) implements CustomPacketPayload,QueryRequest {
//$$         public static final Type<AmountQuery> TYPE=new Type<>(Identifier.fromNamespaceAndPath("carpet-fga-addition","playersort/query_amount"));
//$$         public static final StreamCodec<FriendlyByteBuf,AmountQuery> CODEC=CustomPacketPayload.codec(AmountQuery::write,AmountQuery::new);
//$$         public AmountQuery(FriendlyByteBuf b) { this(b.readVarInt(),b.readLong(),b.readUtf(256),b.readVarInt(),b.readVarInt()); }
//$$         private void write(FriendlyByteBuf b) { b.writeVarInt(version); b.writeLong(requestId); b.writeUtf(itemId,256); b.writeVarInt(cursor); b.writeVarInt(amount); }
//$$         public int kind() { return 2; }
//$$         public String target() { return itemId; }
//$$         @Override public Type<AmountQuery> type() { return TYPE; }
//$$     }
//$$     public record AmountTake(int version,long requestId,String token,String itemId,int amount,boolean silent) implements CustomPacketPayload,TakeRequest {
//$$         public static final Type<AmountTake> TYPE=new Type<>(Identifier.fromNamespaceAndPath("carpet-fga-addition","playersort/take_amount"));
//$$         public static final StreamCodec<FriendlyByteBuf,AmountTake> CODEC=CustomPacketPayload.codec(AmountTake::write,AmountTake::new);
//$$         public AmountTake(FriendlyByteBuf b) { this(b.readVarInt(),b.readLong(),b.readUtf(64),b.readUtf(256),b.readVarInt(),b.readBoolean()); }
//$$         private void write(FriendlyByteBuf b) { b.writeVarInt(version); b.writeLong(requestId); b.writeUtf(token,64); b.writeUtf(itemId,256); b.writeVarInt(amount); b.writeBoolean(silent); }
//$$         @Override public Type<AmountTake> type() { return TYPE; }
//$$     }
//$$     public record Query(int version, long requestId, int kind, String target, int cursor) implements CustomPacketPayload,QueryRequest {
//$$         public static final Type<Query> TYPE = new Type<>(Identifier.fromNamespaceAndPath("carpet-fga-addition", "playersort/query"));
//$$         public static final StreamCodec<FriendlyByteBuf, Query> CODEC = CustomPacketPayload.codec(Query::write, Query::new);
//$$         public Query(FriendlyByteBuf b) { this(b.readVarInt(), b.readLong(), b.readVarInt(), b.readUtf(256), b.readVarInt()); }
//$$         private void write(FriendlyByteBuf b) { b.writeVarInt(version); b.writeLong(requestId); b.writeVarInt(kind); b.writeUtf(target,256); b.writeVarInt(cursor); }
//$$         @Override public Type<Query> type() { return TYPE; }
//$$     }
//$$     public record Take(int version, long requestId, String token, String itemId) implements CustomPacketPayload,TakeRequest {
//$$         public static final Type<Take> TYPE = new Type<>(Identifier.fromNamespaceAndPath("carpet-fga-addition", "playersort/take"));
//$$         public static final StreamCodec<FriendlyByteBuf, Take> CODEC = CustomPacketPayload.codec(Take::write, Take::new);
//$$         public Take(FriendlyByteBuf b) { this(b.readVarInt(), b.readLong(), b.readUtf(64), b.readUtf(256)); }
//$$         private void write(FriendlyByteBuf b) { b.writeVarInt(version); b.writeLong(requestId); b.writeUtf(token,64); b.writeUtf(itemId,256); }
//$$         @Override public Type<Take> type() { return TYPE; }
//$$     }
//$$     /** Atomic named withdrawal; the server derives the unprefixed Chinese sorter name. */
//$$     public record DirectTake(int version, long requestId, String itemId) implements CustomPacketPayload,TakeRequest {
//$$         public static final Type<DirectTake> TYPE = new Type<>(Identifier.fromNamespaceAndPath("carpet-fga-addition", "playersort/direct_take"));
//$$         public static final StreamCodec<FriendlyByteBuf, DirectTake> CODEC = CustomPacketPayload.codec(DirectTake::write, DirectTake::new);
//$$         public DirectTake(FriendlyByteBuf b) { this(b.readVarInt(), b.readLong(), b.readUtf(256)); }
//$$         private void write(FriendlyByteBuf b) { b.writeVarInt(version); b.writeLong(requestId); b.writeUtf(itemId,256); }
//$$         @Override public Type<DirectTake> type() { return TYPE; }
//$$     }
//$$     public record SilentTake(int version, long requestId, String token, String itemId) implements CustomPacketPayload,TakeRequest {
//$$         public static final Type<SilentTake> TYPE = new Type<>(Identifier.fromNamespaceAndPath("carpet-fga-addition", "playersort/silent_take"));
//$$         public static final StreamCodec<FriendlyByteBuf, SilentTake> CODEC = CustomPacketPayload.codec(SilentTake::write, SilentTake::new);
//$$         public SilentTake(FriendlyByteBuf b) { this(b.readVarInt(), b.readLong(), b.readUtf(64), b.readUtf(256)); }
//$$         private void write(FriendlyByteBuf b) { b.writeVarInt(version); b.writeLong(requestId); b.writeUtf(token,64); b.writeUtf(itemId,256); }
//$$         @Override public Type<SilentTake> type() { return TYPE; }
//$$     }
//$$     public record Reply(long requestId, String json) implements CustomPacketPayload {
//$$         public static final Type<Reply> TYPE = new Type<>(Identifier.fromNamespaceAndPath("carpet-fga-addition", "playersort/reply"));
//$$         public static final StreamCodec<FriendlyByteBuf, Reply> CODEC = CustomPacketPayload.codec(Reply::write, Reply::new);
//$$         public Reply(FriendlyByteBuf b) { this(b.readLong(), b.readUtf(MAX_JSON_CHARS)); }
//$$         private void write(FriendlyByteBuf b) { b.writeLong(requestId); b.writeUtf(json, MAX_JSON_CHARS); }
//$$         @Override public Type<Reply> type() { return TYPE; }
//$$     }
//$$ }
//$$
//#endif
