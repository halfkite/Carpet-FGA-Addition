package carpet.fga;

import carpet.fga.mixin.MobBackpackMixin;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.Inject;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

final class MobBackpackMixinTargetTest {
    @Test void saveLoadAndDeathDropHooksMatchTheTargetVersion() throws Exception {
        for (var method : MobBackpackMixin.class.getDeclaredMethods()) {
            Inject annotation = method.getAnnotation(Inject.class);
            if (annotation == null) continue;
            Class<?>[] arguments = method.getParameterTypes();
            for (String targetMethod : annotation.method()) {
                String methodName = targetMethod.split("\\(", 2)[0];
                assertNotNull(Mob.class.getDeclaredMethod(methodName, Arrays.copyOf(arguments, arguments.length - 1)),
                        methodName + " target signature is present");
            }
        }
    }

    @Test void backpackMixinIsRequiredByThePackagedConfiguration() throws Exception {
        try (var stream = getClass().getResourceAsStream("/carpet-fga-addition.mixins.json")) {
            assertNotNull(stream);
            var config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(config.get("required").getAsBoolean());
            assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
            var mixins = java.util.stream.StreamSupport.stream(config.getAsJsonArray("mixins").spliterator(), false)
                    .map(element -> element.getAsString()).collect(Collectors.toSet());
            assertTrue(mixins.contains("MobBackpackMixin"));
        }
    }
}
