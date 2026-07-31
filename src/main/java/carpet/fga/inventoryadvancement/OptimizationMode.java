//#if MC >= 1.20.5
package carpet.fga.inventoryadvancement;

public enum OptimizationMode {
    VANILLA,
    EXACT,
    AGGRESSIVE;

    public static OptimizationMode parse(String value) {
        if (value != null) {
            switch (value) {
                case "VANILLA": return VANILLA;
                case "EXACT": return EXACT;
                case "AGGRESSIVE": return AGGRESSIVE;
                default: break;
            }
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException ignored) {
            return EXACT;
        }
    }
}
//#endif

