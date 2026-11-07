package carpet.fga.mixin;

//#if MC >= 1.21.8
//$$ import net.minecraft.world.entity.EntityReference;
//#endif
import carpet.fga.PossessionOwners;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;

@Mixin(AreaEffectCloud.class)
public abstract class AreaEffectCloudBodyOwnerMixin {
    //#if MC >= 1.21.8
    //$$ @Shadow private EntityReference<LivingEntity> owner;
    //#else
    @Shadow private UUID ownerUUID;
    //#endif

    @ModifyVariable(method = "setOwner", at = @At("HEAD"), argsOnly = true)
    private LivingEntity fga$storeOwner(LivingEntity owner) {
        return (LivingEntity) PossessionOwners.counterpart(owner);
    }

    @ModifyReturnValue(method = "getOwner()Lnet/minecraft/world/entity/LivingEntity;", at = @At("RETURN"))
    private LivingEntity fga$activeOwner(LivingEntity resolved) {
        if (resolved != null) return (LivingEntity) PossessionOwners.counterpart(resolved);
        //#if MC >= 1.21.8
        //$$ UUID id = owner == null ? null : owner.getUUID();
        //#else
        UUID id = ownerUUID;
        //#endif
        return PossessionOwners.resolveMissingOwner(id, ((AreaEffectCloud) (Object) this).level());
    }
}
