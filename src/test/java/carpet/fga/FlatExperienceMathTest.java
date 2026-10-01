package carpet.fga;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class FlatExperienceMathTest {
    @Test
    void matchesIndependentPerLevelSumAndPreservesEveryResidualPoint() {
        for (var mode : FlatExperienceMath.Mode.values()) {
            long expected = 0;
            for (int level = 0; level <= 500; level++) {
                assertEquals(expected, FlatExperienceMath.atLevel(level, mode));
                int cost = mode == FlatExperienceMath.Mode.ALL ? 7
                        : level >= 30 ? 107 : level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
                for (int point = 0; point < cost; point++) {
                    var state = FlatExperienceMath.resolve(expected + point, mode);
                    assertEquals(level, state.level());
                    assertEquals(point, state.points());
                    float progress = FlatExperienceMath.progress(state, mode);
                    assertEquals(expected + point, FlatExperienceMath.total(level, progress, mode));
                    assertEquals(point, (int) (progress * cost));
                }
                expected += cost;
            }
        }
    }

    @Test
    void crossesThirtyInBothDirections() {
        var mode = FlatExperienceMath.Mode.AFTER_THIRTY;
        assertEquals(new FlatExperienceMath.State(29, 106), FlatExperienceMath.resolve(1394, mode));
        assertEquals(new FlatExperienceMath.State(30, 0), FlatExperienceMath.resolve(1395, mode));
        assertEquals(new FlatExperienceMath.State(30, 106), FlatExperienceMath.resolve(1501, mode));
        assertEquals(new FlatExperienceMath.State(31, 0), FlatExperienceMath.resolve(1502, mode));
        assertEquals(new FlatExperienceMath.State(29, 106), FlatExperienceMath.resolve(1502 - 108, mode));
    }

    @Test
    void hugePositiveAndNegativeAwardsAreBoundedAndDoNotOverflow() {
        assertTimeout(Duration.ofSeconds(2), () -> {
            Random random = new Random(731);
            for (var mode : FlatExperienceMath.Mode.values()) {
                for (int i = 0; i < 10000; i++) {
                    int level = random.nextInt(Integer.MAX_VALUE);
                    int point = random.nextInt(FlatExperienceMath.cost(level, mode));
                    long total = FlatExperienceMath.atLevel(level, mode) + point;
                    assertEquals(new FlatExperienceMath.State(level, point), FlatExperienceMath.resolve(total, mode));
                    long changed = total + (i % 2 == 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE);
                    var result = FlatExperienceMath.resolve(changed, mode);
                    long clamped = Math.max(0L, Math.min(FlatExperienceMath.maximum(mode), changed));
                    assertEquals(clamped,
                            FlatExperienceMath.atLevel(result.level(), mode) + result.points());
                }
                assertEquals(0, FlatExperienceMath.resolve(Long.MIN_VALUE, mode).level());
                assertEquals(Integer.MAX_VALUE, FlatExperienceMath.resolve(Long.MAX_VALUE, mode).level());
            }
        });
    }
}
