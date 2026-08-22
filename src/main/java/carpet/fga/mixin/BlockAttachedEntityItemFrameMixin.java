//#if MC == 1.20.1 || MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.ItemFrameBlockificationManager;
import net.minecraft.world.entity.Entity;
//#if MC >= 1.20.5
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
//#else
//$$ import net.minecraft.world.entity.decoration.HangingEntity;
//#endif
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        //#if MC >= 1.20.5
        BlockAttachedEntity.class
        //#else
        //$$ HangingEntity.class
        //#endif
)
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
