//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.PlayerLoadDistanceManager;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class ChunkMapPlayerLoadDistanceMixin {
    @Inject(method = "move", at = @At("TAIL"))
    private void carpetFga$refreshPlayerView(ServerPlayer player, CallbackInfo callback) {
        carpet.fga.PlayerLoadDistanceCompat.reapply(player);
    }
}
//#endif
