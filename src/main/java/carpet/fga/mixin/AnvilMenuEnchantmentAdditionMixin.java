package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.2
import carpet.fga.EnchantmentLevelRules;
import carpet.fga.FGASettings;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuEnchantmentAdditionMixin {
    @Unique
    private boolean carpetFga$invalidAddition;

    @Inject(method = "createResult", at = @At("HEAD"))
    private void carpetFga$resetInvalidAddition(CallbackInfo callback) {
        carpetFga$invalidAddition = false;
    }

    @ModifyVariable(
        method = "createResult",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I"),
        index = 17
    )
    private int carpetFga$addEnchantmentLevels(int vanillaLevel,
                                               @Local(index = 16) int firstLevel,
                                               @Local(index = 14) Object2IntMap.Entry<Holder<Enchantment>> entry) {
        if (!FGASettings.enchantmentLevelAddition) return vanillaLevel;
        int secondLevel = entry.getIntValue();
        int maximum = entry.getKey().value().getMaxLevel();
        if (firstLevel > 0) {
            if (EnchantmentLevelRules.rejectsAddition(firstLevel, secondLevel, maximum)) {
                carpetFga$invalidAddition = true;
            }
        }
        return EnchantmentLevelRules.addLevels(firstLevel, secondLevel, maximum);
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void carpetFga$clearOverLimitResult(CallbackInfo callback) {
        if (FGASettings.enchantmentLevelAddition && carpetFga$invalidAddition) {
            ((ItemCombinerMenuAccessor) this).carpetFga$getResultSlots().setItem(0, ItemStack.EMPTY);
        }
    }
}
//#endif
