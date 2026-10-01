//#if MC == 1.21.1
package carpet.fga;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PlayerLoadDistanceLifecycleTest {
    @Test void inactiveCallbacksNeverRequestGlobalRestore() {
        var lifecycle = new PlayerLoadDistanceLifecycle();
        for (int i = 0; i < 100; i++) assertTrue(lifecycle.release(16).isEmpty());
        assertFalse(lifecycle.active());
    }

    @Test void activationCapturesCurrentDistanceNotStartupDistance() {
        var lifecycle = new PlayerLoadDistanceLifecycle();
        lifecycle.release(10);
        lifecycle.activate(16);
        lifecycle.activate(10);
        assertEquals(16, lifecycle.baseline());
    }

    @Test void ownedGlobalDistanceIsRestoredOnlyOnce() {
        var lifecycle = new PlayerLoadDistanceLifecycle();
        lifecycle.activate(16);
        lifecycle.globalApplied(24, true);
        assertEquals(16, lifecycle.release(24).orElseThrow());
        assertTrue(lifecycle.release(24).isEmpty());
        assertFalse(lifecycle.active());
    }

    @Test void anotherWritersGlobalDistanceIsPreserved() {
        var lifecycle = new PlayerLoadDistanceLifecycle();
        lifecycle.activate(16);
        lifecycle.globalApplied(24, true);
        assertTrue(lifecycle.release(12).isEmpty());
    }

    @Test void noWriteDoesNotClaimOwnership() {
        var lifecycle = new PlayerLoadDistanceLifecycle();
        lifecycle.activate(16);
        lifecycle.globalApplied(24, false);
        assertTrue(lifecycle.release(24).isEmpty());
    }

    @Test void nextActivationUsesNewVanillaBaseline() {
        var lifecycle = new PlayerLoadDistanceLifecycle();
        lifecycle.activate(10);
        lifecycle.globalApplied(24, true);
        lifecycle.release(24);
        lifecycle.activate(20);
        lifecycle.globalApplied(28, true);
        assertEquals(20, lifecycle.release(28).orElseThrow());
    }
}
//#endif
