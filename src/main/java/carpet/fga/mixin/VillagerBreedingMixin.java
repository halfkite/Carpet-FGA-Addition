package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.VillagerBreedingAccess;
import carpet.fga.VillagerBreedingAnimalization;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerBreedingMixin implements VillagerBreedingAccess {
    @Unique
    private int carpetFga$animalizedWillingTicks;

    @Override
    public int carpetFga$getAnimalizedWillingTicks() {
        return carpetFga$animalizedWillingTicks;
    }

    @Override
    public void carpetFga$setAnimalizedWillingTicks(int ticks) {
        carpetFga$animalizedWillingTicks = Math.max(0, ticks);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void carpetFga$tickAnimalizedWillingness(CallbackInfo ci) {
        if (carpetFga$animalizedWillingTicks > 0) {
            carpetFga$animalizedWillingTicks--;
            Villager villager = (Villager) (Object) this;
            if (VillagerBreedingAnimalization.isEnabled()
                    && VillagerBreedingAnimalization.isBreedingAgeEligible(villager)) {
                villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            }
        }
    }

    @Inject(method = "canBreed", at = @At("RETURN"), cancellable = true)
    private void carpetFga$applyAnimalizedWillingness(CallbackInfoReturnable<Boolean> cir) {
        String mode = FGASettings.villagerBreedingAnimalization;
        if (mode.equals("false")) {
            return;
        }
        boolean animalizedCanBreed = VillagerBreedingAnimalization.isBreedingAgeEligible((Villager) (Object) this)
                && carpetFga$animalizedWillingTicks > 0;
        cir.setReturnValue(mode.equals("only") ? animalizedCanBreed : cir.getReturnValue() || animalizedCanBreed);
    }

    @Inject(method = "eatAndDigestFood", at = @At("HEAD"), cancellable = true)
    private void carpetFga$clearAnimalizedWillingness(CallbackInfo ci) {
        if (VillagerBreedingAnimalization.isEnabled() && carpetFga$animalizedWillingTicks > 0) {
            carpetFga$animalizedWillingTicks = 0;
            ci.cancel();
        }
    }
}
