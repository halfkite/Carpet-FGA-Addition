package carpet.fga.mixin;

import carpet.fga.PossessionOwners;
import net.minecraft.world.entity.animal.allay.AllayAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.UUID;

@Mixin(AllayAi.class)
public abstract class AllayBodyDeliveryMixin {
    @ModifyArg(method = "getLikedPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getEntity(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;"), index = 0)
    private static UUID fga$deliveryBody(UUID stored) {
        return PossessionOwners.counterpartId(stored);
    }
}
