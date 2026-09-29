package carpet.fga;

/**
 * Integer XP arithmetic for fixed-cost experience levels.
 *
 * Regression recipe: FlatExperienceMathTest and
 * scripts/powershell/flat-experience-smoke-26.3.ps1. Test false/29-30/0-1,
 * crossing level 30 in both directions, negative awards, Integer.MAX_VALUE
 * awards, and ORG all/half/points/level/upgrade/upgradeto. Check point
 * conservation, residual progress, failure without mutation and latency.
 */
public final class FlatExperienceMath {
    public enum Mode { AFTER_THIRTY, ALL }
    public record State(int level, int points) {}

    private FlatExperienceMath() {}

    public static int cost(int level, Mode mode) {
        if (mode == Mode.ALL) return 7;
        if (level >= 30) return 107;
        return level >= 15 ? 5 * level - 38 : 2 * level + 7;
    }

    public static long atLevel(int level, Mode mode) {
        if (level < 0) throw new IllegalArgumentException("Negative experience level");
        if (mode == Mode.ALL) return 7L * level;
        if (level >= 30) return 1395L + 107L * (level - 30);
        return level <= 16 ? (long) level * level + 6L * level
                : (5L * level * level - 81L * level + 720L) / 2;
    }

    public static int points(int level, float progress, Mode mode) {
        int cost = cost(level, mode);
        // setExperiencePoints stores an integer point count as a float fraction.
        // Round its representation error back to the original point rather than
        // dropping one point on every transfer (for example, 14/107).
        return (int) clamp(Math.round((double) progress * cost), 0L, cost - 1L);
    }

    public static long total(int level, float progress, Mode mode) {
        return atLevel(level, mode) + points(level, progress, mode);
    }

    public static long maximum(Mode mode) {
        return atLevel(Integer.MAX_VALUE, mode) + cost(Integer.MAX_VALUE, mode) - 1L;
    }

    public static State resolve(long total, Mode mode) {
        total = clamp(total, 0L, maximum(mode));
        if (mode == Mode.ALL) return new State((int) (total / 7), (int) (total % 7));
        if (total >= 1395) {
            long upper = total - 1395;
            return new State(30 + (int) (upper / 107), (int) (upper % 107));
        }
        int level = 0;
        while (total >= cost(level, mode)) total -= cost(level++, mode);
        return new State(level, (int) total);
    }

    public static float progress(State state, Mode mode) {
        int cost = cost(state.level(), mode);
        float value = (float) state.points() / cost;
        // Also preserve consumers that truncate float * cost.
        if ((int) (value * cost) < state.points()) value = Math.nextUp(value);
        return value;
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
