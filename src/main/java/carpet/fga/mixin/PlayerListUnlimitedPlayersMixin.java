//#if MC >= 1.21 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.UnlimitedMultiplayerPlayers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
//#if MC <= 1.21.8
import com.mojang.authlib.GameProfile;
//#else
//$$ import net.minecraft.server.players.NameAndId;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

/** Allows connections beyond server.properties max-players while preserving admission checks before it. */
@Mixin(PlayerList.class)
public abstract class PlayerListUnlimitedPlayersMixin {
    @Inject(
            method = "canPlayerLogin",
            //#if MC <= 1.21.8
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/players/PlayerList;maxPlayers:I"),
            //#else
            //$$ at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getMaxPlayers()I"),
            //#endif
            cancellable = true
    )
    private void carpetFga$allowUnlimitedPlayers(
            SocketAddress socketAddress,
            //#if MC <= 1.21.8
            GameProfile gameProfile,
            //#else
            //$$ NameAndId gameProfile,
            //#endif
            CallbackInfoReturnable<Component> cir) {
        if (UnlimitedMultiplayerPlayers.isEnabled()) {
            cir.setReturnValue(null);
        }
    }
}
//#endif
