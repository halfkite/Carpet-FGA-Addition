package carpet.fga.mixin;

import carpet.fga.FlatExperienceManager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class FlatExperienceAwardMixin {
    @Inject(method = "giveExperiencePoints", at = @At("HEAD"), cancellable = true)
    private void carpetFga$awardExperience(int amount, CallbackInfo ci) {
        if (FlatExperienceManager.award((Player) (Object) this, amount)) ci.cancel();
    }
}
