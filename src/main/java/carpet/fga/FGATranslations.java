package carpet.fga;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class FGATranslations {
    private static final String LANGUAGE_ROOT = "/assets/carpet-fga-addition/lang/";
    private static final Map<String, String> ENGLISH = loadLanguage("en_us.json");
    private static final Map<String, String> CHINESE = loadLanguage("zh_cn.json");

    private FGATranslations() {
    }

    public static Map<String, String> getTranslations(String lang) {
        String language = lang == null ? "" : lang.toLowerCase(Locale.ROOT);
        return language.equals("zh_cn") || language.equals("zh_ch") ? CHINESE : ENGLISH;
    }

    private static Map<String, String> loadLanguage(String fileName) {
        String resourcePath = LANGUAGE_ROOT + fileName;
        try (InputStream stream = FGATranslations.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing language resource: " + resourcePath);
            }
            JsonObject json = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> translations = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                if (!entry.getValue().isJsonPrimitive()
                        || !entry.getValue().getAsJsonPrimitive().isString()) {
                    throw new IllegalStateException("Language value must be a string: " + entry.getKey());
                }
                translations.put(entry.getKey(), entry.getValue().getAsString());
            }
            return Map.copyOf(translations);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to load language resource: " + resourcePath, exception);
        }
    }
}
