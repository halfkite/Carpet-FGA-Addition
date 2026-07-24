package carpet.fga;

import carpet.CarpetExtension;
import carpet.CarpetServer;
//#if MC >= 1.18
import carpet.api.settings.SettingsManager;
//#else
//$$ import carpet.settings.SettingsManager;
//#endif
import com.mojang.brigadier.CommandDispatcher;
//#if MC >= 1.19.3
import net.minecraft.commands.CommandBuildContext;
//#endif
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
    private static volatile boolean villagerPerformanceCommandTreeRefreshRequested;
    private boolean previousBeeCollisionBoxRule;

    public static void requestVillagerPerformanceCommandTreeRefresh() {
        villagerPerformanceCommandTreeRefreshRequested = true;
    }

    @Override
    public void onGameStarted() {
        VillagerBreedingAnimalization.registerRuleObserver();
        //#if MC < 1.18
        //$$ carpet.settings.SettingsManager.addGlobalRuleObserver((source, rule, userInput) -> {
        //$$     if (rule.name.equals("unlimitedFillCommands")) {
        //$$         carpet.CarpetSettings.fillLimit = Boolean.TRUE.equals(rule.get()) ? Integer.MAX_VALUE : 32768;
        //$$     }
        //$$ });
        //#endif
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
    public SettingsManager
            //#if MC >= 1.18
            extensionSettingsManager() {
            //#else
            //$$ customSettingsManager() {
            //#endif
        return null;
    }

    @Override
    public void onServerLoaded(MinecraftServer server) {
        //#if MC >= 1.21.1
        VillagerPerformanceConfig.load(server);
        //#endif
        //#if MC <= 26.2
        DroppedItemStackLimitConfig.load(server);
        DroppedItemStackLimitConfig.warnLegacyRule(server);
        //#endif
    }

    @Override
    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher
                                 //#if MC >= 1.19.3
                                 , CommandBuildContext commandBuildContext
                                 //#endif
    ) {
        RangePlayerCommand.register(dispatcher);
        //#if MC <= 26.2
        DroppedItemStackLimitCommand.register(dispatcher);
        //#endif
        //#if MC >= 1.21.1
        VillagerPerformanceCommand.register(dispatcher);
        //#endif
    }

    @Override
    public void onTick(MinecraftServer server) {
        //#if MC >= 1.21.1
        if (villagerPerformanceCommandTreeRefreshRequested) {
            villagerPerformanceCommandTreeRefreshRequested = false;
            server.getPlayerList().getPlayers().forEach(player -> server.getCommands().sendCommands(player));
        }
        //#endif
        //#if MC <= 26.2
        DeathDropPreStackManager.clearTickCache();
        //#endif
        RangeActionManager.tick(server);
        if (previousBeeCollisionBoxRule != FGASettings.restorePre26BeeCollisionBox) {
            previousBeeCollisionBoxRule = FGASettings.restorePre26BeeCollisionBox;
            BeeDimensions.refreshLoadedBees(server);
        }
    }

    @Override
    public void onServerClosed(MinecraftServer server) {
        //#if MC >= 1.21.1
        FakePlayerProfilePreloadManager.close(server);
        VillagerTradeOnlyManager.clear();
        //#endif
        //#if MC <= 26.2
        DeathDropPreStackManager.clear();
        //#endif
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
