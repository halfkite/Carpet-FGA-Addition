//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.PlayerLoadDistanceManager;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerPlayerLoadDistanceMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleClientInformation", at = @At("TAIL"))
    private void carpetFga$refreshRequestedDistance(ServerboundClientInformationPacket packet, CallbackInfo callback) {
        PlayerLoadDistanceManager.reapply(player);
    }
}
//#endif
