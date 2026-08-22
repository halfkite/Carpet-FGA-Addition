//#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FullShulkerBoxCraftingManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
//#if MC >= 26.1.2
//$$ import net.minecraft.world.inventory.ContainerInput;
//#else
import net.minecraft.world.inventory.ClickType;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuFullShulkerMixin {
    @WrapOperation(
            method = "clicked",
            at = @At(
                    value = "INVOKE",
                    target =
                            //#if MC >= 26.1.2
                            //$$ "Lnet/minecraft/world/inventory/AbstractContainerMenu;doClick(IILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V"
                            //#else
                            "Lnet/minecraft/world/inventory/AbstractContainerMenu;doClick(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V"
                            //#endif
            )
    )
    private void carpetFga$trackQuickResultClick(AbstractContainerMenu menu, int slotId, int button,
                                                  //#if MC >= 26.1.2
                                                  //$$ ContainerInput clickType, Player player,
                                                  //#else
                                                  ClickType clickType, Player player,
                                                  //#endif
                                                  Operation<Void> original) {
        boolean restock =
                //#if MC >= 26.1.2
                //$$ clickType == ContainerInput.QUICK_MOVE || clickType == ContainerInput.THROW;
                //#else
                clickType == ClickType.QUICK_MOVE || clickType == ClickType.THROW;
                //#endif
        if (!restock) {
            original.call(menu, slotId, button, clickType, player);
            return;
        }

        FullShulkerBoxCraftingManager.beginQuickResultClick(player);
        try {
            original.call(menu, slotId, button, clickType, player);
        } finally {
            FullShulkerBoxCraftingManager.endQuickResultClick();
        }
    }
}
//#endif
