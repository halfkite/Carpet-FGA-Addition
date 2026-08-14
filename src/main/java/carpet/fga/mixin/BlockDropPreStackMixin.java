//#if MC >= 1.20.5 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.DeathDropPreStackManager;
import carpet.fga.FGASettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures configured item drops from all vanilla Block.dropResources overloads. */
@Mixin(Block.class)
public abstract class BlockDropPreStackMixin {
    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"))
    private static void carpetFga$beginDropResources(BlockState state, Level level, BlockPos position,
                                                      CallbackInfo callback) {
        carpetFga$begin(level, position, state);
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", at = @At("RETURN"))
    private static void carpetFga$finishDropResources(BlockState state, Level level, BlockPos position,
                                                       CallbackInfo callback) {
        carpetFga$finish(level, position);
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V", at = @At("HEAD"))
    private static void carpetFga$beginDropResourcesWithBlockEntity(BlockState state, LevelAccessor level,
                                                                      BlockPos position, BlockEntity blockEntity,
                                                                      CallbackInfo callback) {
        carpetFga$begin(level, position, state);
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V", at = @At("RETURN"))
    private static void carpetFga$finishDropResourcesWithBlockEntity(BlockState state, LevelAccessor level,
                                                                       BlockPos position, BlockEntity blockEntity,
                                                                       CallbackInfo callback) {
        carpetFga$finish(level, position);
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private static void carpetFga$beginDropResourcesWithBreaker(BlockState state, Level level, BlockPos position,
                                                                  BlockEntity blockEntity, Entity breaker, ItemStack tool,
                                                                  CallbackInfo callback) {
        carpetFga$begin(level, position, state);
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private static void carpetFga$finishDropResourcesWithBreaker(BlockState state, Level level, BlockPos position,
                                                                   BlockEntity blockEntity, Entity breaker, ItemStack tool,
                                                                   CallbackInfo callback) {
        carpetFga$finish(level, position);
    }

    private static void carpetFga$begin(Level level, BlockPos position, BlockState state) {
        if (!(level instanceof ServerLevel)
                || !FGASettings.preStackDroppedItems
                || !DeathDropPreStackManager.blockPreStackConfigured()) {
            return;
        }
        DeathDropPreStackManager.beginBlock((ServerLevel) level, position, state);
    }

    private static void carpetFga$begin(LevelAccessor level, BlockPos position, BlockState state) {
        if (!(level instanceof ServerLevel)
                || !FGASettings.preStackDroppedItems
                || !DeathDropPreStackManager.blockPreStackConfigured()) {
            return;
        }
        DeathDropPreStackManager.beginBlock((ServerLevel) level, position, state);
    }

    private static void carpetFga$finish(Level level, BlockPos position) {
        if (!(level instanceof ServerLevel)
                || !FGASettings.preStackDroppedItems
                || !DeathDropPreStackManager.blockPreStackConfigured()) {
            return;
        }
        DeathDropPreStackManager.finishBlock((ServerLevel) level, position, true);
    }

    private static void carpetFga$finish(LevelAccessor level, BlockPos position) {
        if (!(level instanceof ServerLevel)
                || !FGASettings.preStackDroppedItems
                || !DeathDropPreStackManager.blockPreStackConfigured()) {
            return;
        }
        DeathDropPreStackManager.finishBlock((ServerLevel) level, position, true);
    }
}
//#endif
