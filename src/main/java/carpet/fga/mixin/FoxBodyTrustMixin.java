package carpet.fga.mixin;

//#if MC >= 1.21.5
//$$ import net.minecraft.world.entity.LivingEntity;
//#endif
import carpet.fga.PossessionOwners;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//#if MC >= 1.21.11
//$$ import net.minecraft.world.entity.animal.fox.Fox;
//#else
import net.minecraft.world.entity.animal.Fox;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;

@Mixin(Fox.class)
public abstract class FoxBodyTrustMixin {
    //#if MC >= 1.21.5
    //$$ @ModifyVariable(method = {"addTrustedEntity(Lnet/minecraft/world/entity/LivingEntity;)V", "trusts"}, at = @At("HEAD"), argsOnly = true)
    //$$ private LivingEntity fga$trustedIdentity(LivingEntity player) {
    //$$     return (LivingEntity) PossessionOwners.counterpart(player);
    //$$ }
    //#else
    @ModifyExpressionValue(method = "onOffspringSpawnedFromEgg", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getUUID()Ljava/util/UUID;"))
    private UUID fga$trustSpawner(UUID actor) {
        return PossessionOwners.counterpartId(actor);
    }

    @ModifyVariable(method = "trusts", at = @At("HEAD"), argsOnly = true)
    private UUID fga$trustedIdentity(UUID candidate) {
        return PossessionOwners.counterpartId(candidate);
    }
    //#endif
}
