package carpet.fga;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class MapLoadReleaseLifecycleTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach void clear() { MapLoadManager.clear(); }

    @Test void lastStoppedTaskContinuesToReleaseTicketsWithoutActiveTasks() throws Exception {
        MapLoadManager.clear();
        int[] remaining = {100};
        queue().add(limit -> {
            assertEquals(32, limit);
            remaining[0] -= Math.min(limit, remaining[0]);
            return remaining[0] == 0;
        });
        for (int expected : new int[]{68, 36, 4, 0}) {
            MapLoadManager.tick(null);
            assertEquals(expected, remaining[0]);
        }
        assertTrue(queue().isEmpty());
        assertDoesNotThrow(() -> MapLoadManager.tick(null));
    }

    @Test void unreadyTicketsRemainQueuedUntilALaterTickCanReleaseThem() throws Exception {
        MapLoadManager.clear();
        int[] attempts = {0};
        queue().add(limit -> ++attempts[0] >= 3);
        MapLoadManager.tick(null);
        MapLoadManager.tick(null);
        assertEquals(1, queue().size());
        MapLoadManager.tick(null);
        assertEquals(3, attempts[0]);
        assertTrue(queue().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static List<MapLoadManager.TicketRelease> queue() throws Exception {
        var field = MapLoadManager.class.getDeclaredField("DRAINING");
        field.setAccessible(true);
        return (List<MapLoadManager.TicketRelease>) field.get(null);
    }
}
