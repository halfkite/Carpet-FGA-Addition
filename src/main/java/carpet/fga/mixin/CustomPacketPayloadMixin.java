package carpet.fga.mixin;

import carpet.fga.FGAPayloads;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(CustomPacketPayload.class)
public interface CustomPacketPayloadMixin {
    @ModifyVariable(
            method = "codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static <B extends FriendlyByteBuf> List<CustomPacketPayload.TypeAndCodec<? super B, ?>> registerHandshake(
            List<CustomPacketPayload.TypeAndCodec<? super B, ?>> codecs) {
        for (CustomPacketPayload.TypeAndCodec<? super B, ?> codec : codecs) {
            if (codec.type().equals(FGAPayloads.HandshakePayload.TYPE)) {
                return codecs;
            }
        }
        List<CustomPacketPayload.TypeAndCodec<? super B, ?>> extendedCodecs = new ArrayList<>(codecs);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List rawCodecs = extendedCodecs;
        rawCodecs.add(new CustomPacketPayload.TypeAndCodec(
                FGAPayloads.HandshakePayload.TYPE, FGAPayloads.HandshakePayload.STREAM_CODEC));
        return extendedCodecs;
    }
}
