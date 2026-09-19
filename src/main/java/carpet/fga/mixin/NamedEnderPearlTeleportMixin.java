//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.NamedEnderPearlTeleport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes a custom-named ender pearl teleport the matching online player. */
@Mixin(ThrownEnderpearl.class)
public abstract class NamedEnderPearlTeleportMixin {
    @Inject(method = "onHit", at = @At("HEAD"))
    private void carpetFga$redirectNamedPearl(HitResult hitResult, CallbackInfo ci) {
        ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;
        if (!(pearl.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ServerPlayer target = NamedEnderPearlTeleport.findTarget(pearl, serverLevel);
        if (target != null) {
            pearl.setOwner(target);
        }
    }
}
//#endif
