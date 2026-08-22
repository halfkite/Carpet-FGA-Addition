//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.BabyMobNoGrowth;
import net.minecraft.world.entity.AgeableMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AgeableMob.class)
abstract class AgeableMobNoGrowthMixin {
    @Redirect(
            method = "aiStep()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/AgeableMob;setAge(I)V")
    )
    private void carpetFga$preventNaturalBabyGrowth(AgeableMob mob, int newAge) {
        if (newAge > mob.getAge() && mob.isBaby() && BabyMobNoGrowth.isLocked(mob)) return;
        mob.setAge(newAge);
    }

    @Inject(method = "ageUp(IZ)V", at = @At("HEAD"), cancellable = true)
    private void carpetFga$preventAcceleratedBabyGrowth(int seconds, boolean forced, CallbackInfo callback) {
        AgeableMob mob = (AgeableMob) (Object) this;
        if (seconds > 0 && mob.isBaby() && BabyMobNoGrowth.isLocked(mob)) callback.cancel();
    }
}
//#endif
