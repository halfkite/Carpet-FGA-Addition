//#if MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Container.class)
public interface ContainerStackLimitMixin {
    @ModifyReturnValue(
            method =
                    //#if MC >= 1.20.5
                    "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I",
                    //#else
                    //$$ "getMaxStackSize()I",
                    //#endif
            at = @At("RETURN")
    )
    private int carpetFga$scopedStackLimit(int original
            //#if MC >= 1.20.5
            , ItemStack stack
            //#endif
    ) {
        //#if MC >= 1.20.5
        Container self = (Container) this;
        int configured = self instanceof Inventory
                ? FGASettings.effectiveInventoryStackLimit(stack)
                : FGASettings.effectiveContainerStackLimit(stack);
        return configured > stack.getMaxStackSize() ? configured : original;
        //#else
        //$$ Container self = (Container) this;
        //$$ int configured = self instanceof Inventory
        //$$         ? carpet.fga.DroppedItemStackLimitConfig.snapshot().inventoryLimit()
        //$$         : carpet.fga.DroppedItemStackLimitConfig.snapshot().containerLimit();
        //$$ return Math.max(original, configured);
        //#endif
    }
}
//#endif
