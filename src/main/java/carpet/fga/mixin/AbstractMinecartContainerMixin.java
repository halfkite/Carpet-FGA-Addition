//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.DeathDropPreStackManager;
import carpet.fga.FGACompat;
import carpet.fga.FGASettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures inventory and vehicle drops from container minecarts. */
@Mixin(AbstractMinecartContainer.class)
public abstract class AbstractMinecartContainerMixin {
    //#if MC >= 1.21.3
    //$$ @Inject(method = "destroy(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    //$$ private void carpetFga$beginContainerMinecartDrop(ServerLevel level, DamageSource source, CallbackInfo callback) {
    //$$     AbstractMinecartContainer minecart = (AbstractMinecartContainer) (Object) this;
    //$$     if (FGASettings.shouldPreStackDeathDrops(minecart)) {
    //$$         DeathDropPreStackManager.begin(minecart, level);
    //$$     }
    //$$ }

    //$$ @Inject(method = "destroy(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("RETURN"))
    //$$ private void carpetFga$finishContainerMinecartDrop(ServerLevel level, DamageSource source, CallbackInfo callback) {
    //$$     AbstractMinecartContainer minecart = (AbstractMinecartContainer) (Object) this;
    //$$     if (FGASettings.shouldPreStackDeathDrops(minecart)) {
    //$$         DeathDropPreStackManager.finish(minecart, true);
    //$$     }
    //$$ }
    //#else
    @Inject(method = "destroy(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void carpetFga$beginContainerMinecartDrop(DamageSource source, CallbackInfo callback) {
        AbstractMinecartContainer minecart = (AbstractMinecartContainer) (Object) this;
        if (FGACompat.level(minecart) instanceof ServerLevel level
                && FGASettings.shouldPreStackDeathDrops(minecart)) {
            DeathDropPreStackManager.begin(minecart, level);
        }
    }

    @Inject(method = "destroy(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("RETURN"))
    private void carpetFga$finishContainerMinecartDrop(DamageSource source, CallbackInfo callback) {
        AbstractMinecartContainer minecart = (AbstractMinecartContainer) (Object) this;
        if (FGACompat.level(minecart) instanceof ServerLevel level
                && FGASettings.shouldPreStackDeathDrops(minecart)) {
            DeathDropPreStackManager.finish(minecart, true);
        }
    }
    //#endif
}
//#endif
