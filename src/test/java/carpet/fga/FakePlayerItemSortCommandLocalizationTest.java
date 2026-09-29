package carpet.fga;

import org.junit.jupiter.api.Test;

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
        assertEquals(List.of("false", "true"), FakePlayerItemSortCommand.settingValues("dashboard"));
        assertEquals(List.of("english", "chinese", "custom"), FakePlayerItemSortCommand.settingValues("targetLanguage"));
        assertEquals(List.of("false", "vanillaWhitelist", "modWhitelist"), FakePlayerItemSortCommand.settingValues("whitelistMode"));
        assertEquals(List.of("false", "true", "opall"), FakePlayerItemSortCommand.settingValues("inventoryRebuild"));
        assertEquals(List.of("0", "1", "2"), FakePlayerItemSortCommand.settingValues("cpuThreads"));
        assertEquals(List.of("4", "8", "16"), FakePlayerItemSortCommand.settingValues("speed"));
        assertEquals(List.of(), FakePlayerItemSortCommand.settingValues("notASetting"));
    }

    @Test
    void sorterManagementCommandsAreRegisteredOnTheBaselineAnd26_3Port() {
        //#if MC == 1.21.1 || MC == 26.3
        assertTrue(FakePlayerItemSortCommand.settingKeys().contains("dashboard"));
        assertTrue(FakePlayerItemSortCommand.settingKeys().contains("cpuThreads"));
        assertEquals(List.of("whitelistMode", "quickShulker", "targetLanguage", "shulkerRestock",
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
        }
    }
}
