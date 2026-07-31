//#if MC >= 1.21.1 && MC <= 1.21.5
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSpectatorTeleportMixin {
    @Inject(method = "setGameMode", at = @At("RETURN"))
    private void carpetFga$refreshCommandsAfterGameModeChange(
            GameType gameType, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) || !FGASettings.spectatorFreeTeleport) {
            return;
        }
        ServerPlayer player = (ServerPlayer) (Object) this;
        player.server.getCommands().sendCommands(player);
    }
}
//#endif
