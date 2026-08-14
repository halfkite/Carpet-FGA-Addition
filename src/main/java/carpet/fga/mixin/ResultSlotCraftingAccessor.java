//#if MC >= 1.16.5 && MC <= 26.2
package carpet.fga.mixin;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ResultSlot.class)
public interface ResultSlotCraftingAccessor {
    @Accessor("craftSlots")
    CraftingContainer carpetFga$getCraftSlots();
}
//#endif
