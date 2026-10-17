package carpet.fga;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class CraftableBarrierRecipeTest {
    @Test
    void recipeUsesEightGlassAroundOneObsidianAndProducesOneBarrier() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/data/carpet-fga-addition/recipe/craftable_barrier.json")) {
            assertNotNull(stream, "craftable barrier recipe resource must be packaged");
            JsonObject recipe = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();

            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
            JsonArray pattern = recipe.getAsJsonArray("pattern");
            assertEquals(List.of("GGG", "GOG", "GGG"), strings(pattern));

            JsonObject key = recipe.getAsJsonObject("key");
            assertEquals("minecraft:glass", ingredientId(key.get("G")));
            assertEquals("minecraft:obsidian", ingredientId(key.get("O")));

            JsonObject result = recipe.getAsJsonObject("result");
            assertEquals("minecraft:barrier", result.get("id").getAsString());
            assertEquals(1, result.get("count").getAsInt());
        }
    }

    @Test
    void ruleDefaultsToDisabled() {
        assertFalse(FGASettings.craftableBarrier);
    }

    private static String ingredientId(JsonElement ingredient) {
        return ingredient.isJsonObject()
                ? ingredient.getAsJsonObject().get("item").getAsString()
                : ingredient.getAsString();
    }

    private static List<String> strings(JsonArray array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.getAsString()));
        return values;
    }
}
