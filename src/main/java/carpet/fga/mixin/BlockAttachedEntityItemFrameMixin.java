//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.ItemFrameBlockificationManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockAttachedEntity.class)
public abstract class BlockAttachedEntityItemFrameMixin {
    @Unique private boolean carpetFga$itemFrameIndexed;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void carpetFga$useBlockDrivenItemFrameChecks(CallbackInfo ci) {
        if ((Object) this instanceof ItemFrame frame
                && !((Entity) (Object) this).level().isClientSide) {
            if (!carpetFga$itemFrameIndexed) {
                ItemFrameBlockificationManager.register(frame);
                carpetFga$itemFrameIndexed = true;
            }
        }
        if (FGASettings.itemFrameBlockification
                && (Object) this instanceof ItemFrame
                && !((Entity) (Object) this).level().isClientSide) {
            ci.cancel();
        }
    }
}
//#endif
