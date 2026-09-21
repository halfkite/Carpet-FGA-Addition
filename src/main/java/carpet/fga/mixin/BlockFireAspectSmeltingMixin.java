//#if MC >= 1.21 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.FireAspectToolManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
//#if MC >= 26.1
//$$ import net.minecraft.world.item.ItemInstance;
//#endif
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Block.class)
public abstract class BlockFireAspectSmeltingMixin {
    @Inject(
            method =
                    //#if MC >= 26.1
                    //$$ "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;",
                    //#else
                    "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
                    //#endif
            at = @At("RETURN"),
            cancellable = true
    )
    private static void carpetFga$smeltToolDrops(BlockState state, ServerLevel level, BlockPos position,
                                                  BlockEntity blockEntity, Entity breaker,
                                                  //#if MC >= 26.1
                                                  //$$ ItemInstance tool,
                                                  //#else
                                                  ItemStack tool,
                                                  //#endif
                                                  CallbackInfoReturnable<List<ItemStack>> callback) {
        callback.setReturnValue(FireAspectToolManager.smeltDrops(
                level, breaker,
                //#if MC >= 26.1
                //$$ (ItemStack) tool,
                //#else
                tool,
                //#endif
                callback.getReturnValue()));
    }
}
//#endif
