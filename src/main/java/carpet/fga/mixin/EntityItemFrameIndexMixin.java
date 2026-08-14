//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.ItemFrameBlockificationManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityItemFrameIndexMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void carpetFga$unindexRemovedItemFrame(Entity.RemovalReason reason, CallbackInfo ci) {
        ItemFrameBlockificationManager.unregister((Entity) (Object) this);
    }
}
//#endif
