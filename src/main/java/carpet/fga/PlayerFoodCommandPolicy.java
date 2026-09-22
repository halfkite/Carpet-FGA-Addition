package carpet.fga;

/** Pure permission policy for the /food clear command. */
public final class PlayerFoodCommandPolicy {
    private PlayerFoodCommandPolicy() {
    }

    public static boolean allowsRoot(String ruleValue, boolean sourceIsPlayer, int permissionLevel) {
        String value = normalize(ruleValue);
        if ("false".equals(value)) return false;
        if ("true".equals(value)) return true;
        if ("onlyself".equals(value)) return sourceIsPlayer || permissionLevel >= 2;
        return permissionLevel >= requiredLevel(value);
    }

    public static boolean allowsTargets(String ruleValue, int permissionLevel) {
        String value = normalize(ruleValue);
        if ("onlyself".equals(value)) return permissionLevel >= 2;
        return allowsRoot(value, false, permissionLevel);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static int requiredLevel(String value) {
        if ("ops".equals(value)) return 2;
        try {
            int level = Integer.parseInt(value);
            return level >= 0 && level <= 4 ? level : Integer.MAX_VALUE;
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }
}
