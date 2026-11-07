package carpet.fga.mixin;

import carpet.fga.PossessionOwners;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LightningBolt.class)
public abstract class LightningBodyCauseMixin {
    @ModifyVariable(method = "setCause", at = @At("HEAD"), argsOnly = true)
    private ServerPlayer fga$storeCause(ServerPlayer player) {
        return (ServerPlayer) PossessionOwners.counterpart(player);
    }

    @ModifyExpressionValue(method = {"getCause", "tick"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/LightningBolt;cause:Lnet/minecraft/server/level/ServerPlayer;"))
    private ServerPlayer fga$causingBody(ServerPlayer stored) {
        return (ServerPlayer) PossessionOwners.counterpart(stored);
    }
}
