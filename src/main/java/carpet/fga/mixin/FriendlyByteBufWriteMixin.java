package carpet.fga.mixin;

import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//#if MC >= 1.20.2
import org.spongepowered.asm.mixin.injection.ModifyArg;
//#else
//$$ import org.spongepowered.asm.mixin.injection.ModifyVariable;
//#endif

/**
 * Widen the vanilla 16-character limit used when writing GameProfile names.
 */
@Mixin(FriendlyByteBuf.class)
public abstract class FriendlyByteBufWriteMixin {
    //#if MC >= 1.20.2
    @ModifyArg(
            method = "writeUtf(Ljava/lang/String;I)Lnet/minecraft/network/FriendlyByteBuf;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/Utf8String;write(Lio/netty/buffer/ByteBuf;Ljava/lang/CharSequence;I)V"
            ),
            index = 2
    )
    private int increasePlayerNameLimit(int maxLength) {
        return maxLength == 16 ? 128 : maxLength;
    }
    //#else
    //$$ @ModifyVariable(
    //$$         method = "writeUtf(Ljava/lang/String;I)Lnet/minecraft/network/FriendlyByteBuf;",
    //$$         at = @At("HEAD"),
    //$$         argsOnly = true,
    //$$         ordinal = 0
    //$$ )
    //$$ private int increasePlayerNameLimit(int maxLength) {
    //$$     return maxLength == 16 ? 128 : maxLength;
    //$$ }
    //#endif
}