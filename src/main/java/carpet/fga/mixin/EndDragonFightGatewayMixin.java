//#if MC >= 1.16.5 && MC < 26.0
package carpet.fga.mixin;

import carpet.fga.EndGatewayRegenerationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndDragonFight.class)
public abstract class EndDragonFightGatewayMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "spawnNewGateway(Lnet/minecraft/core/BlockPos;)V", at = @At("RETURN"))
    private void carpetFga$recordGeneratedGateway(BlockPos position, CallbackInfo callback) {
        EndGatewayRegenerationManager.record(level, position);
    }
}
//#endif
