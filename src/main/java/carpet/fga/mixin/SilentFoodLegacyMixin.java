package carpet.fga.mixin;

//#if MC == 1.21.1
import carpet.fga.SilentFoodTransaction;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Both vanilla eat implementations emit sound, while still applying their normal food effects. */
@Mixin({LivingEntity.class, Player.class})
public abstract class SilentFoodLegacyMixin {
    @WrapOperation(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private void fga$silentEat(Level level, Player excluded, double x, double y, double z,
                               SoundEvent sound, SoundSource source, float volume, float pitch,
                               Operation<Void> original) {
        if (!SilentFoodTransaction.isSilent((LivingEntity) (Object) this)) {
            original.call(level, excluded, x, y, z, sound, source, volume, pitch);
        }
    }
}
//#endif
