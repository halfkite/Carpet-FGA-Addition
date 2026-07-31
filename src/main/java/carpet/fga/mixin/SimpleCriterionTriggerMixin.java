//#if MC >= 1.20.5 && MC < 26.2
package carpet.fga.mixin;

import carpet.fga.inventoryadvancement.InventoryAdvancementManager;
//#if MC >= 26.2
//$$ import net.minecraft.advancements.triggers.CriterionTrigger;
//#else
import net.minecraft.advancements.CriterionTrigger;
//#endif
//#if MC >= 26.2
//$$ import net.minecraft.advancements.triggers.InventoryChangeTrigger;
//#else
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
//#endif
//#if MC >= 26.2
//$$ import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
//#else
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
//#endif
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SimpleCriterionTrigger.class, priority = 900)
abstract class SimpleCriterionTriggerMixin {
    @Inject(method = "addPlayerListener(Lnet/minecraft/server/PlayerAdvancements;Lnet/minecraft/advancements/CriterionTrigger$Listener;)V", at = @At("TAIL"), require = 0)
    private void invadvopt$onListenerAdded(PlayerAdvancements advancements, CriterionTrigger.Listener<?> listener, CallbackInfo callback) {
        if ((Object)this instanceof InventoryChangeTrigger) {
            InventoryAdvancementManager.RUNTIME.addListener((InventoryChangeTrigger)(Object)this, advancements, listener);
        }
    }

    @Inject(method = "removePlayerListener(Lnet/minecraft/server/PlayerAdvancements;Lnet/minecraft/advancements/CriterionTrigger$Listener;)V", at = @At("TAIL"), require = 0)
    private void invadvopt$onListenerRemoved(PlayerAdvancements advancements, CriterionTrigger.Listener<?> listener, CallbackInfo callback) {
        if ((Object)this instanceof InventoryChangeTrigger) {
            InventoryAdvancementManager.RUNTIME.removeListener((InventoryChangeTrigger)(Object)this, advancements, listener);
        }
    }

    @Inject(method = "removePlayerListeners(Lnet/minecraft/server/PlayerAdvancements;)V", at = @At("TAIL"), require = 0)
    private void invadvopt$onAllListenersRemoved(PlayerAdvancements advancements, CallbackInfo callback) {
        if ((Object)this instanceof InventoryChangeTrigger) {
            InventoryAdvancementManager.RUNTIME.removeListeners(advancements);
        }
    }
}
//#endif

