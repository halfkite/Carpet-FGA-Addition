package carpet.fga.mixin;

import carpet.fga.PossessionOwners;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DamageSource.class)
public abstract class DamageBodyAttributionMixin {
    @ModifyVariable(method = "<init>(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Entity fga$storeDirectSource(Entity direct) {
        return PossessionOwners.counterpart(direct);
    }

    @ModifyVariable(method = "<init>(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static Entity fga$storeAttacker(Entity attacker) {
        return PossessionOwners.counterpart(attacker);
    }

    @ModifyExpressionValue(method = {"getDirectEntity", "getWeaponItem", "getSourcePosition", "getLocalizedDeathMessage"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/damagesource/DamageSource;directEntity:Lnet/minecraft/world/entity/Entity;"))
    private Entity fga$directBody(Entity direct) {
        return PossessionOwners.counterpart(direct);
    }

    @ModifyExpressionValue(method = {"getEntity", "scalesWithDifficulty", "getLocalizedDeathMessage"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/damagesource/DamageSource;causingEntity:Lnet/minecraft/world/entity/Entity;"))
    private Entity fga$attackerBody(Entity attacker) {
        return PossessionOwners.counterpart(attacker);
    }
}
