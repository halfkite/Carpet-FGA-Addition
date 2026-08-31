//#if MC >= 1.20.1 && MC <= 1.21.5
package carpet.fga.mixin;

import carpet.fga.PlayerTpEndControlManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
//#if MC >= 1.21
import net.minecraft.world.level.block.EndGatewayBlock;
//#else
//$$ import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
//#endif
import net.minecraft.world.level.block.state.BlockState;
//#if MC == 1.21.5
//$$ import net.minecraft.world.level.portal.TeleportTransition;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        //#if MC >= 1.21
        EndGatewayBlock.class
        //#else
        //$$ TheEndGatewayBlockEntity.class
        //#endif
)
public abstract class EndGatewayPlayerTpControlMixin {
    //#if MC >= 1.21
    //#if MC == 1.21.5
    //$$ @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    //$$ private void carpetFga$controlPlayerGateway(BlockState state, Level level, BlockPos pos, Entity entity,
    //$$                                             TeleportTransition teleportTransition, CallbackInfo ci) {
    //$$     if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
    //$$     if (!PlayerTpEndControlManager.canTeleport(player, PlayerTpEndControlManager.PortalType.GATEWAY)) ci.cancel();
    //$$ }
    //#else
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void carpetFga$controlPlayerGateway(BlockState state, Level level, BlockPos pos, Entity entity,
                                                CallbackInfo ci) {
        carpetFga$controlPlayerGateway(level, entity, ci);
    }
    //#endif

    private static void carpetFga$controlPlayerGateway(Level level, Entity entity, CallbackInfo ci) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (!PlayerTpEndControlManager.canTeleport(player, PlayerTpEndControlManager.PortalType.GATEWAY)) ci.cancel();
    }
    //#else
    //$$ @Inject(method = "teleportEntity", at = @At("HEAD"), cancellable = true)
    //$$ private static void carpetFga$controlPlayerGateway(Level level, BlockPos pos, BlockState state,
    //$$                                                     Entity entity, TheEndGatewayBlockEntity gateway,
    //$$                                                     CallbackInfo ci) {
    //$$     if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
    //$$     if (!PlayerTpEndControlManager.canTeleport(player, PlayerTpEndControlManager.PortalType.GATEWAY)) ci.cancel();
    //$$ }
    //#endif
}
//#endif
