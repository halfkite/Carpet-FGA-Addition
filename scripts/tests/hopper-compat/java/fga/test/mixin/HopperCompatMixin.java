package fga.test.mixin;

import fga.test.HopperCompatTest;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = HopperBlockEntity.class)
public class HopperCompatMixin {
    @WrapOperation(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack probe(Container source, Container target, ItemStack stack, Direction direction,
                                   Operation<ItemStack> original) {
        HopperCompatTest.calls++;
        HopperCompatTest.seenCount = stack.getCount();
        if (HopperCompatTest.oneAtATime) {
            if (target.getItem(0).isEmpty()) {
                target.setItem(0, stack.copyWithCount(1));
                return stack.copyWithCount(stack.getCount() - 1);
            }
            return stack;
        }
        return original.call(source, target, stack, direction);
    }
}
