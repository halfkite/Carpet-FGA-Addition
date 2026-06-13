package carpet.fga;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.api.settings.SettingsManager;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Map;

/**
 * Carpet FGA Addition 扩展实现。
 * 将 FGA 规则直接注册到 carpet 主 SettingsManager（/carpet 命令），
 * 不创建独立的命令。
 */
public class FGAExtension implements CarpetExtension {

    private static final String MOD_ID = "carpet-fga-addition";

    @Override
    public void onGameStarted() {
        // 将 FGA 规则注册到 carpet 主 SettingsManager，这样规则出现在 /carpet 下
        carpet.settings.SettingsManager carpetManager = CarpetServer.settingsManager;
        if (carpetManager != null) {
            carpetManager.parseSettingsClass(FGASettings.class);
        }
    }

    /**
     * 返回 null 表示不创建独立命令，规则统一由 /carpet 管理。
     */
    @Override
    public SettingsManager extensionSettingsManager() {
        return null;
    }

    /**
     * 提供 FGA 分类和规则的中文翻译。
     */
    @Override
    public Map<String, String> canHasTranslations(String lang) {
        return FGATranslations.getTranslations(lang);
    }

    @Override
    public String version() {
        return MOD_ID;
    }
}
