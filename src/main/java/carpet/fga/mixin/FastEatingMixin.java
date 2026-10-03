//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
//#if MC >= 1.21.2
//$$ import net.minecraft.world.item.ItemUseAnimation;
//#else
import net.minecraft.world.item.UseAnim;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class FastEatingMixin {
    @Shadow protected ItemStack useItem;
    @Shadow protected int useItemRemaining;

    @Inject(method = "startUsingItem", at = @At("TAIL"))
    private void carpetFga$fastEat(InteractionHand hand, CallbackInfo ci) {
        if (!FGASettings.fastEating || !((Object) this instanceof ServerPlayer) || useItem.isEmpty()) return;
        //#if MC >= 1.21.2
        //$$ ItemUseAnimation animation = useItem.getUseAnimation();
        //$$ if (animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK) {
        //#else
        UseAnim animation = useItem.getUseAnimation();
        if (animation == UseAnim.EAT || animation == UseAnim.DRINK) {
        //#endif
            useItemRemaining = Math.min(useItemRemaining, 8);
        }
    }
}
//#endif
