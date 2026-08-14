//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.MinecartFeatureManager;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Minecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartFeatureMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void carpetFga$beforeFeatureTick(CallbackInfo ci) {
        if ((Object) this instanceof Minecart minecart) MinecartFeatureManager.beforeMinecartTick(minecart);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void carpetFga$afterFeatureTick(CallbackInfo ci) {
        if ((Object) this instanceof Minecart minecart) MinecartFeatureManager.afterMinecartTick(minecart);
    }

    @Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
    private void carpetFga$featureMaxSpeed(CallbackInfoReturnable<Double> cir) {
        if ((Object) this instanceof Minecart minecart) {
            cir.setReturnValue(MinecartFeatureManager.maxSpeed(minecart, cir.getReturnValue()));
        }
    }

    @Inject(method = "applyNaturalSlowdown", at = @At("HEAD"), cancellable = true)
    private void carpetFga$replaceBoostSlowdown(CallbackInfo ci) {
        if ((Object) this instanceof Minecart minecart
                && MinecartFeatureManager.suppressNaturalSlowdown(minecart)) {
            ci.cancel();
        }
    }
}
//#endif
