//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.NetherPortalLightManager;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sends the vanilla light packet as soon as the worker light recalculation completes. */
@Mixin(ThreadedLevelLightEngine.class)
public abstract class NetherPortalLightUpdateMixin {
    @Inject(method = "runUpdate", at = @At("RETURN"))
    private void carpetFga$flushPortalLightUpdate(CallbackInfo ci) {
        NetherPortalLightManager.schedulePendingClientRefreshes();
    }
}
//#endif
