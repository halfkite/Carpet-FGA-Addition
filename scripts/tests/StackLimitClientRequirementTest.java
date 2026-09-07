package carpet.fga;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/** Runs against the actual compiled configuration class, without a world or client. */
public final class StackLimitClientRequirementTest {
    public static void main(String[] args) throws Exception {
        Field state = DroppedItemStackLimitConfig.class.getDeclaredField("state");
        Field failed = DroppedItemStackLimitConfig.class.getDeclaredField("loadFailed");
        state.setAccessible(true);
        failed.setAccessible(true);
        Object savedState = state.get(null);
        boolean savedFailed = failed.getBoolean(null);
        String savedRule = FGASettings.droppedItemStackLimit;
        int checks = 0;
        try {
            for (int[] limits : new int[][] {{0, 0}, {1000, 0}, {0, 1000}, {1000, 1000}}) {
                Object configured = new DroppedItemStackLimitConfig.State(
                        DroppedItemStackLimitConfig.Mode.ALL, 1000, 1000,
                        limits[0], limits[1], Set.of(), Map.of());
                state.set(null, configured);
                for (boolean loadFailed : new boolean[] {false, true}) {
                    failed.setBoolean(null, loadFailed);
                    for (String rule : new String[] {"false", "true", "ops", "0", "1", "2", "3", "4", "false", "true"}) {
                        FGASettings.droppedItemStackLimit = rule;
                        boolean expected = !rule.equals("false") && !loadFailed
                                && (limits[0] != 0 || limits[1] != 0);
                        if (DroppedItemStackLimitConfig.requiresModdedClient() != expected) {
                            throw new AssertionError("rule=" + rule + ", inventory=" + limits[0]
                                    + ", container=" + limits[1] + ", loadFailed=" + loadFailed);
                        }
                        if (state.get(null) != configured) {
                            throw new AssertionError("Client requirement check changed the saved configuration");
                        }
                        checks++;
                    }
                }
            }
        } finally {
            FGASettings.droppedItemStackLimit = savedRule;
            state.set(null, savedState);
            failed.setBoolean(null, savedFailed);
        }
        System.out.println("Stack client requirement: " + checks + " checks passed");
    }
}
