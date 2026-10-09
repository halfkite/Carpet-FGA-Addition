//#if MC >= 1.21 && MC <= 26.3
package carpet.fga.mixin;

import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prepare an expanded shulker inventory before vanilla copies item components into it. */
@Mixin(BaseContainerBlockEntity.class)
public abstract class ShulkerBoxItemLoadMixin {
    @Inject(method = "applyImplicitComponents", at = @At("HEAD"))
    private void carpetFga$prepareShulkerCapacity(CallbackInfo ci) {
        if ((Object) this instanceof ShulkerBoxBlockEntity shulker) {
            shulker.getContainerSize();
        }
    }
}
//#endif
