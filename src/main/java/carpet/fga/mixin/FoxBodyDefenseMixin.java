package carpet.fga.mixin;

//#if MC >= 1.21.5
//$$ import java.util.stream.Stream;
//$$ import net.minecraft.world.entity.EntityReference;
//$$ import net.minecraft.world.entity.LivingEntity;
//$$ import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//#endif
import carpet.fga.PossessionOwners;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.UUID;

//#if MC >= 1.21.11
//$$ @Mixin(targets = "net.minecraft.world.entity.animal.fox.Fox$DefendTrustedTargetGoal")
//#else
@Mixin(targets = "net.minecraft.world.entity.animal.Fox$DefendTrustedTargetGoal")
//#endif
public abstract class FoxBodyDefenseMixin {
    //#if MC >= 1.21.5
    //$$ @ModifyExpressionValue(method = "canUse", at = @At(value = "INVOKE",
    //#if MC >= 1.21.11
    //$$         target = "Lnet/minecraft/world/entity/animal/fox/Fox;getTrustedEntities()Ljava/util/stream/Stream;"))
    //#else
    //$$         target = "Lnet/minecraft/world/entity/animal/Fox;getTrustedEntities()Ljava/util/stream/Stream;"))
    //#endif
    //$$ private Stream<EntityReference<LivingEntity>> fga$defendedBodies(
    //$$         Stream<EntityReference<LivingEntity>> stored) {
    //$$     return PossessionOwners.hasSessions() ? stored.map(PossessionOwners::bodyReference) : stored;
    //$$ }
    //#else
    @ModifyArg(method = "canUse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getEntity(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;"), index = 0)
    private UUID fga$defendedBody(UUID stored) {
        return PossessionOwners.counterpartId(stored);
    }
    //#endif
}
