//#if MC >= 1.21.3 && MC <= 26.3
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.EnchantedGoldenCarrotManager;
//$$ import carpet.fga.FGASettings;
//$$ import net.minecraft.world.entity.LivingEntity;
//$$ import net.minecraft.world.entity.player.Player;
//$$ import net.minecraft.world.food.FoodProperties;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.item.component.Consumable;
//$$ import net.minecraft.world.level.Level;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ @Mixin(FoodProperties.class)
//$$ public abstract class FoodPropertiesEnchantedGoldenCarrotMixin {
//$$     @Inject(method = "onConsume", at = @At("HEAD"), cancellable = true)
//$$     private void carpetFga$useNormalGoldenCarrotFoodWhenDisabled(
//$$             Level level,
//$$             LivingEntity entity,
//$$             ItemStack stack,
//$$             Consumable consumable,
//$$             CallbackInfo ci) {
//$$         if (!EnchantedGoldenCarrotManager.isEnchantedGoldenCarrot(stack)
//$$                 || FGASettings.enchantedGoldenCarrot
//$$                 || !(entity instanceof Player player)) return;
//$$         FoodProperties normal = net.minecraft.world.item.Items.GOLDEN_CARROT.getDefaultInstance()
//$$                 .get(net.minecraft.core.component.DataComponents.FOOD);
//$$         if (normal != null) player.getFoodData().eat(normal);
//$$         ci.cancel();
//$$     }
//$$ }
//#endif
