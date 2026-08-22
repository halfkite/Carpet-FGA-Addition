//#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.TerrainRegenerationManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerTerrainRegenerationMixin {
    @Inject(method="loadLevel",at=@At("HEAD"))
    private void fga$prepareTerrainTasks(CallbackInfo ci){TerrainRegenerationManager.beforeWorldLoad((MinecraftServer)(Object)this);}

    @Inject(method="loadLevel",at=@At("TAIL"))
    private void fga$executeTerrainTasks(CallbackInfo ci){TerrainRegenerationManager.onServerLoaded((MinecraftServer)(Object)this);}
}
//#endif
