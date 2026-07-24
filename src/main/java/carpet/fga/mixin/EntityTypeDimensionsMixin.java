package carpet.fga.mixin;

import carpet.fga.BeeDimensions;
import carpet.fga.FGASettings;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
public abstract class EntityTypeDimensionsMixin {
    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void carpetFga$restorePre26BeeDimensions(CallbackInfoReturnable<EntityDimensions> cir) {
        if (FGASettings.restorePre26BeeCollisionBox
                && BeeDimensions.isBee((EntityType<?>) (Object) this)) {
            cir.setReturnValue(BeeDimensions.pre26Dimensions());
        }
    }
}
