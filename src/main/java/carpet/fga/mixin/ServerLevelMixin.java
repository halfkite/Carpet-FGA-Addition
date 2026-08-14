//#if MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.DeathDropPreStackManager;
//#if MC == 1.21.1
import carpet.fga.ItemFrameBlockificationManager;
//#endif
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void carpetFga$captureMobDeathDrop(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (DeathDropPreStackManager.capture((ServerLevel) (Object) this, entity)) {
            cir.setReturnValue(true);
        }
    }

    //#if MC == 1.21.1
    @Inject(method = "addEntity", at = @At("RETURN"))
    private void carpetFga$indexItemFrame(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) ItemFrameBlockificationManager.register(entity);
    }

    //#endif
}
//#endif
