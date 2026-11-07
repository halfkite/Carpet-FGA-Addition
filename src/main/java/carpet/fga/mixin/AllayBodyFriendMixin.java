package carpet.fga.mixin;

import carpet.fga.PossessionOwners;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.animal.allay.Allay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

@Mixin(Allay.class)
public abstract class AllayBodyFriendMixin {
    //#if MC >= 1.21.4
    //$$ @ModifyExpressionValue(method = {"mobInteract", "isLikedPlayer"},
    //#elseif MC >= 1.21.3
    //$$ @ModifyExpressionValue(method = {"mobInteract", "hurtServer"},
    //#else
    @ModifyExpressionValue(method = {"mobInteract", "hurt"},
    //#endif
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getUUID()Ljava/util/UUID;"))
    private UUID fga$likedIdentity(UUID actor) {
        return PossessionOwners.counterpartId(actor);
    }
}
