package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.2
import carpet.fga.PlayerPossessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class PossessionDeathMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void fga$releaseBeforeDeath(DamageSource source, CallbackInfo ci) {
        PlayerPossessionManager.endFor((ServerPlayer) (Object) this);
    }
}
//#endif
