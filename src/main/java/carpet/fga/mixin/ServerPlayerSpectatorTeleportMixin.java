//#if MC >= 1.21 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.SpectatorFreeTeleport;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSpectatorTeleportMixin {
    @Inject(
            method = "setGameMode",
            at = @At("RETURN"),
            //#if MC < 26.3
            require = 0
            //#else
            //$$ require = 1
            //#endif
    )
    private void carpetFga$refreshCommandsAfterGameModeChange(
            GameType gameType, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) || !SpectatorFreeTeleport.isEnabled()) {
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
