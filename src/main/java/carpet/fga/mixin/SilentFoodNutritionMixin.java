package carpet.fga.mixin;
//#if MC >= 1.21.3
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//$$ import com.llamalad7.mixinextras.sugar.Local;
//$$ import carpet.fga.SilentFoodTransaction;
//$$ import net.minecraft.world.entity.Entity;
//#if MC <= 1.21.4
//$$ import net.minecraft.world.entity.player.Player;
//#endif
//$$ import net.minecraft.world.entity.LivingEntity;
//$$ import net.minecraft.world.food.FoodProperties;
//$$ import net.minecraft.world.level.Level;
//$$ import net.minecraft.sounds.SoundEvent;
//$$ import net.minecraft.sounds.SoundSource;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ @Mixin(FoodProperties.class)
//$$ public abstract class SilentFoodNutritionMixin {
//#if MC >= 1.21.5
//$$     @WrapOperation(method = "onConsume", at = @At(value = "INVOKE", target =
//$$             "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
//$$     private void fga$silentNutrition(Level level, Entity excluded, double x, double y, double z,
//#else
//$$     @WrapOperation(method = "onConsume", at = @At(value = "INVOKE", target =
//$$             "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
//$$     private void fga$silentNutrition(Level level, Player excluded, double x, double y, double z,
//#endif
//$$             SoundEvent sound, SoundSource category, float volume, float pitch, Operation<Void> original,
//$$             @Local(argsOnly = true) LivingEntity consumer) {
//$$         if (!SilentFoodTransaction.isSilent(consumer)) original.call(level, excluded, x, y, z, sound, category, volume, pitch);
//$$     }
//$$ }
//#endif
