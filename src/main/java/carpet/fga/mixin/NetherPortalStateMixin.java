//#if MC == 1.21.1
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
@Mixin(ServerLevel.class)
public abstract class NetherPortalStateMixin {
    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void carpetFga$trackPortalState(BlockPos position, BlockState oldState,
                                            BlockState newState, CallbackInfo ci) {
        NetherPortalLightManager.onBlockStateChange((ServerLevel) (Object) this,
                position, oldState, newState);
    }
}
//#endif
