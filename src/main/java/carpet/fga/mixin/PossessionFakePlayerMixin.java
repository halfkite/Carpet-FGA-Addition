package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.2
import carpet.fga.PlayerPossessionManager;
import carpet.patches.EntityPlayerMPFake;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityPlayerMPFake.class, remap = false)
public abstract class PossessionFakePlayerMixin {
    // Use the stable Carpet method name and let Mixin resolve its overload;
    // the production Carpet jar keeps only kill(Component), while the
    // development mappings also expose the inherited no-argument hook.
    @Inject(method = "kill", at = @At("HEAD"), remap = false)
    private void fga$releaseBeforeKill(CallbackInfo ci) {
        PlayerPossessionManager.endFor((net.minecraft.server.level.ServerPlayer) (Object) this);
    }
}
//#endif
