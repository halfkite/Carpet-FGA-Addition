//#if MC >= 1.19.4
package carpet.fga;

import carpet.CarpetSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;
import java.util.Map;

/**
 * Player facing text. The client resolves the translation key when it has this mod, so the text follows
 * the client language; when it does not (no mod, or an older build without the key), the fallback is
 * shown instead - the same text in the server language. Either way the player never sees a raw key.
 */
public final class FGAText {
    private FGAText() {
    }

    public static MutableComponent text(String key, Object... args) {
        return Component.translatableWithFallback(key, format(key, args), args);
    }

    /** The text in the server language, as a plain string. */
    public static String raw(String key, Object... args) {
        return format(key, args);
    }

    private static String format(String key, Object... args) {
        return formatForLanguage(CarpetSettings.language, key, args);
    }

    static String formatForLanguage(String language, String key, Object... args) {
        Map<String, String> serverLanguage = FGATranslations.getTranslations(language);
        String pattern = serverLanguage.getOrDefault(key, key);
        return args.length == 0 ? pattern : String.format(Locale.ROOT, pattern, args);
    }
}
//#endif
