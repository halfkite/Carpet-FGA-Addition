//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.EnchantedGoldenCarrotManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public abstract class PlayerEnchantedGoldenCarrotMixin {
    @Redirect(method = "eat",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
    private void carpetFga$useNormalGoldenCarrotFoodWhenDisabled(
            FoodData foodData,
            FoodProperties food,
            Level level,
            ItemStack stack,
            FoodProperties originalFood) {
        foodData.eat(EnchantedGoldenCarrotManager.foodPropertiesForUse(stack, food));
    }
}
//#endif
