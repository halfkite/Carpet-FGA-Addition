//#if MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGACompat;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Inject(
            method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void carpetFga$insertOversizedItemEntity(Container destination, ItemEntity itemEntity,
                                                             CallbackInfoReturnable<Boolean> cir) {
        ItemStack original = itemEntity.getItem();
        int batchLimit = Math.min(original.getMaxStackSize(),
                //#if MC >= 1.20.5
                destination.getMaxStackSize(original)
                //#else
                //$$ destination.getMaxStackSize()
                //#endif
        );
        if (original.getCount() <= batchLimit) {
            return;
        }

        ItemStack remaining = original.copy();
        while (!remaining.isEmpty()) {
            int batchSize = Math.min(remaining.getCount(), batchLimit);
            ItemStack batch = FGACompat.copyWithCount(remaining, batchSize);
            ItemStack rejected = HopperBlockEntity.addItem(null, destination, batch, (Direction) null);
            int inserted = batchSize - rejected.getCount();
            if (inserted <= 0) {
                break;
            }
            remaining.shrink(inserted);
            if (inserted < batchSize) {
                break;
            }
        }

        if (remaining.isEmpty()) {
            itemEntity.setItem(ItemStack.EMPTY);
            FGACompat.discard(itemEntity);
            cir.setReturnValue(true);
        } else {
            itemEntity.setItem(remaining);
            cir.setReturnValue(false);
        }
    }
}
//#endif
