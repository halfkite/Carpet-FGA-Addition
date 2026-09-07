package fga.itemtest.mixin;

import fga.itemtest.ItemCompatTest;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.function.Predicate;

@Mixin(value = ItemEntity.class)
public class ItemCompatMixin {
    @Inject(method = "isMergable", at = @At("RETURN"), cancellable = true)
    private void veto(CallbackInfoReturnable<Boolean> result) {
        if (ItemCompatTest.veto) result.setReturnValue(false);
    }

    @ModifyExpressionValue(method = "isMergable",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
    private int eligibilityCap(int original) {
        return ItemCompatTest.cap > 0 ? ItemCompatTest.cap : original;
    }

    @ModifyExpressionValue(method = {"areMergable",
            "merge(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
    private static int cap(int original) {
        return ItemCompatTest.cap > 0 ? ItemCompatTest.cap : original;
    }

    @ModifyArg(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V",
            index = 2, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;merge(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;"))
    private static int argument(int original) {
        return ItemCompatTest.argument > 0 ? ItemCompatTest.argument : original;
    }

    @ModifyArg(method = "mergeWithNeighbours", index = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private AABB customBox(AABB original) {
        return ItemCompatTest.customBox != null ? ItemCompatTest.customBox : original;
    }

    @WrapOperation(method = "mergeWithNeighbours",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<ItemEntity> observeBox(Level level, Class<ItemEntity> type, AABB box,
            Predicate<? super ItemEntity> predicate, Operation<List<ItemEntity>> original) {
        ItemCompatTest.seenBox = box;
        return List.of();
    }
}
