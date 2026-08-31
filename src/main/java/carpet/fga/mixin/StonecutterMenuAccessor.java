//#if MC >= 1.21
package carpet.fga.mixin;

import net.minecraft.world.inventory.StonecutterMenu;
//#if MC == 1.21.1
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import java.util.List;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(StonecutterMenu.class)
public interface StonecutterMenuAccessor {
    //#if MC == 1.21.1
    @org.spongepowered.asm.mixin.gen.Accessor("recipes")
    List<RecipeHolder<StonecutterRecipe>> carpetFga$getRecipes();

    @Invoker("setupResultSlot")
    void carpetFga$invokeSetupResultSlot();
    //#else
    //$$ @Invoker("setupResultSlot")
    //$$ void carpetFga$invokeSetupResultSlot(int selectedIndex);
    //#endif
}
//#endif
