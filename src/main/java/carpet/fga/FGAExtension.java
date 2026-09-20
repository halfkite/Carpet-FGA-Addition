package carpet.fga;

import carpet.CarpetExtension;
import carpet.CarpetServer;
//#if MC >= 1.21 && MC <= 26.3
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
//#endif
//#if MC >= 1.19
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
    //#if MC >= 1.21 && MC <= 26.3
    private boolean previousSpectatorFreeTeleportRule;
    //#endif

    @Override
    public void onGameStarted() {
        VillagerBreedingAnimalization.registerRuleObserver();
        registerFgaCommandTreeRefreshObserver();
        registerUnlimitedFillLegacyBridge();
        registerItemFrameBlockificationObserver();
        registerPlayerLoadDistanceObserver();
        //#if MC >= 1.21 && MC <= 26.3
        registerEnchantedGoldenCarrotObserver();
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        // Restore swapped body state before vanilla saves player data during shutdown.
        // Waiting until onServerClosed is too late and can persist the temporary position
        // of the possessed body as the controller's next-login position.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> PlayerPossessionManager.clear());
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        registerNetherPortalLightObserver();
        //#endif
        // Register FGA rules into carpet's main SettingsManager so they appear under /carpet.
        //#if MC >= 1.19
        carpet.api.settings.SettingsManager carpetManager = CarpetServer.settingsManager;
        //#else
        //$$ carpet.settings.SettingsManager carpetManager = CarpetServer.settingsManager;
        //#endif
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
        RecipeBookAlwaysUnlockedManager.clear();
        //#if MC >= 1.21 && MC <= 26.3
        PlayerTpEndControlManager.load(server);
        //#endif
        //#if MC >= 1.20.1 && MC <= 26.3
        FakePlayerItemSortConfig.load(server);
        FakePlayerItemSortManager.load(server);
        //#endif
        //#if MC >= 1.20.1 && MC <= 26.3
        DropPreStackConfig.load(server);
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        EntityDropRemovalConfig.load(server);
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        PiglinBarterCustomizationManager.load(server);
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        if (NetherPortalLightManager.isActive()) NetherPortalLightManager.activate(server);
        //#endif
        //#if MC == 1.20.1 || MC == 1.21.1
        PlayerLoadDistanceCompat.load(server);
        //#endif
        //#if MC == 1.20.1 || MC == 1.21.1
        MinecartFeatureConfig.load(server);
        MinecartFeatureManager.load(server);
        ItemFrameBlockificationManager.rebuild(server);
        //#endif
        //#if MC <= 26.3
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
        //#if MC >= 1.21 && MC <= 26.3
        PlayerFoodCommand.register(dispatcher);
        PiglinBarterItemExclusionsCommand.register(dispatcher);
        //#endif
        RangePlayerCommand.register(dispatcher);
        //#if MC >= 1.21 && MC <= 26.3
        PlayerPossessionStatusCommand.register(dispatcher);
        //#endif
        //#if MC <= 26.3
        DroppedItemStackLimitCommand.register(dispatcher);
        //#endif
        //#if MC >= 1.20.1
        VillagerPerformanceCommand.register(dispatcher);
        //#endif
        VehicleStopCommand.register(dispatcher);
        //#if MC >= 1.21 && MC <= 26.3
        PlayerTpEndControlCommand.register(dispatcher);
        //#endif
        //#if MC >= 1.20.1 && MC <= 26.3
        FakePlayerItemSortCommand.register(dispatcher);
        //#endif
        //#if MC >= 1.20.1 && MC <= 26.3
        DropPreStackCommand.register(dispatcher);
        //#if MC >= 1.21 && MC <= 26.3
        EntityDropRemovalCommand.register(dispatcher);
        //#endif
        //#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.3
        TerrainRegenerationCommand.register(dispatcher);
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        TrialStopCommand.register(dispatcher);
        //#endif
        //#if MC == 1.20.1 || MC == 1.21.1
        PlayerLoadDistanceCommand.register(dispatcher);
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        MapLoadCommand.register(dispatcher);
        //#endif
        //#endif
        //#if MC == 1.20.1 || MC == 1.21.1
        MinecartFeatureCommand.register(dispatcher);
        //#endif
        FGACommand.register(dispatcher);
    }

    @Override
    public void onTick(MinecraftServer server) {
        //#if MC <= 26.3
        DeathDropPreStackManager.clearTickCache();
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        NetherPortalLightManager.tick(server);
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        PlayerPossessionManager.tick(server);
        //#endif
        //#if MC >= 1.20.1
        StackLimitClientRequirement.tick(server);
        //#endif
        RangeActionManager.tick(server);
        //#if MC >= 1.20.1 && MC <= 26.3
        FakePlayerItemSortManager.tick(server);
        //#endif
        //#if MC == 1.20.1 || MC == 1.21.1
        PlayerLoadDistanceCompat.tick(server);
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        MapLoadManager.tick(server);
        //#endif
        //#if MC == 1.20.1 || MC == 1.21.1
        MinecartFeatureManager.tick(server);
        //#endif
        //#if MC >= 1.16.5 && MC <= 26.3
        FullShulkerBoxCraftingManager.tick(server);
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        EndGatewayRegenerationManager.tick(server);
        //#endif
        //#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.3
        TerrainRegenerationManager.tick(server);
        //#endif
        //#if MC >= 1.19.4
        PlayerHealthDisplay.tick(server);
        //#endif
        if (previousBeeCollisionBoxRule != FGASettings.restorePre26BeeCollisionBox) {
            previousBeeCollisionBoxRule = FGASettings.restorePre26BeeCollisionBox;
            BeeDimensions.refreshLoadedBees(server);
        }
        //#if MC >= 1.21 && MC <= 26.3
        if (previousSpectatorFreeTeleportRule != FGASettings.spectatorFreeTeleport) {
            previousSpectatorFreeTeleportRule = FGASettings.spectatorFreeTeleport;
            server.getPlayerList().getPlayers().forEach(player -> server.getCommands().sendCommands(player));
        }
        //#endif
    }

    @Override
    public void onServerClosed(MinecraftServer server) {
        //#if MC >= 1.21 && MC <= 26.3
        PlayerPossessionManager.clear();
        //#endif
        VehicleStopConfig.clear();
        VehicleStopManager.clear();
        RecipeBookAlwaysUnlockedManager.clear();
        //#if MC >= 1.21 && MC <= 26.3
        PlayerTpEndControlManager.clear();
        //#endif
        //#if MC >= 1.20.1 && MC <= 26.3
        FakePlayerItemSortManager.close();
        //#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.3
        TerrainRegenerationManager.clear();
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        TrialSpawnerStopManager.clear();
        //#endif
        //#endif
        //#if MC == 1.20.1 || MC >= 1.21.1
        FakePlayerProfilePreloadManager.close(server);
        //#endif
        //#if MC >= 1.20.1 && MC <= 26.3
        DropPreStackConfig.clear();
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        EntityDropRemovalConfig.clear();
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        PiglinBarterCustomizationManager.clear();
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        MapLoadManager.clear();
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        NetherPortalLightManager.clear();
        //#endif
        //#if MC == 1.20.1 || MC == 1.21.1
        PlayerLoadDistanceCompat.clear();
        //#endif
        //#if MC == 1.20.1 || MC == 1.21.1
        MinecartFeatureConfig.clear();
        MinecartFeatureManager.clear();
        ItemFrameBlockificationManager.clear();
        //#endif
        //#if MC >= 1.16.5 && MC <= 26.3
        FullShulkerBoxCraftingManager.clear();
        //#endif
        //#if MC >= 1.20.1
        VillagerTradeOnlyManager.clear();
        //#endif
        //#if MC >= 1.21 && MC <= 26.3
        EndGatewayRegenerationManager.clear();
        //#endif
        //#if MC >= 1.19.4
        PlayerHealthDisplay.clear(server);
        //#endif
        //#if MC <= 26.3
        DeathDropPreStackManager.clear();
        //#endif
        //#if MC >= 1.20.1
        StackLimitClientRequirement.clear();
        //#endif
        RangeActionManager.clear();
        previousBeeCollisionBoxRule = false;
        //#if MC >= 1.21 && MC <= 26.3
        previousSpectatorFreeTeleportRule = false;
        //#endif
    }

    @Override
    public void onPlayerLoggedOut(net.minecraft.server.level.ServerPlayer player) {
        FGAModDetector.remove(player);
        //#if MC >= 1.20.1
        StackLimitClientRequirement.onPlayerLoggedOut(player);
        //#endif
        //#if MC == 1.20.1 || MC == 1.21.1
        if (player instanceof carpet.patches.EntityPlayerMPFake) FakePlayerItemSortManager.markDashboardDirty();
        MinecartFeatureManager.removePlayer(player);
        //#endif
        //#if MC == 1.20.1 || MC == 1.21.1
        PlayerLoadDistanceCompat.onLogout(player);
        //#endif
        //#if MC >= 1.19.4
        PlayerHealthDisplay.remove(player);
        //#endif
    }

    @Override
    public void onPlayerLoggedIn(net.minecraft.server.level.ServerPlayer player) {
        //#if MC >= 1.20.1
        StackLimitClientRequirement.onPlayerLoggedIn(player);
        //#endif
        RecipeBookAlwaysUnlockedManager.onPlayerLoggedIn(player);
        //#if MC == 1.20.1 || MC == 1.21.1
        if (player instanceof carpet.patches.EntityPlayerMPFake) FakePlayerItemSortManager.markDashboardDirty();
        //#endif
        //#if MC == 1.20.1 || MC == 1.21.1
        PlayerLoadDistanceCompat.onLogin(player);
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
        //#if MC >= 1.19
        carpet.api.settings.SettingsManager.registerGlobalRuleObserver((source, rule, userInput) -> {
            if (!"droppedItemStackLimit".equals(rule.name())
                    //#if MC >= 1.21 && MC <= 26.3
                    && !"playerPossession".equals(rule.name())
                    && !"commandPlayer".equals(rule.name())
                    && !"showControllerPrefix".equals(rule.name())
                    && !"permissionSwapsToo".equals(rule.name())
                    //#endif
                    //#if MC >= 1.21 && MC <= 26.3
                    && !"piglinBarterItemExclusions".equals(rule.name())
                    //#endif
                    && !"villagerPerformanceOptimization".equals(rule.name())
                    && !"minecartFeatureCommandPermission".equals(rule.name())
                    && !"terrainRegenerationCommandPermission".equals(rule.name())
                    //#if MC >= 1.21 && MC <= 26.3
                    && !"mapLoadCommandPermission".equals(rule.name())
                    //#endif
                    && !"trialStopCommandPermission".equals(rule.name())
                    && !"entityDropRemoval".equals(rule.name())
                    //#if MC >= 1.21 && MC <= 26.3
                    && !"PlayerTpEndControl".equals(rule.name())
                    //#endif
                    ) {
                return;
            }
            MinecraftServer server = CarpetServer.minecraft_server;
            if (server != null) {
                //#if MC >= 1.21 && MC <= 26.3
                if ("playerPossession".equals(rule.name()) || "commandPlayer".equals(rule.name())) {
                    PlayerPossessionManager.onRuleChanged();
                }
                if ("showControllerPrefix".equals(rule.name())) {
                    PlayerPossessionManager.refreshDisplayNames(server);
                }
                if ("permissionSwapsToo".equals(rule.name())) {
                    PlayerPossessionManager.refreshPermissions(server);
                }
                //#endif
                //#if MC >= 1.21 && MC <= 26.3
                if ("piglinBarterItemExclusions".equals(rule.name())) {
                    PiglinBarterCustomizationManager.onRuleChanged(server);
                }
                //#endif
                CommandHelper.notifyPlayersCommandsChanged(server);
            }
        });
        //#endif
    }

    private static void registerItemFrameBlockificationObserver() {
        //#if MC >= 1.20.1 && MC <= 26.3
        carpet.api.settings.SettingsManager.registerGlobalRuleObserver((source, rule, userInput) -> {
            //#if MC == 1.20.1 || MC == 1.21.1
            if ("itemFrameBlockification".equals(rule.name())) {
                MinecraftServer server = CarpetServer.minecraft_server;
                if (server != null) ItemFrameBlockificationManager.rebuild(server);
            }
            //#endif
            if ("comparatorThroughBlocks".equals(rule.name())) {
                MinecraftServer server = CarpetServer.minecraft_server;
                if (server != null) ComparatorThroughBlocks.refreshLoadedComparators(server);
            }
            //#if MC == 1.20.1 || MC == 1.21.1
            if ("fireworkMinecartBoost".equals(rule.name())
                    && !Boolean.TRUE.equals(rule.value())) {
                MinecartFeatureManager.clearBoosts();
            }
            //#endif
        });
        //#endif
    }

    private static void registerPlayerLoadDistanceObserver() {
        //#if MC >= 1.20.1 && MC <= 26.3
        carpet.api.settings.SettingsManager.registerGlobalRuleObserver((source, rule, userInput) -> {
            MinecraftServer server = CarpetServer.minecraft_server;
            //#if MC == 1.20.1 || MC == 1.21.1
            if ("playerLoadDistance".equals(rule.name())) {
                PlayerLoadDistanceCompat.onRuleChanged();
                if (server == null) return;
                CommandHelper.notifyPlayersCommandsChanged(server);
                server.getPlayerList().getPlayers().forEach(player -> player.connection.send(
                        net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(server.getPlayerList().getPlayers())));
            } else
            //#endif
            if (("deepslateStonecuttingRecipes".equals(rule.name())
                    //#if MC >= 1.20.1 && MC <= 26.3
                    || "woodStonecuttingRecipes".equals(rule.name())
                    //#if MC >= 1.21 && MC <= 26.3
                    || "lightSourceStonecuttingRecipes".equals(rule.name())
                    //#endif
                    //#endif
                    ) && server != null) {
                boolean lightRecipesChanged = "lightSourceStonecuttingRecipes".equals(rule.name());
                net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket packet =
                        new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(
                                //#if MC >= 1.21.3
                                //$$ server.getRecipeManager().getSynchronizedItemProperties(),
                                //$$ server.getRecipeManager().getSynchronizedStonecutterRecipes());
                                //#else
                                server.getRecipeManager().getRecipes());
                                //#endif
                server.getPlayerList().getPlayers().forEach(player -> {
                    player.connection.send(packet);
                    //#if MC == 1.21.1
                    // A stonecutter already open while the rule changes keeps its
                    // previous recipe list until the next input update. Close it so
                    // the client immediately rebuilds the list from the new packet.
                    if (lightRecipesChanged && player.containerMenu
                            instanceof net.minecraft.world.inventory.StonecutterMenu) {
                        player.closeContainer();
                    }
                    //#endif
                });
            }
        });
        //#endif
    }

    //#if MC >= 1.21 && MC <= 26.3
    private static void registerEnchantedGoldenCarrotObserver() {
        carpet.api.settings.SettingsManager.registerGlobalRuleObserver((source, rule, userInput) -> {
            if (!"enchantedGoldenCarrot".equals(rule.name())) return;
            MinecraftServer server = CarpetServer.minecraft_server;
            if (server == null) return;
            boolean enabled = FGASettings.enchantedGoldenCarrot;
            //#if MC < 1.21.3
            // Rebuild the client recipe manager from the server's filtered recipe set on
            // both transitions.  A REMOVE packet only clears the known flag; the client
            // still keeps the recipe definition and can display it when the recipe book
            // is showing all recipes.  Re-sending the filtered full set removes those
            // definitions, then ADD restores the player's previous vanilla unlocks.
            net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket packet =
                    new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(
                            server.getRecipeManager().getRecipes());
            server.getPlayerList().getPlayers().forEach(player -> {
                player.connection.send(packet);
                java.util.List<net.minecraft.resources.ResourceLocation> knownRecipes =
                        server.getRecipeManager().getRecipes().stream()
                                .filter(holder -> enabled || !EnchantedGoldenCarrotManager.isRecipe(holder))
                                .filter(holder -> player.getRecipeBook().contains(holder))
                                .map(net.minecraft.world.item.crafting.RecipeHolder::id)
                                .toList();
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundRecipePacket(
                        net.minecraft.network.protocol.game.ClientboundRecipePacket.State.ADD,
                        knownRecipes,
                        java.util.List.of(),
                        player.getRecipeBook().getBookSettings()));
            });
            //#else
            //$$ // 26.3 sends the property sets used by the display-based recipe book
            //$$ net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket packet =
            //$$         new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(
            //$$                 server.getRecipeManager().getSynchronizedItemProperties(),
            //$$                 server.getRecipeManager().getSynchronizedStonecutterRecipes());
            //$$ server.getPlayerList().getPlayers().forEach(player -> player.connection.send(packet));
            //#endif
        });
    }
    //#endif

    //#if MC >= 1.21 && MC <= 26.3
    private static void registerNetherPortalLightObserver() {
        carpet.api.settings.SettingsManager.registerGlobalRuleObserver((source, rule, userInput) -> {
            if (!"netherPortalNoLight".equals(rule.name())) return;
            if (!"true".equals(rule.value()) && !"onlynew".equals(rule.value())) return;
            MinecraftServer server = CarpetServer.minecraft_server;
            if (server != null) NetherPortalLightManager.activate(server);
        });
    }
    //#endif

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
