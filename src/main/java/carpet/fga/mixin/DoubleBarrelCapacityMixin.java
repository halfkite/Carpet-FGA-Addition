//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.BarrelCapacityManager;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
//#if MC >= 1.21.6
//$$ import net.minecraft.world.level.storage.ValueInput;
//#else
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BarrelBlockEntity.class)
public abstract class DoubleBarrelCapacityMixin {
    @Shadow private NonNullList<ItemStack> items;
    @Unique private boolean carpetFga$readingItems;
    @Unique private boolean carpetFga$retainStoredOverflow;

//#if MC >= 1.21.6
//$$     @Inject(method = "loadAdditional", at = @At("HEAD"))
//$$     private void carpetFga$readAllSlots(ValueInput input, CallbackInfo ci) {
//#else
    @Inject(method = "loadAdditional", at = @At("HEAD"))
    private void carpetFga$readAllSlots(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
//#endif
        // Always read extended slots, including when the expansion was disabled after a restart.
        carpetFga$readingItems = true;
        if (items != null && items.size() < 54) {
            NonNullList<ItemStack> expanded = NonNullList.withSize(54, ItemStack.EMPTY);
            for (int i = 0; i < items.size(); i++) expanded.set(i, items.get(i));
            items = expanded;
        }
    }

//#if MC >= 1.21.6
//$$     @Inject(method = "loadAdditional", at = @At("TAIL"))
//$$     private void carpetFga$finishRead(ValueInput input, CallbackInfo ci) {
//#else
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void carpetFga$finishRead(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
//#endif
        carpetFga$readingItems = false;
        // Shrink empty overflow after reading, so disabled ordinary barrels keep the vanilla-sized list.
        ((BarrelBlockEntity) (Object) this).getContainerSize();
    }

    @Unique
    private boolean carpetFga$expanded() {
        if (BarrelCapacityManager.active() || carpetFga$retainStoredOverflow) return true;
        if (items == null) return false;
        for (int i = 27; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                // Menus and transfer APIs can retain slot references; keep this capacity for this load lifetime.
                carpetFga$retainStoredOverflow = true;
                BarrelCapacityManager.warnOverflow();
                return true;
            }
        }
        return false;
    }

    @Inject(method = "getContainerSize", at = @At("HEAD"), cancellable = true)
    private void carpetFga$size(CallbackInfoReturnable<Integer> cir) {
        if (!carpetFga$readingItems && !carpetFga$expanded()) return;
        if (items != null && items.size() < 54) {
            NonNullList<ItemStack> expanded = NonNullList.withSize(54, ItemStack.EMPTY);
            for (int i = 0; i < items.size(); i++) expanded.set(i, items.get(i));
            items = expanded;
        }
        cir.setReturnValue(54);
    }

    @Inject(method = "getContainerSize", at = @At("RETURN"))
    private void carpetFga$compactEmptyOverflow(CallbackInfoReturnable<Integer> cir) {
        if (carpetFga$readingItems || cir.getReturnValue() != 27 || items == null || items.size() <= 27
                || carpetFga$expanded()) return;
        NonNullList<ItemStack> compact = NonNullList.withSize(27, ItemStack.EMPTY);
        for (int i = 0; i < 27; i++) compact.set(i, items.get(i));
        items = compact;
    }

    @Inject(method = "createMenu", at = @At("HEAD"), cancellable = true)
    private void carpetFga$menu(int id, Inventory inventory, CallbackInfoReturnable<AbstractContainerMenu> cir) {
        if (carpetFga$expanded()) {
            cir.setReturnValue(ChestMenu.sixRows(id, inventory, (BarrelBlockEntity) (Object) this));
        }
    }
}
//#endif
