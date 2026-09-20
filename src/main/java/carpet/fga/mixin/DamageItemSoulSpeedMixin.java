//#if MC >= 1.21 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.EnchantmentDurabilityManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.effects.DamageItem;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DamageItem.class)
public abstract class DamageItemSoulSpeedMixin {
    @Redirect(
            method = "apply",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V"
            )
    )
    private void carpetFga$suppressSoulSpeedDamage(
            net.minecraft.world.item.ItemStack stack,
            int amount,
            ServerLevel level,
            ServerPlayer player,
            //#if MC >= 26.3
            //$$ Consumer<net.minecraft.world.item.ItemStack> onBroken) {
            //#else
            Consumer<Item> onBroken) {
            //#endif
        if (!EnchantmentDurabilityManager.suppressSoulSpeedDamage(level, stack)) {
            stack.hurtAndBreak(amount, level, player, onBroken);
        }
    }
}
//#endif
