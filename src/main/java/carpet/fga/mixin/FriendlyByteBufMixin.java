package carpet.fga.mixin;

import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 客户端 Mixin：修改 FriendlyByteBuf.readUtf(int) 内部对 Utf8String.read 的调用。
 * 当 maxLength 为 16（原版玩家名限制）时替换为 128，
 * 以支持服务端发来的超过 16 字符的假人名字。
 */
@Mixin(FriendlyByteBuf.class)
public abstract class FriendlyByteBufMixin {

    /**
     * 拦截 readUtf(int) 中调用 Utf8String.read(ByteBuf, int) 的 maxLength 参数。
     * 将原版 16 字符限制替换为 128，防止客户端因长名字崩溃。
     */
    @ModifyArg(
        method = "readUtf(I)Ljava/lang/String;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/Utf8String;read(Lio/netty/buffer/ByteBuf;I)Ljava/lang/String;"
        ),
        index = 1
    )
    private int increaseMaxLength(int maxLength) {
        return maxLength == 16 ? 128 : maxLength;
    }
}
