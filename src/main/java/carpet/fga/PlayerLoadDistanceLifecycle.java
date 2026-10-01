//#if MC == 1.21.1
package carpet.fga;

import java.util.OptionalInt;

/** Tracks only view-distance writes owned by an active FGA rule session. */
final class PlayerLoadDistanceLifecycle {
    private boolean active;
    private boolean ownsGlobalDistance;
    private int baseline;
    private int lastGlobalDistance;

    void activate(int currentDistance) {
        if (active) return;
        active = true;
        ownsGlobalDistance = false;
        baseline = currentDistance;
        lastGlobalDistance = currentDistance;
    }

    boolean active() { return active; }
    int baseline() { return baseline; }
    int lastGlobalDistance() { return lastGlobalDistance; }

    void globalApplied(int distance, boolean written) {
        if (!active) throw new IllegalStateException("Inactive load-distance session");
        lastGlobalDistance = distance;
        ownsGlobalDistance = written;
    }

    OptionalInt release(int currentDistance) {
        boolean restore = active && ownsGlobalDistance && currentDistance == lastGlobalDistance
                && currentDistance != baseline;
        active = false;
        ownsGlobalDistance = false;
        return restore ? OptionalInt.of(baseline) : OptionalInt.empty();
    }

    void reset() {
        active = false;
        ownsGlobalDistance = false;
        baseline = 0;
        lastGlobalDistance = 0;
    }
}
//#endif
