package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.2
import carpet.fga.PlayerPossessionManager;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PossessionServerDisconnectMixin {
    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void fga$releaseOnDisconnect(DisconnectionDetails details, CallbackInfo ci) {
        PlayerPossessionManager.disconnected(((ServerGamePacketListenerImpl) (Object) this).player,
                carpet.CarpetServer.minecraft_server);
    }
}
//#endif
