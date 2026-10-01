//#if MC >= 26.3
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.FakePlayerNameAlias;
//$$ import io.netty.buffer.ByteBuf;
//$$ import net.minecraft.network.Utf8String;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.ModifyVariable;
//$$
//$$ @Mixin(Utf8String.class)
//$$ public abstract class ModernLongNameUtf8CodecMixin {
//$$     @ModifyVariable(
//$$             method = "read(Lio/netty/buffer/ByteBuf;I)Ljava/lang/String;",
//$$             at = @At("HEAD"),
//$$             argsOnly = true,
//$$             ordinal = 0
//$$     )
//$$     private static int fga$allowScopedPlayerInfoNameDecoding(int maxLength) {
//$$         return maxLength == 16 && FakePlayerNameAlias.fullNamesActive() ? 128 : maxLength;
//$$     }
//$$
//$$     @ModifyVariable(
//$$             method = "write(Lio/netty/buffer/ByteBuf;Ljava/lang/CharSequence;I)V",
//$$             at = @At("HEAD"),
//$$             argsOnly = true,
//$$             ordinal = 0
//$$     )
//$$     private static int fga$allowScopedPlayerInfoNameLength(int maxLength) {
//$$         return maxLength == 16 && FakePlayerNameAlias.fullNamesActive() ? 128 : maxLength;
//$$     }
//$$ }
//#endif
