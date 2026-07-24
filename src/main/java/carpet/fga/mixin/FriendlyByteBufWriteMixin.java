package carpet.fga.mixin;

import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 放宽玩家信息包写入 GameProfile 名称时使用的原版 16 字符限制。
 */
@Mixin(FriendlyByteBuf.class)
public abstract class FriendlyByteBufWriteMixin {
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
}
