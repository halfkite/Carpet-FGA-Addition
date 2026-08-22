//#if MC == 1.20.1 || MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.ItemFrameBlockificationManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityTickList.class)
public abstract class EntityTickListItemFrameMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void carpetFga$keepItemFramesStatic(Entity entity, CallbackInfo ci) {
        if (FGASettings.itemFrameBlockification && entity instanceof ItemFrame) {
            ItemFrameBlockificationManager.register(entity);
            ci.cancel();
        }
    }
}
//#endif
