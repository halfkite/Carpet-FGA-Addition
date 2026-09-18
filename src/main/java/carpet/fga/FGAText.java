//#if MC >= 1.19.4
package carpet.fga;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Player facing text. The client resolves the translation key when it has this mod, so the text follows
 * the client language; when it does not (no mod, or an older build without the key), the fallback is
 * shown instead - the same text in the server language. Either way the player never sees a raw key.
 */
public final class FGAText {
    private static final String PREFIX = "carpet.fga.";
    private static final Map<String, String> SERVER_LANGUAGE = loadServerLanguage();

    private FGAText() {
    }

    public static Component text(String key, Object... args) {
        return Component.translatableWithFallback(key, format(key, args), args);
    }

    /** The text in the server language, as a plain string. */
    public static String raw(String key, Object... args) {
        return format(key, args);
    }

    private static String format(String key, Object... args) {
        String pattern = SERVER_LANGUAGE.getOrDefault(key, key);
        return args.length == 0 ? pattern : String.format(pattern, args);
    }

    private static Map<String, String> loadServerLanguage() {
        Locale locale = Locale.getDefault();
        String file = "en_us.json";
        if ("zh".equals(locale.getLanguage())) {
            String country = locale.getCountry();
            file = "TW".equals(country) || "HK".equals(country) ? "zh_tw.json" : "zh_cn.json";
        }
        Map<String, String> messages = new HashMap<>();
        try (InputStream stream = FGAText.class.getResourceAsStream(
                "/assets/carpet-fga-addition/lang/" + file)) {
            if (stream == null) return messages;
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                if (entry.getKey().startsWith(PREFIX)) messages.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception exception) {
            // Nothing to do: text() then falls back to the key, which only happens when the lang file
            // itself is unreadable.
        }
        return messages;
    }
}
//#endif
