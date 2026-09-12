package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.2
import carpet.fga.PlayerPossessionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PossessionPlayerNameMixin {
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void fga$appendControllerName(CallbackInfoReturnable<Component> callback) {
        if ((Object) this instanceof ServerPlayer player) {
            callback.setReturnValue(PlayerPossessionManager.decorateName(player, callback.getReturnValue()));
        }
    }
}
//#endif
