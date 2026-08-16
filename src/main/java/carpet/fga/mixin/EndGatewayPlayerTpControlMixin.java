//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.PlayerTpEndControlManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndGatewayBlock.class)
public abstract class EndGatewayPlayerTpControlMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void carpetFga$controlPlayerGateway(BlockState state, Level level, BlockPos pos, Entity entity,
                                                CallbackInfo ci) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (!PlayerTpEndControlManager.canTeleport(player, PlayerTpEndControlManager.PortalType.GATEWAY)) ci.cancel();
    }
}
//#endif
