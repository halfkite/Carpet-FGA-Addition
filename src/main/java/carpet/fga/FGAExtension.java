package carpet.fga;

import carpet.CarpetExtension;
import carpet.CarpetServer;
//#if MC >= 1.21
import carpet.utils.CommandHelper;
//#endif
//#if MC >= 1.19
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
    private boolean previousBeeCollisionBoxRule;
    //#if MC >= 1.21.1 && MC <= 1.21.5
    private boolean previousSpectatorFreeTeleportRule;
    //#endif

    @Override
    public void onGameStarted() {
        VillagerBreedingAnimalization.registerRuleObserver();
        registerFgaCommandTreeRefreshObserver();
        registerUnlimitedFillLegacyBridge();
        registerItemFrameBlockificationObserver();
        registerPlayerLoadDistanceObserver();
        // Register FGA rules into carpet's main SettingsManager so they appear under /carpet.
        carpet.settings.SettingsManager carpetManager = CarpetServer.settingsManager;
        if (carpetManager != null) {
            carpetManager.parseSettingsClass(FGASettings.class);
        }
    }

    @Override
    public void registerLoggers() {
        //#if MC >= 1.19.4
        PlayerHealthDisplay.registerLogger();
        //#endif
    }

    /**
     * 返回 null 表示不创建独立命令，规则统一由 /carpet 管理。
     */
    @Override
    public SettingsManager
            //#if MC >= 1.19
            extensionSettingsManager() {
            //#else
            //$$ customSettingsManager() {
            //#endif
        return null;
    }

    @Override
    public void onServerLoaded(MinecraftServer server) {
        //#if MC >= 1.20.1
        VillagerPerformanceConfig.load(server);
        //#endif
        VehicleStopConfig.load(server);
        VehicleStopManager.clear();
        //#if MC >= 1.20.5 && MC <= 26.2
        DropPreStackConfig.load(server);
        //#if MC >= 1.21 && MC <= 26.2
        FakePlayerItemSortConfig.load(server);
        FakePlayerItemSortManager.load(server);
        //#endif
        //#if MC == 1.21.1
        PlayerLoadDistanceManager.load(server);
        PlayerTpEndControlManager.load(server);
        MinecartFeatureConfig.load(server);
        MinecartFeatureManager.load(server);
        ItemFrameBlockificationManager.rebuild(server);
        //#endif
        //#endif
        //#if MC <= 26.2
        DroppedItemStackLimitConfig.load(server);
        DroppedItemStackLimitConfig.warnLegacyRule(server);
        //#endif
        syncCarpetFillLimitForLegacyVersions(FGASettings.unlimitedFillCommands);
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
        //#if MC >= 1.20.1
        VillagerPerformanceCommand.register(dispatcher);
        //#endif
        VehicleStopCommand.register(dispatcher);
        //#if MC >= 1.20.5 && MC <= 26.2
        DropPreStackCommand.register(dispatcher);
        //#if MC >= 1.21 && MC <= 26.2
        FakePlayerItemSortCommand.register(dispatcher);
        TerrainRegenerationCommand.register(dispatcher);
        TrialStopCommand.register(dispatcher);
        //#endif
        //#if MC == 1.21.1
        PlayerLoadDistanceCommand.register(dispatcher);
        PlayerTpEndControlCommand.register(dispatcher);
        MinecartFeatureCommand.register(dispatcher);
        //#endif
        //#endif
        FGACommand.register(dispatcher);
    }

    @Override
    public void onTick(MinecraftServer server) {
        //#if MC <= 26.2
        DeathDropPreStackManager.clearTickCache();
        //#endif
        //#if MC >= 1.20.5
        StackLimitClientRequirement.tick(server);
        //#endif
        RangeActionManager.tick(server);
        //#if MC >= 1.21 && MC <= 26.2
        FakePlayerItemSortManager.tick(server);
        //#endif
        //#if MC == 1.21.1
        PlayerLoadDistanceManager.tick(server);
        MinecartFeatureManager.tick(server);
        //#endif
        //#if MC >= 1.16.5 && MC <= 26.2
        FullShulkerBoxCraftingManager.tick(server);
        //#endif
        //#if MC >= 1.16.5 && MC <= 26.2
        EndGatewayRegenerationManager.tick(server);
        //#endif
        //#if MC >= 1.19.4
        PlayerHealthDisplay.tick(server);
        //#endif
        if (previousBeeCollisionBoxRule != FGASettings.restorePre26BeeCollisionBox) {
            previousBeeCollisionBoxRule = FGASettings.restorePre26BeeCollisionBox;
            BeeDimensions.refreshLoadedBees(server);
        }
        //#if MC >= 1.21.1 && MC <= 1.21.5
        if (previousSpectatorFreeTeleportRule != FGASettings.spectatorFreeTeleport) {
            previousSpectatorFreeTeleportRule = FGASettings.spectatorFreeTeleport;
            server.getPlayerList().getPlayers().forEach(player -> server.getCommands().sendCommands(player));
        }
        //#endif
    }

    @Override
    public void onServerClosed(MinecraftServer server) {
        VehicleStopConfig.clear();
        VehicleStopManager.clear();
        //#if MC >= 1.21 && MC <= 26.2
        FakePlayerItemSortManager.close();
        TerrainRegenerationManager.clear();
        TrialSpawnerStopManager.clear();
        //#endif
        //#if MC >= 1.21.1
        FakePlayerProfilePreloadManager.close(server);
        //#if MC >= 1.21.1 && MC <= 26.2
        DropPreStackConfig.clear();
        //#if MC == 1.21.1
        PlayerLoadDistanceManager.clear();
        PlayerTpEndControlManager.clear();
        RecipeBookAlwaysUnlockedManager.clear();
        MinecartFeatureConfig.clear();
        MinecartFeatureManager.clear();
        ItemFrameBlockificationManager.clear();
        //#endif
        //#endif
        //#endif
        //#if MC >= 1.16.5 && MC <= 26.2
        FullShulkerBoxCraftingManager.clear();
        //#endif
        //#if MC >= 1.20.1
        VillagerTradeOnlyManager.clear();
        //#endif
        //#if MC >= 1.16.5 && MC <= 26.2
        EndGatewayRegenerationManager.clear();
        //#endif
        //#if MC >= 1.19.4
        PlayerHealthDisplay.clear(server);
        //#endif
        //#if MC <= 26.2
        DeathDropPreStackManager.clear();
        //#endif
        //#if MC >= 1.20.5
        StackLimitClientRequirement.clear();
        //#endif
        RangeActionManager.clear();
        previousBeeCollisionBoxRule = false;
        //#if MC >= 1.21.1 && MC <= 1.21.5
        previousSpectatorFreeTeleportRule = false;
        //#endif
    }

    @Override
    public void onPlayerLoggedOut(net.minecraft.server.level.ServerPlayer player) {
        FGAModDetector.remove(player);
        //#if MC == 1.21.1
        if (player instanceof carpet.patches.EntityPlayerMPFake) FakePlayerItemSortManager.markDashboardDirty();
        MinecartFeatureManager.removePlayer(player);
        PlayerLoadDistanceManager.onLogout(player);
        //#endif
        //#if MC >= 1.19.4
        PlayerHealthDisplay.remove(player);
        //#endif
    }

    @Override
    public void onPlayerLoggedIn(net.minecraft.server.level.ServerPlayer player) {
        //#if MC == 1.21.1
        if (player instanceof carpet.patches.EntityPlayerMPFake) FakePlayerItemSortManager.markDashboardDirty();
        PlayerLoadDistanceManager.onLogin(player);
        RecipeBookAlwaysUnlockedManager.onPlayerLoggedIn(player);
        //#endif
    }

    /**
     * 提供 FGA 分类和规则的中文翻译。
     */
    @Override
    public Map<String, String> canHasTranslations(String lang) {
        return FGATranslations.getTranslations(lang);
    }


    /**
     * Pre-1.19.4 Carpet already owns the /fill 32768 constant via fillLimit.
     * Sync that value instead of fighting Carpet's FillCommandMixin.
     */
    private static void registerUnlimitedFillLegacyBridge() {
        //#if MC < 1.19.4
        //#if MC >= 1.19
        carpet.api.settings.SettingsManager.registerGlobalRuleObserver((source, rule, userInput) -> {
            if ("unlimitedFillCommands".equals(rule.name())) {
                syncCarpetFillLimitForLegacyVersions(Boolean.TRUE.equals(rule.value()));
            }
        });
        //#else
        //$$ carpet.settings.SettingsManager.addGlobalRuleObserver((source, rule, userInput) -> {
        //$$     if ("unlimitedFillCommands".equals(rule.name)) {
        //$$         syncCarpetFillLimitForLegacyVersions(Boolean.TRUE.equals(rule.get()));
        //$$     }
        //$$ });
        //#endif
        //#endif
    }

    private static void registerFgaCommandTreeRefreshObserver() {
        //#if MC >= 1.21
        carpet.api.settings.SettingsManager.registerGlobalRuleObserver((source, rule, userInput) -> {
            if (!"droppedItemStackLimit".equals(rule.name())
                    && !"villagerPerformanceOptimization".equals(rule.name())
                    && !"minecartFeatureCommandPermission".equals(rule.name())
                    && !"terrainRegenerationCommandPermission".equals(rule.name())
                    && !"trialStopCommandPermission".equals(rule.name())
                    //#if MC == 1.21.1
                    && !"PlayerTpEndControl".equals(rule.name())
                    //#endif
                    ) {
                return;
            }
            MinecraftServer server = CarpetServer.minecraft_server;
            if (server != null) {
                CommandHelper.notifyPlayersCommandsChanged(server);
            }
        });
        //#endif
    }

    private static void registerItemFrameBlockificationObserver() {
        //#if MC >= 1.21 && MC <= 26.2
        carpet.api.settings.SettingsManager.registerGlobalRuleObserver((source, rule, userInput) -> {
            //#if MC == 1.21.1
            if ("itemFrameBlockification".equals(rule.name())) {
                MinecraftServer server = CarpetServer.minecraft_server;
                if (server != null) ItemFrameBlockificationManager.rebuild(server);
            }
            //#endif
            if ("comparatorThroughBlocks".equals(rule.name())) {
                MinecraftServer server = CarpetServer.minecraft_server;
                if (server != null) ComparatorThroughBlocks.refreshLoadedComparators(server);
            }
            //#if MC == 1.21.1
            if ("fireworkMinecartBoost".equals(rule.name())
                    && !Boolean.TRUE.equals(rule.value())) {
                MinecartFeatureManager.clearBoosts();
            }
            //#endif
        });
        //#endif
    }

    private static void registerPlayerLoadDistanceObserver() {
        //#if MC >= 1.21 && MC <= 26.2
        carpet.api.settings.SettingsManager.registerGlobalRuleObserver((source, rule, userInput) -> {
            MinecraftServer server = CarpetServer.minecraft_server;
            //#if MC == 1.21.1
            if ("playerLoadDistance".equals(rule.name())) {
                PlayerLoadDistanceManager.onRuleChanged();
                if (server == null) return;
                CommandHelper.notifyPlayersCommandsChanged(server);
                server.getPlayerList().getPlayers().forEach(player -> player.connection.send(
                        net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(server.getPlayerList().getPlayers())));
            } else
            //#endif
            if (("deepslateStonecuttingRecipes".equals(rule.name())
                    //#if MC >= 1.21 && MC <= 26.2
                    || "woodStonecuttingRecipes".equals(rule.name())
                    //#endif
                    ) && server != null) {
                net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket packet =
                        new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(
                                //#if MC >= 1.21.3
                                //$$ server.getRecipeManager().getSynchronizedItemProperties(),
                                //$$ server.getRecipeManager().getSynchronizedStonecutterRecipes());
                                //#else
                                server.getRecipeManager().getRecipes());
                                //#endif
                server.getPlayerList().getPlayers().forEach(player -> player.connection.send(packet));
            }
        });
        //#endif
    }

    private static void syncCarpetFillLimitForLegacyVersions(boolean unlimited) {
        //#if MC < 1.19.4
        //$$ try {
        //$$     // Direct field write bypasses /carpet validators; 20M is Carpet's documented max,
        //$$     // but MAX_VALUE is accepted by the fill mixin path and keeps parity with newer gamerule unlock.
        //$$     carpet.CarpetSettings.fillLimit = unlimited ? Integer.MAX_VALUE : 32768;
        //$$ } catch (Throwable ignored) {
        //$$     // Carpet version without fillLimit should not break startup.
        //$$ }
        //#endif
    }

    @Override
    public String version() {
        return MOD_ID;
    }
}
