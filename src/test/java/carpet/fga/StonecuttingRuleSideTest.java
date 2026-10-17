package carpet.fga;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class StonecuttingRuleSideTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void recipeDecisionsFollowServerRuleTransitions() {
        boolean previous = FGASettings.lightSourceStonecuttingRecipes;
        var light = holder("carpet-fga-addition:light_level_0_15_from_light_source_stonecutting");
        try {
            for (boolean enabled : new boolean[]{false, true, false}) {
                FGASettings.lightSourceStonecuttingRecipes = enabled;
                assertEquals(!enabled, DeepslateStonecuttingRecipes.isDisabledFgaRecipe(light));
                assertFalse(DeepslateStonecuttingRecipes.isDisabledFgaRecipe(holder("minecraft:stone_slab_from_stone_stonecutting")));
                assertFalse(DeepslateStonecuttingRecipes.isDisabledFgaRecipe(null));
            }
        } finally {
            FGASettings.lightSourceStonecuttingRecipes = previous;
        }
    }

    private static RecipeHolder<StonecutterRecipe> holder(String name) {
        ResourceLocation id = ResourceLocation.parse(name);
        //#if MC >= 1.21.3
        //$$ return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), null);
        //#else
        return new RecipeHolder<>(id, null);
        //#endif
    }
}
