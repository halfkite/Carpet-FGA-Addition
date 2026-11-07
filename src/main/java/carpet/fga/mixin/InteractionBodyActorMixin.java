package carpet.fga.mixin;

import carpet.fga.PossessionOwners;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Interaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.UUID;

@Mixin(Interaction.class)
public abstract class InteractionBodyActorMixin {
    @ModifyExpressionValue(method = {"interact", "skipAttackInteraction"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getUUID()Ljava/util/UUID;"))
    private UUID fga$recordActor(UUID actor) {
        return PossessionOwners.counterpartId(actor);
    }

    @ModifyArg(method = {"getLastAttacker", "getTarget"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getPlayerByUUID(Ljava/util/UUID;)Lnet/minecraft/world/entity/player/Player;"), index = 0)
    private UUID fga$resolveActor(UUID stored) {
        return PossessionOwners.counterpartId(stored);
    }
}
