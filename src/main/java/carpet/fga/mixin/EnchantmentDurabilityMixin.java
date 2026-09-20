//#if MC >= 1.21 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.EnchantmentDurabilityManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Enchantment.class)
public abstract class EnchantmentDurabilityMixin {
    @Inject(method = "modifyDurabilityChange", at = @At("HEAD"), cancellable = true)
    private void carpetFga$suppressSelectedDurabilityEffect(
            ServerLevel level, int enchantmentLevel, ItemStack stack, MutableFloat damage, CallbackInfo callback) {
        if (EnchantmentDurabilityManager.suppressDurabilityEffect((Enchantment) (Object) this)) {
            callback.cancel();
        }
    }

    @Redirect(
            method = "runLocationChangedEffects",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/effects/EnchantmentLocationBasedEffect;onChangedBlock(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/enchantment/EnchantedItemInUse;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Z)V"
            )
    )
    private void carpetFga$suppressSoulSpeedDurability(
            EnchantmentLocationBasedEffect effect,
            ServerLevel level,
            int enchantmentLevel,
            EnchantedItemInUse item,
            Entity entity,
            Vec3 position,
            boolean previouslyActive) {
        if (!EnchantmentDurabilityManager.suppressSoulSpeedLocationDurability(
                (Enchantment) (Object) this, effect)) {
            effect.onChangedBlock(level, enchantmentLevel, item, entity, position, previouslyActive);
        }
    }
}
//#endif
