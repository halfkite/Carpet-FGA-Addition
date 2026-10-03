package carpet.fga;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class TerrainRegenerationLifecycleTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach void clear() { TerrainRegenerationManager.clear(); }

    @Test void closingAWorldDiscardsLazyMarksAndCompletedLoadCleanup() throws Exception {
        ChunkPos position = new ChunkPos(4, 7);
        long key = FGACompat.chunkKey(4, 7);
        map("REGENERATE_ON_LOAD").put("minecraft:overworld", new java.util.HashSet<>(Set.of(key)));
        TerrainRegenerationManager.markRegenerated(position, "minecraft:overworld");
        assertTrue(TerrainRegenerationManager.shouldRegenerateFromScratch(position, "minecraft:overworld"));
        assertFalse(map("CLEANUP_AFTER_LOAD").isEmpty());

        TerrainRegenerationManager.clear();

        assertFalse(TerrainRegenerationManager.shouldRegenerateFromScratch(position, "minecraft:overworld"));
        assertTrue(map("REGENERATE_ON_LOAD").isEmpty());
        assertTrue(map("CLEANUP_AFTER_LOAD").isEmpty());
        // A new world may reuse the coordinate and independently mark it in another dimension.
        map("REGENERATE_ON_LOAD").put("minecraft:the_nether", new java.util.HashSet<>(Set.of(key)));
        assertFalse(TerrainRegenerationManager.shouldRegenerateFromScratch(position, "minecraft:overworld"));
        assertTrue(TerrainRegenerationManager.shouldRegenerateFromScratch(position, "minecraft:the_nether"));
    }

    @Test void closingAWorldDiscardsUnfinishedClearTasks() throws Exception {
        UUID id = UUID.randomUUID();
        var task = new TerrainRegenerationManager.Task(id, TerrainRegenerationManager.Type.CLEAR,
                "minecraft:overworld", 0, 0, 0, 0, TerrainRegenerationManager.Status.RUNNING,
                "test", 0L, List.of(), null);
        Class<?> live = Class.forName("carpet.fga.TerrainRegenerationManager$LiveState");
        var constructor = live.getDeclaredConstructor(TerrainRegenerationManager.Task.class, Path.class);
        constructor.setAccessible(true);
        map("LIVE").put(id, constructor.newInstance(task, Path.of("test-backup")));

        TerrainRegenerationManager.clear();
        // A newly loaded world with no tasks must not try to tick an old destructive task.
        assertDoesNotThrow(() -> TerrainRegenerationManager.tick(null));
        assertTrue(map("LIVE").isEmpty());
        assertTrue(TerrainRegenerationManager.tasks().isEmpty());
        assertDoesNotThrow(TerrainRegenerationManager::clear);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> map(String name) throws Exception {
        Field field = TerrainRegenerationManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<Object, Object>) field.get(null);
    }
}
