package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.2
import carpet.fga.EnchantmentLevelRules;
import carpet.fga.FGASettings;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public abstract class EnchantmentMaxLevelMixin {
    @Inject(method = "getMaxLevel", at = @At("RETURN"), cancellable = true)
    private void carpetFga$increaseMaximumLevel(CallbackInfoReturnable<Integer> callback) {
        int increase = FGASettings.enchantmentLevelLimitIncrease();
        if (increase > 0) {
            callback.setReturnValue(EnchantmentLevelRules.increaseLimit(callback.getReturnValue(), increase));
        }
    }
}
//#endif
