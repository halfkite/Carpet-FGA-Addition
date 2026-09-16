package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.3
import carpet.fga.PlayerPossessionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restore possession on the server thread before vanilla saves and removes the player. */
@Mixin(PlayerList.class)
public abstract class PossessionLogoutMixin {
    @Shadow public abstract MinecraftServer getServer();

    @Inject(method = "remove", at = @At("HEAD"))
    private void fga$releaseBeforeLogoutSave(ServerPlayer player, CallbackInfo ci) {
        PlayerPossessionManager.disconnected(player, getServer());
    }
}
//#endif
