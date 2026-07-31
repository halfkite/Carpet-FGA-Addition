//#if MC >= 1.20.5
package carpet.fga.mixin;

import carpet.fga.inventoryadvancement.InventoryAdvancementManager;
//#if MC >= 26.2
//$$ import net.minecraft.advancements.predicates.ItemPredicate;
//#else
import net.minecraft.advancements.critereon.ItemPredicate;
//#endif
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemPredicate.class, priority = 900)
abstract class ItemPredicateMixin {
    @Inject(method = "test(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), require = 0)
    private void invadvopt$countPredicateTest(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        InventoryAdvancementManager.RUNTIME.onItemPredicateTest();
    }
}
//#endif

