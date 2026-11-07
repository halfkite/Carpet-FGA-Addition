package carpet.fga.mixin;

//#if MC >= 1.21.8
//$$ import net.minecraft.world.entity.EntityReference;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//#endif
import carpet.fga.PossessionOwners;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;

@Mixin(Animal.class)
public abstract class BreedingBodyActorMixin {
    @ModifyVariable(method = "setInLove", at = @At("HEAD"), argsOnly = true)
    private Player fga$storeFeeder(Player player) {
        return (Player) PossessionOwners.counterpart(player);
    }

    //#if MC >= 1.21.8
    //$$ @ModifyExpressionValue(method = "getLoveCause", at = @At(value = "FIELD",
    //$$         target = "Lnet/minecraft/world/entity/animal/Animal;loveCause:Lnet/minecraft/world/entity/EntityReference;"))
    //$$ private EntityReference<ServerPlayer> fga$feederReference(
    //$$         EntityReference<ServerPlayer> stored) {
    //$$     return PossessionOwners.bodyReference(stored);
    //$$ }
    //#else
    @ModifyArg(method = "getLoveCause", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getPlayerByUUID(Ljava/util/UUID;)Lnet/minecraft/world/entity/player/Player;"), index = 0)
    private UUID fga$feederId(UUID stored) {
        return PossessionOwners.counterpartId(stored);
    }
    //#endif
}
