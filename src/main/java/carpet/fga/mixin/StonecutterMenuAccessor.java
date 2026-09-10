//#if MC == 1.21.1
package carpet.fga.mixin;

import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StonecutterMenu.class)
public interface StonecutterMenuAccessor {
    @org.spongepowered.asm.mixin.gen.Accessor("recipes")
    List<RecipeHolder<StonecutterRecipe>> carpetFga$getRecipes();
}
//#endif
