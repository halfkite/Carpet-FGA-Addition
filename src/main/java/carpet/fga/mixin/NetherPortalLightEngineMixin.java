//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.NetherPortalLightManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiConsumer;

/** Removes portal emission from both queued block checks and chunk light-source scans. */
@Mixin(BlockLightEngine.class)
public abstract class NetherPortalLightEngineMixin {
    @Inject(method = "getEmission", at = @At("HEAD"), cancellable = true)
    private void carpetFga$disablePortalEmission(long packedPosition, BlockState state,
                                                  CallbackInfoReturnable<Integer> cir) {
        LightChunkGetter chunkSource = ((LightEngineAccessor) (Object) this).carpetFga$getChunkSource();
        if (chunkSource.getLevel() instanceof ServerLevel level
                && NetherPortalLightManager.shouldSuppress(level, packedPosition, state)) {
            cir.setReturnValue(0);
        }
    }

    @ModifyArg(
            method = "propagateLightSources",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/LightChunk;findBlockLightSources(Ljava/util/function/BiConsumer;)V"),
            index = 0)
    private BiConsumer<BlockPos, BlockState> carpetFga$filterPortalSources(
            BiConsumer<BlockPos, BlockState> original) {
        LightChunkGetter chunkSource = ((LightEngineAccessor) (Object) this).carpetFga$getChunkSource();
        if (!(chunkSource.getLevel() instanceof ServerLevel level)) return original;
        return (position, state) -> {
            if (!NetherPortalLightManager.shouldSuppress(level, position.asLong(), state)) {
                original.accept(position, state);
            }
        };
    }
}
//#endif
