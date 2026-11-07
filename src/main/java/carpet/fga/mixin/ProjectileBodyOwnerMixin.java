package carpet.fga.mixin;

//#if MC >= 1.21.8
//$$ import net.minecraft.world.entity.EntityReference;
//#endif
import carpet.fga.PossessionOwners;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;

@Mixin(Projectile.class)
public abstract class ProjectileBodyOwnerMixin {
    //#if MC >= 1.21.8
    //$$ @Shadow protected EntityReference<Entity> owner;
    //#else
    @Shadow private UUID ownerUUID;
    //#endif

    @ModifyVariable(method = "setOwner(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), argsOnly = true)
    private Entity fga$storeShooter(Entity shooter) {
        return PossessionOwners.counterpart(shooter);
    }

    @ModifyReturnValue(method = "getOwner", at = @At("RETURN"))
    private Entity fga$resolveShooter(Entity resolved) {
        if (resolved != null) return PossessionOwners.counterpart(resolved);
        //#if MC >= 1.21.8
        //$$ UUID id = owner == null ? null : owner.getUUID();
        //#else
        UUID id = ownerUUID;
        //#endif
        return PossessionOwners.resolveMissingOwner(id, ((Projectile) (Object) this).level());
    }

    @ModifyVariable(method = "ownedBy", at = @At("HEAD"), argsOnly = true)
    private Entity fga$compareShooter(Entity candidate) {
        return PossessionOwners.counterpart(candidate);
    }
}
