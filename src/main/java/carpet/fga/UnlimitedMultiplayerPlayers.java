//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import carpet.CarpetServer;
import carpet.api.settings.CarpetRule;
import net.fabricmc.loader.api.FabricLoader;

/** Resolves the effective player-limit bypass without depending on GCA at compile time. */
public final class UnlimitedMultiplayerPlayers {
    private static final String GCA_MOD_ID = "gca";
    private static final String GCA_RULE_NAME = "fakePlayerResident";

    private UnlimitedMultiplayerPlayers() {}

    public static boolean isEnabled() {
        if (FGASettings.unlimitedMultiplayerPlayers) return true;
        if (!FabricLoader.getInstance().isModLoaded(GCA_MOD_ID)) return false;
        carpet.api.settings.SettingsManager settingsManager = CarpetServer.settingsManager;
        if (settingsManager == null) return false;
        CarpetRule<?> rule = settingsManager.getCarpetRule(GCA_RULE_NAME);
        return rule != null && Boolean.TRUE.equals(rule.value());
    }
}
//#endif
