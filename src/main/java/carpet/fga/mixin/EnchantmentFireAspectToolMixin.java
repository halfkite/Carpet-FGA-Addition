//#if MC >= 1.21 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.FireAspectToolManager;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public abstract class EnchantmentFireAspectToolMixin {
    @Inject(method = "isSupportedItem", at = @At("HEAD"), cancellable = true)
    private void carpetFga$allowFireAspectOnTools(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (FireAspectToolManager.allowsFireAspect((Enchantment) (Object) this, stack)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "isPrimaryItem", at = @At("HEAD"), cancellable = true)
    private void carpetFga$blockEnchantmentTableFireAspect(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (FireAspectToolManager.blocksEnchantmentTableFireAspect((Enchantment) (Object) this, stack)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
    private void carpetFga$allowAnvilFireAspectOnTools(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (FireAspectToolManager.allowsFireAspect((Enchantment) (Object) this, stack)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "areCompatible", at = @At("HEAD"), cancellable = true)
    private static void carpetFga$conflictWithSilkTouch(
            Holder<Enchantment> first,
            Holder<Enchantment> second,
            CallbackInfoReturnable<Boolean> callback) {
        if (FireAspectToolManager.conflictsWithSilkTouch(first, second)) {
            callback.setReturnValue(false);
        }
    }
}
//#endif
