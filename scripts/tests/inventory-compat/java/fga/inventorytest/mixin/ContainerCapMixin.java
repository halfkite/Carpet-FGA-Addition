package fga.inventorytest.mixin;

import fga.inventorytest.InventoryCompatTest;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Container.class)
public interface ContainerCapMixin {
    @ModifyReturnValue(method = "getMaxStackSize()I", at = @At("RETURN"))
    private int compatCap(int original) {
        return InventoryCompatTest.cap > 0 ? InventoryCompatTest.cap : original;
    }
}
