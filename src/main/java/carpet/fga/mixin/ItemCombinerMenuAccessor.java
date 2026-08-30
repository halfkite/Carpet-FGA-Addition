//#if MC == 1.21.1
package carpet.fga.mixin;

import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccessor {
    @Accessor("resultSlots")
    ResultContainer carpetFga$getResultSlots();
}
//#endif
