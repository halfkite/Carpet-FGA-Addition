//#if MC >= 1.20.5
package carpet.fga.inventoryadvancement;

import carpet.fga.FGASettings;

public final class InventoryAdvancementManager {
    public static final InventoryAdvancementRuntime RUNTIME = new InventoryAdvancementRuntime();

    private InventoryAdvancementManager() {}

    public static boolean enabled() {
        return "exact".equals(FGASettings.inventoryAdvancementOptimization);
    }

    public static OptimizationMode mode() {
        return enabled() ? OptimizationMode.EXACT : OptimizationMode.VANILLA;
    }

    public static double shadowVerifyRate() {
        return 0.001D;
    }

    public static int periodicFullScanTicks() {
        return 200;
    }

    public static boolean metricsEnabled() {
        return true;
    }

    public static boolean disableOnMismatch() {
        return true;
    }
}
//#endif
