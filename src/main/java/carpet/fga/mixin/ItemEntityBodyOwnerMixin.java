package carpet.fga.mixin;

//#if MC >= 1.21.8
//$$ import net.minecraft.world.entity.EntityReference;
//#endif
import carpet.fga.PossessionOwners;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;

@Mixin(ItemEntity.class)
public abstract class ItemEntityBodyOwnerMixin {
    //#if MC >= 1.21.8
    //$$ @Shadow private EntityReference<Entity> thrower;
    //#else
    @Shadow private UUID thrower;
    //#endif

    @ModifyVariable(method = "setThrower", at = @At("HEAD"), argsOnly = true)
    private Entity fga$storeOwner(Entity owner) {
        return PossessionOwners.counterpart(owner);
    }

    @ModifyReturnValue(method = "getOwner()Lnet/minecraft/world/entity/Entity;", at = @At("RETURN"))
    private Entity fga$activeOwner(Entity resolved) {
        if (resolved != null) return PossessionOwners.counterpart(resolved);
        //#if MC >= 1.21.8
        //$$ UUID id = thrower == null ? null : thrower.getUUID();
        //#else
        UUID id = thrower;
        //#endif
        return PossessionOwners.resolveMissingOwner(id, ((ItemEntity) (Object) this).level());
    }
}
