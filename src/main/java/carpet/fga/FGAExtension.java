package carpet.fga;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.api.settings.SettingsManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.util.Map;

/**
 * Carpet FGA Addition 扩展实现。
 * 将 FGA 规则直接注册到 carpet 主 SettingsManager（/carpet 命令），
 * 不创建独立的命令。
 */
public class FGAExtension implements CarpetExtension {

    private static final String MOD_ID = "carpet-fga-addition";
    private boolean previousBeeCollisionBoxRule;

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

    @Override
    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandBuildContext) {
        RangePlayerCommand.register(dispatcher);
    }

    @Override
    public void onTick(MinecraftServer server) {
        RangeActionManager.tick(server);
        if (previousBeeCollisionBoxRule != FGASettings.restorePre26BeeCollisionBox) {
            previousBeeCollisionBoxRule = FGASettings.restorePre26BeeCollisionBox;
            BeeDimensions.refreshLoadedBees(server);
        }
    }

    @Override
    public void onServerClosed(MinecraftServer server) {
        RangeActionManager.clear();
        previousBeeCollisionBoxRule = false;
    }

    @Override
    public void onPlayerLoggedOut(net.minecraft.server.level.ServerPlayer player) {
        FGAModDetector.remove(player);
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
