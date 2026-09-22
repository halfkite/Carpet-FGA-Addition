package carpet.fga;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullShulkerCraftingPolicyTest {
    @Test
    void disabledRuleRemainsDisabledOnAnIntegratedServer() {
        assertFalse(FullShulkerCraftingPolicy.ruleEnabled(false, "false"));
        assertTrue(FullShulkerCraftingPolicy.ruleEnabled(true, "false"),
                "a logical client keeps the permissive server-rule preview");
    }

    @Test
    void only64IsAuthoritativeOnTheLogicalServer() {
        assertTrue(FullShulkerCraftingPolicy.only64Mode(false, "only64"));
        assertFalse(FullShulkerCraftingPolicy.only64Mode(true, "only64"));
    }

    @Test
    void heterogeneousPistonRecipeDoesNotMultiplyByItsNineInputSlots() {
        long fullBoxItems = 27L * 64L;
        assertEquals(fullBoxItems,
                FullShulkerCraftingPolicy.totalOutputItems(fullBoxItems, 1));
    }

    @Test
    void recipeOutputCountStillScalesEachCraft() {
        long fullBoxItems = 27L * 64L;
        assertEquals(fullBoxItems * 4L,
                FullShulkerCraftingPolicy.totalOutputItems(fullBoxItems, 4));
    }
}
