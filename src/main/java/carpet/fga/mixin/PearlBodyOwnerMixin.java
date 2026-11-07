package carpet.fga.mixin;

//#if MC >= 1.21.8 && MC <= 26.3
//$$ import carpet.fga.PossessionOwners;
//$$ import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//$$ import net.minecraft.world.entity.Entity;
//#if MC >= 1.21.11
//$$ import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
//#else
//$$ import net.minecraft.world.entity.projectile.ThrownEnderpearl;
//#endif
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$
//$$ @Mixin(ThrownEnderpearl.class)
//$$ public abstract class PearlBodyOwnerMixin {
//$$     // Only the server-side lookup. The other return delegates to the transformed superclass.
//$$     @ModifyReturnValue(method = "getOwner", at = @At("TAIL"))
//$$     private Entity fga$teleportBody(Entity owner) {
//$$         return PossessionOwners.counterpart(owner);
//$$     }
//$$
//#if MC >= 1.21.10
//$$     @ModifyReturnValue(method = "findOwnerIncludingDeadPlayer", at = @At("RETURN"))
//$$     private static Entity fga$ticketBody(Entity owner) {
//$$         return PossessionOwners.counterpart(owner);
//$$     }
//#endif
//$$ }
//#endif
