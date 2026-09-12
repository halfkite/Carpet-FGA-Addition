//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.NetherPortalLightManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records portal creation and removal only while the portal-light rule is active. */
//#if MC <= 1.21.4
@Mixin(ServerLevel.class)
//#else
//$$ @Mixin(net.minecraft.world.level.Level.class)
//#endif
public abstract class NetherPortalStateMixin {
    //#if MC <= 1.21.4
    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void carpetFga$trackPortalState(BlockPos position, BlockState oldState,
                                            BlockState newState, CallbackInfo ci) {
        NetherPortalLightManager.onBlockStateChange((ServerLevel) (Object) this,
                position, oldState, newState);
    }
    //#else
    //$$ @Inject(method = "updatePOIOnBlockStateChange", at = @At("HEAD"))
    //$$ private void carpetFga$trackPortalState(BlockPos position, BlockState oldState,
    //$$                                         BlockState newState, CallbackInfo ci) {
    //$$     if ((Object) this instanceof ServerLevel level) {
    //$$         NetherPortalLightManager.onBlockStateChange(level, position, oldState, newState);
    //$$     }
    //$$ }
    //#endif
}
//#endif
