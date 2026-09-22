package carpet.fga;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerFoodCommandPolicyTest {
    @Test
    void falseDisablesRootAndTargets() {
        assertFalse(PlayerFoodCommandPolicy.allowsRoot("false", true, 4));
        assertFalse(PlayerFoodCommandPolicy.allowsTargets("false", 4));
    }

    @Test
    void trueAllowsEveryoneToTarget() {
        assertTrue(PlayerFoodCommandPolicy.allowsRoot("true", true, 0));
        assertTrue(PlayerFoodCommandPolicy.allowsTargets("true", 0));
    }

    @Test
    void onlySelfAllowsNonOpsOnlyTheirOwnCommand() {
        assertTrue(PlayerFoodCommandPolicy.allowsRoot("onlyself", true, 0));
        assertFalse(PlayerFoodCommandPolicy.allowsTargets("onlyself", 0));
        assertTrue(PlayerFoodCommandPolicy.allowsTargets("onlyself", 2));
    }

    @Test
    void opsAndNumericLevelsUsePermissionThresholds() {
        assertFalse(PlayerFoodCommandPolicy.allowsRoot("ops", true, 1));
        assertTrue(PlayerFoodCommandPolicy.allowsRoot("ops", true, 2));
        assertFalse(PlayerFoodCommandPolicy.allowsTargets("3", 2));
        assertTrue(PlayerFoodCommandPolicy.allowsTargets("3", 3));
    }
}
