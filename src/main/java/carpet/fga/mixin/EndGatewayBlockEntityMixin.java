//#if MC >= 1.21.1 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.EndGatewayRegenerationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TheEndGatewayBlockEntity.class)
public abstract class EndGatewayBlockEntityMixin {
    @Inject(method = "portalTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/TheEndGatewayBlockEntity;)V", at = @At("HEAD"))
    private static void carpetFga$recordLoadedGateway(Level level, BlockPos position, BlockState state,
                                                       TheEndGatewayBlockEntity gateway, CallbackInfo callback) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            EndGatewayRegenerationManager.record(serverLevel, position);
        }
    }
}
//#endif
