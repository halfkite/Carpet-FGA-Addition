//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the lighting source level to the portal-light engine mixin. */
@Mixin(LightEngine.class)
public interface LightEngineAccessor {
    @Accessor("chunkSource")
    LightChunkGetter carpetFga$getChunkSource();
}
//#endif
