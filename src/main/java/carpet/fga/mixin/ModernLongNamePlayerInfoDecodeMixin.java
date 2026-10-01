//#if MC >= 26.3
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.FakePlayerNameAlias;
//$$ import net.minecraft.network.codec.StreamCodec;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Redirect;
//$$
//$$ @Mixin(targets = "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action")
//$$ public abstract class ModernLongNamePlayerInfoDecodeMixin {
//$$     @Redirect(
//$$             method = "lambda$static$0(Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket$EntryBuilder;Lnet/minecraft/network/RegistryFriendlyByteBuf;)V",
//$$             at = @At(
//$$                     value = "INVOKE",
//$$                     target = "Lnet/minecraft/network/codec/StreamCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;",
//$$                     ordinal = 0
//$$             )
//$$     )
//$$     @SuppressWarnings("rawtypes")
//$$     private static Object fga$decodeLongPlayerName(StreamCodec codec, Object buffer) {
//$$         return FakePlayerNameAlias.withFullNames(() -> codec.decode(buffer));
//$$     }
//$$ }
//#endif
