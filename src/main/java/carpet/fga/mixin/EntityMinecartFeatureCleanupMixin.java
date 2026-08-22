//#if MC == 1.20.1 || MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.MinecartFeatureManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Minecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMinecartFeatureCleanupMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void carpetFga$cleanMinecartFeatures(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof Minecart minecart) {
            MinecartFeatureManager.minecartRemoved(minecart, reason);
        }
    }
}
//#endif
