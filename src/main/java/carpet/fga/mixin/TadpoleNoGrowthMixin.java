//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.BabyMobNoGrowth;
import net.minecraft.world.entity.animal.frog.Tadpole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Tadpole.class)
abstract class TadpoleNoGrowthMixin {
    @Redirect(
            method = "aiStep()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/frog/Tadpole;setAge(I)V")
    )
    private void carpetFga$preventNaturalGrowth(Tadpole tadpole, int newAge) {
        if (BabyMobNoGrowth.isLocked(tadpole)) return;
        carpetFga$setAge(newAge);
    }

    @Inject(method = "ageUp(I)V", at = @At("HEAD"), cancellable = true)
    private void carpetFga$preventAcceleratedGrowth(int seconds, CallbackInfo callback) {
        if (seconds > 0 && BabyMobNoGrowth.isLocked((Tadpole) (Object) this)) callback.cancel();
    }

    @Invoker("setAge")
    protected abstract void carpetFga$setAge(int age);
}
//#endif
