//#if MC >= 1.20.5
package carpet.fga.mixin;

import carpet.fga.inventoryadvancement.InventoryAdvancementManager;
//#if MC >= 26.2
//$$ import net.minecraft.advancements.triggers.InventoryChangeTrigger;
//#else
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
//#endif
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InventoryChangeTrigger.class, priority = 900)
abstract class InventoryChangeTriggerMixin {
    @Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void invadvopt$replaceTrigger(ServerPlayer player, Inventory inventory, ItemStack stack, CallbackInfo callback) {
        if (InventoryAdvancementManager.RUNTIME.handleTrigger((InventoryChangeTrigger)(Object)this, player, inventory, stack)) {
            callback.cancel();
        }
    }

    @Inject(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"), require = 0)
    private void invadvopt$finishVanillaTrigger(ServerPlayer player, Inventory inventory, ItemStack stack, CallbackInfo callback) {
        InventoryAdvancementManager.RUNTIME.onVanillaTriggerReturn();
    }
}
//#endif

