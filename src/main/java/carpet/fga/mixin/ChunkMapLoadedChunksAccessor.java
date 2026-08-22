//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
//#if MC >= 1.21.10
//$$ import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
//#endif
import org.spongepowered.asm.mixin.Mixin;
//#if MC >= 1.21.10
//$$ import org.spongepowered.asm.mixin.gen.Accessor;
//#else
import org.spongepowered.asm.mixin.gen.Invoker;
//#endif

@Mixin(ChunkMap.class)
public interface ChunkMapLoadedChunksAccessor {
    //#if MC >= 1.21.10
    //$$ @Accessor("visibleChunkMap")
    //$$ Long2ObjectLinkedOpenHashMap<ChunkHolder> carpetFga$getVisibleChunkMap();
    //#else
    @Invoker("getChunks")
    Iterable<ChunkHolder> carpetFga$getLoadedChunks();
    //#endif
}
//#endif
