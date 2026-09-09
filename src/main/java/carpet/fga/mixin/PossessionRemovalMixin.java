package carpet.fga.mixin;

//#if MC == 1.21.1
import carpet.fga.PlayerPossessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class PossessionRemovalMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void fga$releaseOnRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        if (reason != Entity.RemovalReason.CHANGED_DIMENSION
                && (Object) this instanceof ServerPlayer player) PlayerPossessionManager.endFor(player);
    }
}
//#endif
