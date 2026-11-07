package carpet.fga.mixin;

//#if MC <= 1.21.4
import carpet.fga.PossessionOwners;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

@Mixin(targets = "net.minecraft.world.entity.animal.Fox$FoxBreedGoal")
public abstract class FoxBodyBreedingMixin {
    @ModifyExpressionValue(method = "breed", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;getUUID()Ljava/util/UUID;"))
    private UUID fga$trustBreeder(UUID actor) {
        return PossessionOwners.counterpartId(actor);
    }
}
//#endif
