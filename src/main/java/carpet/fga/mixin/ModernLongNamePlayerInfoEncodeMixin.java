//#if MC >= 26.3
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.FakePlayerNameAlias;
//$$ import net.minecraft.network.codec.StreamCodec;
//$$ import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
//$$ import org.spongepowered.asm.mixin.Final;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.Unique;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.Redirect;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ import java.util.List;
//$$
//$$ /** 26.3+ direct player-name codec path; never registered with the pre-26.3 buffer path. */
//$$ @Mixin(ClientboundPlayerInfoUpdatePacket.class)
//$$ public abstract class ModernLongNamePlayerInfoEncodeMixin {
//$$     @Shadow @Final
//$$     private List<ClientboundPlayerInfoUpdatePacket.Entry> entries;
//$$
//$$     @Unique
//$$     private boolean fga$allowLongNameEncoding;
//$$
//$$     @Inject(method = "<init>", at = @At("RETURN"))
//$$     private void fga$captureLongNameScope(CallbackInfo ci) {
//$$         fga$allowLongNameEncoding = FakePlayerNameAlias.fullNamesActive();
//$$     }
//$$
//$$     @Redirect(
//$$             method = "write(Lnet/minecraft/network/RegistryFriendlyByteBuf;)V",
//$$             at = @At(value = "INVOKE",
//$$                     target = "Lnet/minecraft/network/codec/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V")
//$$     )
//$$     @SuppressWarnings("rawtypes")
//$$     private void fga$encodeLongNames(StreamCodec codec, Object buffer, Object packetEntries) {
//$$         boolean allowLongNames = fga$allowLongNameEncoding;
//$$         if (!allowLongNames) {
//$$             // Replay mods re-encode received packets on a worker without the server constructor's scope.
//$$             for (ClientboundPlayerInfoUpdatePacket.Entry entry : entries) {
//$$                 if (entry.profile() != null && entry.profile().name().length() > 16) {
//$$                     allowLongNames = true;
//$$                     break;
//$$                 }
//$$             }
//$$         }
//$$         if (!allowLongNames) {
//$$             codec.encode(buffer, packetEntries);
//$$             return;
//$$         }
//$$         FakePlayerNameAlias.withFullNames(() -> {
//$$             codec.encode(buffer, packetEntries);
//$$             return null;
//$$         });
//$$     }
//$$ }
//#endif
