//#if MC >= 1.20.1 && MC <= 1.21.5
package carpet.fga.mixin;

import carpet.fga.PlayerTpEndControlManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
//#if MC == 1.21.5
import net.minecraft.world.level.portal.TeleportTransition;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndPortalBlock.class)
public abstract class EndPortalPlayerTpControlMixin {
    //#if MC == 1.21.5
    //$$ @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    //$$ private void carpetFga$controlPlayerEndPortal(BlockState state, Level level, BlockPos pos, Entity entity,
    //$$                                                 TeleportTransition teleportTransition, CallbackInfo ci) {
    //$$     if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
    //$$     PlayerTpEndControlManager.PortalType type = level.dimension() == Level.END
    //$$             ? PlayerTpEndControlManager.PortalType.EXIT : PlayerTpEndControlManager.PortalType.ENTER;
    //$$     if (!PlayerTpEndControlManager.canTeleport(player, type)) ci.cancel();
    //$$ }
    //#else
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void carpetFga$controlPlayerEndPortal(BlockState state, Level level, BlockPos pos, Entity entity,
                                                  CallbackInfo ci) {
        carpetFga$controlPlayerEndPortal(level, entity, ci);
    }
    //#endif

    private static void carpetFga$controlPlayerEndPortal(Level level, Entity entity, CallbackInfo ci) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        PlayerTpEndControlManager.PortalType type = level.dimension() == Level.END
                ? PlayerTpEndControlManager.PortalType.EXIT : PlayerTpEndControlManager.PortalType.ENTER;
        if (!PlayerTpEndControlManager.canTeleport(player, type)) ci.cancel();
    }
    //#endif
}
//#endif
