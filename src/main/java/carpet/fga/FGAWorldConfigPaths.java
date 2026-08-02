package carpet.fga;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.function.Predicate;

/** Resolves and safely migrates FGA files stored inside a world. */
public final class FGAWorldConfigPaths {
    private FGAWorldConfigPaths() {
    }

    public static Path current(MinecraftServer server, String fileName) {
        return server.getWorldPath(LevelResource.ROOT).resolve("config")
                .resolve("carpetfgaaddition").resolve(fileName);
    }

    public static Path legacy(MinecraftServer server, String fileName) {
        return server.getWorldPath(LevelResource.ROOT).resolve("carpet")
                .resolve("carpetfgaaddition").resolve(fileName);
    }

    /**
     * Copies a legacy file into the new directory only after the supplied validator accepts it.
     * A failed migration leaves the legacy file untouched and returns its path for read-only fallback.
     */
    public static Path migrate(Path current, Path legacy, Predicate<Path> validator) throws IOException {
        if (Files.exists(current) || !Files.isRegularFile(legacy)) return current;

        Files.createDirectories(current.getParent());
        Path temporary = current.resolveSibling(current.getFileName() + ".migration-" + UUID.randomUUID() + ".tmp");
        try {
            Files.copy(legacy, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (!validator.test(temporary)) return legacy;
            moveAtomically(temporary, current);
            try {
                moveLegacyToBackup(legacy);
            } catch (IOException ignored) {
                // The new file is already authoritative; retain the old file if its backup rename fails.
            }
            return current;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void moveLegacyToBackup(Path legacy) throws IOException {
        Path backup = legacy.resolveSibling(legacy.getFileName() + ".migrated");
        if (Files.exists(backup)) {
            backup = legacy.resolveSibling(legacy.getFileName() + ".migrated-" + System.currentTimeMillis());
        }
        Files.move(legacy, backup);
    }
}
