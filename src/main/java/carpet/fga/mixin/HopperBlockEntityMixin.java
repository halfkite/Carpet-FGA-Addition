//#if MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGACompat;
import carpet.fga.FGASettings;
//#if MC == 1.21.1 || MC == 26.2
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//#endif
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
//#if MC >= 1.21 && MC <= 26.2
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.ShulkerBoxBlock;
//#endif
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    //#if MC == 1.21.1 || MC == 26.2
    @WrapOperation(
            method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;")
    )
    private static ItemStack carpetFga$insertOneOversizedBatch(Container source, Container destination,
            ItemStack stack, Direction direction, Operation<ItemStack> original) {
        if (!FGASettings.isDroppedItemStackLimitEnabled()) {
            return original.call(source, destination, stack, direction);
        }
        int batchLimit = Math.min(stack.getMaxStackSize(), destination.getMaxStackSize(stack));
        if (batchLimit <= 0 || stack.getCount() <= batchLimit) {
            return original.call(source, destination, stack, direction);
        }
        // Keep one transfer attempt and its wrappers; never repeat another mod's per-call allowance.
        int deferred = stack.getCount() - batchLimit;
        ItemStack rejected = original.call(source, destination, FGACompat.copyWithCount(stack, batchLimit), direction);
        return FGACompat.copyWithCount(stack, deferred + rejected.getCount());
    }
    //#else
//$$     @Inject(
//$$             method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
//$$             at = @At("HEAD"),
//$$             cancellable = true
//$$     )
//$$     private static void carpetFga$insertOversizedItemEntity(Container destination, ItemEntity itemEntity,
//$$                                                              CallbackInfoReturnable<Boolean> cir) {
//$$         ItemStack original = itemEntity.getItem();
        //#if MC >= 1.21 && MC <= 26.2
//$$         // Carpet Org can temporarily make shulker boxes stackable. A hopper minecart
//$$         // must still consume at most one box per item-entity transfer attempt.
//$$         if (destination instanceof MinecartHopper
//$$                 && original.getCount() > 1
//$$                 && original.getItem() instanceof BlockItem blockItem
//$$                 && blockItem.getBlock() instanceof ShulkerBoxBlock) {
//$$             ItemStack one = FGACompat.copyWithCount(original, 1);
//$$             ItemStack rejected = HopperBlockEntity.addItem(null, destination, one, (Direction) null);
//$$             if (rejected.isEmpty()) {
//$$                 original.shrink(1);
//$$                 if (original.isEmpty()) {
//$$                     itemEntity.setItem(ItemStack.EMPTY);
//$$                     FGACompat.discard(itemEntity);
//$$                 } else {
//$$                     itemEntity.setItem(original);
//$$                 }
//$$                 cir.setReturnValue(true);
//$$             }
//$$             return;
//$$         }
        //#endif

//$$         if (!FGASettings.isDroppedItemStackLimitEnabled()) {
//$$             return;
//$$         }

//$$         int batchLimit = Math.min(original.getMaxStackSize(),
                //#if MC >= 1.20.5
//$$                 destination.getMaxStackSize(original)
                //#else
                //$$ destination.getMaxStackSize()
                //#endif
//$$         );
//$$         if (original.getCount() <= batchLimit) {
//$$             return;
//$$         }

//$$         ItemStack remaining = original.copy();
//$$         while (!remaining.isEmpty()) {
//$$             int batchSize = Math.min(remaining.getCount(), batchLimit);
//$$             ItemStack batch = FGACompat.copyWithCount(remaining, batchSize);
//$$             ItemStack rejected = HopperBlockEntity.addItem(null, destination, batch, (Direction) null);
//$$             int inserted = batchSize - rejected.getCount();
//$$             if (inserted <= 0) {
//$$                 break;
//$$             }
//$$             remaining.shrink(inserted);
//$$             if (inserted < batchSize) {
//$$                 break;
//$$             }
//$$         }

//$$         if (remaining.isEmpty()) {
//$$             itemEntity.setItem(ItemStack.EMPTY);
//$$             FGACompat.discard(itemEntity);
//$$             cir.setReturnValue(true);
//$$         } else {
//$$             itemEntity.setItem(remaining);
//$$             cir.setReturnValue(false);
//$$         }
//$$     }
    //#endif
}
//#endif
