package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.2
import carpet.fga.PlayerPossessionManager;
import carpet.helpers.EntityPlayerActionPack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityPlayerActionPack.class, remap = false)
public abstract class PossessionActionPackMixin {
    @Shadow @Final private ServerPlayer player;

    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true)
    private void fga$suspendAutomaticActions(CallbackInfo ci) {
        if (PlayerPossessionManager.isParticipant(player)) ci.cancel();
    }
}
//#endif
