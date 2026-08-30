package carpet.fga;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EnchantmentLevelRules {
    public static final int MAX_STORED_LEVEL = 255;
    public static final int MAX_INCREASE = 254;
    private static final Pattern INCREASE_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern LEGACY_INCREASE_PATTERN = Pattern.compile("\\[\\s*(\\d+)\\s*]");

    private EnchantmentLevelRules() {
    }

    public static String normalizeIncrease(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.equalsIgnoreCase("false")) return "false";
        if (value.equals("[]")) return "0";

        Matcher matcher = INCREASE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            matcher = LEGACY_INCREASE_PATTERN.matcher(value);
            if (!matcher.matches()) return null;
        }
        try {
            int increase = Integer.parseInt(matcher.group(1));
            return increase <= MAX_INCREASE ? Integer.toString(increase) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static int parseIncrease(String value) {
        String normalized = normalizeIncrease(value);
        if (normalized == null || normalized.equals("false")) return 0;
        return Integer.parseInt(normalized);
    }

    public static int increaseLimit(int vanillaLimit, int increase) {
        return saturatedAdd(vanillaLimit, increase);
    }

    public static int addLevels(int firstLevel, int secondLevel) {
        return saturatedAdd(firstLevel, secondLevel);
    }

    public static boolean rejectsAddition(int firstLevel, int secondLevel, int maximumLevel) {
        return firstLevel > 0 && (firstLevel >= maximumLevel
                || secondLevel >= maximumLevel
                || (long) firstLevel + secondLevel > maximumLevel);
    }

    private static int saturatedAdd(int first, int second) {
        long sum = (long) first + second;
        return (int) Math.max(0L, Math.min(MAX_STORED_LEVEL, sum));
    }
}
