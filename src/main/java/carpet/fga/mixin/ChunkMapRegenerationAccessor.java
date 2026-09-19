//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapRegenerationAccessor {
    @Invoker("createEmptyChunk")
    ChunkAccess carpetFga$createEmptyChunk(ChunkPos pos);
}
//#endif
