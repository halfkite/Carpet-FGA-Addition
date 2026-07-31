//#if MC >= 1.16.5 && MC < 26.0
package carpet.fga.mixin;

import carpet.fga.WanderingTraderNoDespawn;
import net.minecraft.world.entity.npc.WanderingTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WanderingTrader.class)
abstract class WanderingTraderNoDespawnMixin {
    @Inject(method = "maybeDespawn()V", at = @At("HEAD"), cancellable = true)
    private void carpetFga$preventConfiguredDespawn(CallbackInfo callback) {
        if (WanderingTraderNoDespawn.preventsDespawn((WanderingTrader) (Object) this)) callback.cancel();
    }
}
//#endif
