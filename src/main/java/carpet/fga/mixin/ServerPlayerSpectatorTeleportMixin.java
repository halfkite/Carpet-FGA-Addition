//#if MC >= 1.21 && MC <= 26.2
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
    @Inject(method = "setGameMode", at = @At("RETURN"), require = 0)
    private void carpetFga$refreshCommandsAfterGameModeChange(
            GameType gameType, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) || !FGASettings.spectatorFreeTeleport) {
            return;
        }
        ServerPlayer player = (ServerPlayer) (Object) this;
        //#if MC >= 1.21.10
        //$$ player.level().getServer().getCommands().sendCommands(player);
        //#elseif MC >= 1.21.8
        //$$ player.getServer().getCommands().sendCommands(player);
        //#else
        player.server.getCommands().sendCommands(player);
        //#endif
    }
}
//#endif
