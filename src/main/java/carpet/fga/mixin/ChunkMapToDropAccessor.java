//#if MC >= 1.20.1 && MC <= 1.21.1
package carpet.fga.mixin;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the chunk map's drop queue so terrain regeneration can unload a chunk on purpose instead of
 * waiting for the player to walk away. Vanilla then reloads it from disk, which regenerates it because
 * the stored data was cleared first.
 */
@Mixin(ChunkMap.class)
public interface ChunkMapToDropAccessor {
    @Accessor("toDrop")
    LongSet carpetFga$getToDrop();
}
//#endif
