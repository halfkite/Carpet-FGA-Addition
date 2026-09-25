package fga.handshaketest.mixin;

import carpet.fga.FGAPayloads;
import fga.handshaketest.HandshakeSabotage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * Reproduces the reported condition: the payload list reaches vanilla's codec without FGA's handshake
 * because a lower priority mod rebuilt the list from a copy (Carpet does exactly that to add its own
 * payload). The list is edited in place so Carpet's recursion guard keeps working, and the removal is
 * recorded so the test can prove the condition really happened.
 */
@Mixin(value = CustomPacketPayload.class, priority = 1500)
public interface HandshakeInterferenceMixin {
    @ModifyVariable(
            method = "codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static <B extends FriendlyByteBuf> List<CustomPacketPayload.TypeAndCodec<? super B, ?>> fgaTest$dropHandshakeEntry(
            List<CustomPacketPayload.TypeAndCodec<? super B, ?>> codecs) {
        int before = codecs.size();
        codecs.removeIf(value -> value.type().id().equals(FGAPayloads.HANDSHAKE_CHANNEL));
        int removed = before - codecs.size();
        if (removed > 0) {
            HandshakeSabotage.removed += removed;
            HandshakeSabotage.stripped = true;
        }
        return codecs;
    }
}
