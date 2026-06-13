package carpet.fga;

import java.util.HashMap;
import java.util.Map;

/**
 * 提供 Carpet FGA Addition 的分类名和规则名翻译。
 */
public class FGATranslations {

    public static Map<String, String> getTranslations(String lang) {
        Map<String, String> translations = new HashMap<>();

        // FGA 分类名翻译（会出现在 /carpet 的可点击筛选列表中）
        translations.put("carpet.category.FGA", "FGA");

        // 规则显示名翻译
        translations.put("carpet.rule.fakePlayerNameLength.name", "假人名字最大长度");

        // 规则描述翻译（覆盖 @Rule 中的 desc）
        translations.put("carpet.rule.fakePlayerNameLength.desc",
            "设置假人玩家名字的最大字符长度（1-128），默认 128。客户端需同时安装此模组以支持超过 16 字符的名字。");

        return translations;
    }
}
