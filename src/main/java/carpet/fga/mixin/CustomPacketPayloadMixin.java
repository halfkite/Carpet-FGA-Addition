//#if MC >= 1.20.5
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
        // Carpet recursively calls this codec with a marker list. Copying that list removes
        // its recursion guard and causes a StackOverflowError when both mods are client-side.
        List<CustomPacketPayload.TypeAndCodec<? super B, ?>> extendedCodecs =
                codecs.getClass().getName().equals("carpet.helpers.CarpetTaintedList")
                        ? codecs : new ArrayList<>(codecs);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List rawCodecs = extendedCodecs;
        addCodec(rawCodecs, codecs, FGAPayloads.HandshakePayload.TYPE, FGAPayloads.HandshakePayload.STREAM_CODEC);
        //#if MC >= 1.21 && MC <= 1.21.1
        addCodec(rawCodecs, codecs, FGAPayloads.EntityPlaceHelloPayload.TYPE,
                FGAPayloads.EntityPlaceHelloPayload.STREAM_CODEC);
        addCodec(rawCodecs, codecs, FGAPayloads.EntityPlaceCapabilityPayload.TYPE,
                FGAPayloads.EntityPlaceCapabilityPayload.STREAM_CODEC);
        addCodec(rawCodecs, codecs, FGAPayloads.EntityPlaceRequestPayload.TYPE,
                FGAPayloads.EntityPlaceRequestPayload.STREAM_CODEC);
        addCodec(rawCodecs, codecs, FGAPayloads.EntityPlaceResultPayload.TYPE,
                FGAPayloads.EntityPlaceResultPayload.STREAM_CODEC);
        //#endif
        return extendedCodecs;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addCodec(List rawCodecs,
            List<? extends CustomPacketPayload.TypeAndCodec<?, ?>> existing,
            CustomPacketPayload.Type<?> type, StreamCodec<?, ?> codec) {
        if (existing.stream().noneMatch(value -> value.type().equals(type))) {
            rawCodecs.add(new CustomPacketPayload.TypeAndCodec(type, codec));
        }
    }
}
//#endif
