package carpet.fga.mixin;

//#if MC >= 1.21.5
//$$ import net.minecraft.world.entity.EntityReference;
//$$ import net.minecraft.world.entity.LivingEntity;
//#endif
import carpet.fga.PossessionOwners;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.OwnableEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

@Mixin(OwnableEntity.class)
public interface OwnedAnimalBodyMixin {
    // Resolve the active body in the animal's world, keeping the synced owner field persistent.
    //#if MC >= 1.21.5
    //$$ @ModifyExpressionValue(method = "getOwner", at = @At(value = "INVOKE",
    //$$         target = "Lnet/minecraft/world/entity/OwnableEntity;getOwnerReference()Lnet/minecraft/world/entity/EntityReference;"))
    //$$ private EntityReference<LivingEntity> fga$ownerReference(
    //$$         EntityReference<LivingEntity> stored) {
    //$$     return PossessionOwners.bodyReference(stored);
    //$$ }
    //#else
    @ModifyExpressionValue(method = "getOwner", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/OwnableEntity;getOwnerUUID()Ljava/util/UUID;"))
    private UUID fga$ownerId(UUID stored) {
        return PossessionOwners.counterpartId(stored);
    }
    //#endif
}
