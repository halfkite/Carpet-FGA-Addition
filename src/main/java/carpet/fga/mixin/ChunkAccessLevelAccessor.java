//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkAccess.class)
public interface ChunkAccessLevelAccessor {
    @Accessor("levelHeightAccessor")
    LevelHeightAccessor carpetFga$levelHeightAccessor();
}
//#endif
