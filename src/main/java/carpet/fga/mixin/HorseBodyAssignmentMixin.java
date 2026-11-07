package carpet.fga.mixin;

//#if MC >= 1.21.5
//$$ import net.minecraft.world.entity.LivingEntity;
//$$ import org.spongepowered.asm.mixin.injection.ModifyVariable;
//#endif
import carpet.fga.PossessionOwners;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//#if MC >= 1.21.11
//$$ import net.minecraft.world.entity.animal.equine.AbstractHorse;
//#else
import net.minecraft.world.entity.animal.horse.AbstractHorse;
//#endif
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

@Mixin(AbstractHorse.class)
public abstract class HorseBodyAssignmentMixin {
    //#if MC >= 1.21.5
    //$$ @ModifyVariable(method = "setOwner", at = @At("HEAD"), argsOnly = true)
    //$$ private LivingEntity fga$storeOwner(LivingEntity owner) {
    //$$     return (LivingEntity) PossessionOwners.counterpart(owner);
    //$$ }
    //#else
    @ModifyExpressionValue(method = "tameWithName", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getUUID()Ljava/util/UUID;"))
    private UUID fga$storeOwner(UUID original, Player player) {
        return PossessionOwners.counterpart(player).getUUID();
    }
    //#endif
}
