package carpet.fga;

import com.google.gson.JsonParser;
import carpet.fga.mixin.ChunkGeneratorVoidDecorationMixin;
import carpet.fga.mixin.FlatLevelSourceVoidMixin;
import carpet.fga.mixin.NoiseBasedChunkGeneratorVoidMixin;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

final class TerrainGenerationMixinTargetTest {
    @Test void lazyRegenerationTargetsExistOnTheActualGameVersion() throws Exception {
        assertEquals(CompletableFuture.class,
                ChunkMap.class.getDeclaredMethod("scheduleChunkLoad", ChunkPos.class).getReturnType());
        assertEquals(ChunkAccess.class,
                ChunkMap.class.getDeclaredMethod("createEmptyChunk", ChunkPos.class).getReturnType());
        assertEquals(LevelHeightAccessor.class,
                ChunkAccess.class.getDeclaredField("levelHeightAccessor").getType());
    }

    @Test void terrainAndDecorationInjectionsMatchActualGeneratorSignatures() throws Exception {
        verifyInjections(NoiseBasedChunkGeneratorVoidMixin.class, NoiseBasedChunkGenerator.class);
        verifyInjections(FlatLevelSourceVoidMixin.class, FlatLevelSource.class);
        verifyInjections(ChunkGeneratorVoidDecorationMixin.class, ChunkGenerator.class);
    }

    @Test void requiredGenerationHooksAreRegisteredInProcessedResources() throws Exception {
        try (var stream = getClass().getResourceAsStream("/carpet-fga-addition.mixins.json")) {
            assertNotNull(stream);
            var config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(config.get("required").getAsBoolean());
            assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
            Set<String> mixins = java.util.stream.StreamSupport.stream(config.getAsJsonArray("mixins").spliterator(), false)
                    .map(element -> element.getAsString()).collect(Collectors.toSet());
            assertTrue(mixins.containsAll(Set.of("ChunkMapRegenerationMixin", "ChunkMapRegenerationAccessor",
                    "ChunkAccessLevelAccessor", "NoiseBasedChunkGeneratorVoidMixin",
                    "FlatLevelSourceVoidMixin", "ChunkGeneratorVoidDecorationMixin")), mixins.toString());
        }
    }

    private static void verifyInjections(Class<?> mixin, Class<?> target) throws Exception {
        int checked = 0;
        for (var handler : mixin.getDeclaredMethods()) {
            Inject injection = handler.getAnnotation(Inject.class);
            if (injection == null) continue;
            Class<?>[] parameters = handler.getParameterTypes();
            assertTrue(CallbackInfo.class.isAssignableFrom(parameters[parameters.length - 1]));
            for (String descriptor : injection.method()) {
                String name = descriptor.split("\\(", 2)[0];
                assertNotNull(target.getDeclaredMethod(name, Arrays.copyOf(parameters, parameters.length - 1)));
                assertFalse(Set.of("createStructures", "createReferences", "createBiomes").contains(name),
                        "void generation must preserve structure starts, references and biomes");
                checked++;
            }
        }
        assertTrue(checked > 0, mixin.getName());
    }
}
