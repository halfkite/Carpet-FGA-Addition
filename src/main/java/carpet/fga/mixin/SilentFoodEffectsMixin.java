package carpet.fga.mixin;

//#if MC >= 1.21.3
//$$ import carpet.fga.SilentFoodTransaction;
//$$ import net.minecraft.world.item.component.Consumable;
//$$ import net.minecraft.world.entity.LivingEntity;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.util.RandomSource;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//$$ @Mixin(Consumable.class)
//$$ public abstract class SilentFoodEffectsMixin
//$$ {
//$$     @Inject(method = "emitParticlesAndSounds", at = @At("HEAD"), cancellable = true)
//$$     private void fga$silentFood(RandomSource random, LivingEntity entity, ItemStack stack, int count, CallbackInfo ci)
//$$     { if (SilentFoodTransaction.isSilent(entity)) ci.cancel(); }
//$$ }
//#endif
