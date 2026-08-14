//#if MC == 1.21.1
package carpet.fga.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapPlayerLoadDistanceAccessor {
    @Invoker("applyChunkTrackingView")
    void carpetFga$applyChunkTrackingView(ServerPlayer player, ChunkTrackingView view);

    @Invoker("updatePlayerStatus")
    void carpetFga$updatePlayerStatus(ServerPlayer player, boolean add);

    @Invoker("getDistanceManager")
    net.minecraft.server.level.DistanceManager carpetFga$getDistanceManager();
}
//#endif
