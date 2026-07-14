package carpet.fga.mixin;

import carpet.fga.FGAPayloads;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(CustomPacketPayload.class)
public interface CustomPacketPayloadMixin {
    @Inject(
            method = "codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;",
            at = @At("HEAD")
    )
    private static <B extends FriendlyByteBuf> void registerHandshake(
            CustomPacketPayload.FallbackProvider<B> fallbackProvider,
            List<CustomPacketPayload.TypeAndCodec<? super B, ?>> codecs,
            CallbackInfoReturnable<StreamCodec<B, CustomPacketPayload>> cir) {
        for (CustomPacketPayload.TypeAndCodec<? super B, ?> codec : codecs) {
            if (codec.type().equals(FGAPayloads.HandshakePayload.TYPE)) {
                return;
            }
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        List rawCodecs = codecs;
        rawCodecs.add(new CustomPacketPayload.TypeAndCodec(
                FGAPayloads.HandshakePayload.TYPE, FGAPayloads.HandshakePayload.STREAM_CODEC));
    }
}
