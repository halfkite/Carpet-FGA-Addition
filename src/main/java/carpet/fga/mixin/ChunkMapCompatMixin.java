//#if MC < 1.18
package carpet.fga.mixin;

import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkMap.class, priority = 900)
public abstract class ChunkMapCompatMixin {
    @Shadow
    @Final
    private Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

    @Inject(method = "getChunks", at = @At("HEAD"), cancellable = true)
    private void carpetFga$restoreChunkIteration(CallbackInfoReturnable<Iterable<ChunkHolder>> cir) {
        cir.setReturnValue(Iterables.unmodifiableIterable(visibleChunkMap.values()));
    }
}
//#endif
