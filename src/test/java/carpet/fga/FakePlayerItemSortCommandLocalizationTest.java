package carpet.fga;

import org.junit.jupiter.api.Test;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FakePlayerItemSortCommandLocalizationTest {
    @Test
    void settingSuggestionsOnlyContainValuesForTheSelectedKey() {
        assertEquals(List.of("false", "true"), FakePlayerItemSortCommand.settingValues("quickShulker"));
        assertEquals(List.of("false", "true"), FakePlayerItemSortCommand.settingValues("cleanOpenedTarget"));
        assertEquals(List.of("false", "true"), FakePlayerItemSortCommand.settingValues("shulkerRestock"));
        //#if MC == 26.3
        assertEquals(List.of("false", "true", "login"), FakePlayerItemSortCommand.settingValues("dashboard"));
        //#else
        //$$ assertEquals(List.of("false", "true"), FakePlayerItemSortCommand.settingValues("dashboard"));
        //#endif
        assertEquals(List.of("false", "true"), FakePlayerItemSortCommand.settingValues("summonNotices"));
        assertEquals(List.of("english", "chinese", "custom"), FakePlayerItemSortCommand.settingValues("targetLanguage"));
        assertEquals(List.of("false", "vanillaWhitelist", "modWhitelist"), FakePlayerItemSortCommand.settingValues("whitelistMode"));
        assertEquals(List.of("false", "true", "opall"), FakePlayerItemSortCommand.settingValues("inventoryRebuild"));
        //#if MC == 26.3
        assertEquals(List.of("0", "1", "2"), FakePlayerItemSortCommand.settingValues("cpuThreads"));
        //#else
        //$$ assertEquals(List.of("0", "1", "2"), FakePlayerItemSortCommand.settingValues("cpuThreads"));
        //#endif
        assertEquals(List.of("4", "8", "16"), FakePlayerItemSortCommand.settingValues("speed"));
        assertEquals(List.of(), FakePlayerItemSortCommand.settingValues("notASetting"));
    }

    @Test
    void sorterManagementCommandsAreRegisteredOnTheBaselineAnd26_3Port() {
        //#if MC == 1.21.1 || MC == 26.3
        assertTrue(FakePlayerItemSortCommand.settingKeys().contains("dashboard"));
        assertTrue(FakePlayerItemSortCommand.settingKeys().contains("cpuThreads"));
        assertEquals(List.of("whitelistMode", "quickShulker", "targetLanguage", "summonNotices", "shulkerRestock",
                "cleanOpenedTarget", "inventoryRebuild", "dashboard", "cpuThreads", "speed"),
                FakePlayerItemSortCommand.settingKeys());
        //#else
        //$$ assertFalse(FakePlayerItemSortCommand.settingKeys().contains("dashboard"));
        //$$ assertFalse(FakePlayerItemSortCommand.settingKeys().contains("cpuThreads"));
        //#endif
    }

    @Test
    void serverLanguageFallbackSelectionFormatsArguments() {
        String key = "carpet.fga.fake_player_item_sort.setting_set";
        assertEquals("分类设置 cleanOpenedTarget 已设置为 false",
                FGAText.formatForLanguage("zh_cn", key, "cleanOpenedTarget", "false"));
        assertEquals("Sorter setting cleanOpenedTarget set to false.",
                FGAText.formatForLanguage("en_us", key, "cleanOpenedTarget", "false"));
        assertEquals("分類設定 cleanOpenedTarget 已設定為 false",
                FGAText.formatForLanguage("zh_tw", key, "cleanOpenedTarget", "false"));
    }

    @Test
    void sorterFeedbackKeysExistInEverySupportedLanguage() {
        for (String language : List.of("en_us", "zh_cn", "zh_tw")) {
            assertTrue(FGATranslations.getTranslations(language)
                    .containsKey("carpet.fga.fake_player_item_sort.setting_set"), language);
            assertTrue(FGATranslations.getTranslations(language)
                    .containsKey("carpet.fga.fake_player_item_sort.notice_restock_failed"), language);
            for (String key : List.of("setup_welcome", "setup_title", "setup_language", "setup_progress",
                    "setup_summon_notices", "setup_summon_notices_note", "setup_speed_note", "setup_cpu_custom",
                    "stock_title", "stock_line", "stock_exported", "permission_saved", "rebuild_stopped")) {
                assertTrue(FGATranslations.getTranslations(language)
                        .containsKey("carpet.fga.fake_player_item_sort." + key), language + ": " + key);
            }
        }
    }

    @Test
    void refactoredSetupAndAdministrationAreOnlyEnabledForThe26_3Baseline() {
        //#if MC == 26.3
        assertEquals(11, FakePlayerItemSortConfig.setupFields().size());
        assertTrue(FakePlayerItemSortConfig.permissionCommands().containsAll(
                List.of("all", "help", "setup", "stock", "permission", "restart", "sort", "summonNotices")));
        //#else
        //$$ assertEquals(0, FakePlayerItemSortConfig.setupFields().size());
        //$$ assertFalse(FakePlayerItemSortConfig.permissionCommands().contains("stock"));
        //#endif
    }

    @Test
    void refactored26_3CommandTreeRetainsExistingAndNewCommands() {
        //#if MC == 26.3
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var root = FakePlayerItemSortCommand.root("playersort").build();
        // The root node compiles raw sources; it must retain its original layout.
        if (!FakePlayerItemSortCommand.usesGroupedSettings()) {
            assertTrue(root.getChild("set") == null);
            for (String command : List.of("mode", "setting", "format", "workers", "dashboard")) {
                assertTrue(root.getChild(command) != null, "missing baseline command: " + command);
            }
            assertTrue(root.getChild("whitelist").getChild("mode") != null);
            return;
        }
        for (String command : List.of("help", "status", "set", "whitelist", "name", "setup", "language", "stock", "permission")) {
            assertTrue(root.getChild(command) != null, "missing /fga playersort " + command);
        }
        for (String command : List.of("mode", "setting", "prefix", "quickShulker", "autoCraft", "summonNotices",
                "cleanOpenedTarget", "speed", "cpu", "dashboard", "format", "workers")) {
            assertTrue(root.getChild(command) == null, "setting must not be exposed at root: " + command);
        }
        var settings = root.getChild("set");
        for (String command : List.of("language", "mode", "summonNotices", "prefix", "quickShulker", "autoCraft",
                "whitelistMode", "cleanOpenedTarget", "speed", "cpu", "dashboard", "format", "workers")) {
            assertTrue(settings.getChild(command) != null, "missing /fga playersort set " + command);
        }
        assertEquals(root.getChild("language").getChildren().stream().map(node -> node.getName()).toList(),
                settings.getChild("language").getChildren().stream().map(node -> node.getName()).toList());
        assertTrue(root.getChild("whitelist").getChild("mode") == null);
        assertTrue(settings.getChild("speed").getChild("ticks") != null, "speed must accept a direct numeric argument");
        assertTrue(settings.getChild("speed").getChild("custom") == null, "speed must not require the custom literal");
        assertTrue(settings.getChild("cpu").getChild("custom") != null, "cpu must expose a custom worker-count option");
        var playerSort = FakePlayerItemSortCommand.playerSort().build();
        for (String command : List.of("continuous", "stop", "restart")) {
            assertTrue(playerSort.getChild(command) != null, "missing /player <fake> bot_sort " + command);
        }
        //#else
        //$$ assertFalse(FakePlayerItemSortConfig.permissionCommands().contains("stock"));
        //#endif
    }
}
