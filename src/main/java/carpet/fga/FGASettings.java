package carpet.fga;

import carpet.api.settings.Validator;
import carpet.api.settings.CarpetRule;
import carpet.settings.Rule;
import carpet.utils.Messenger;
import net.minecraft.commands.CommandSourceStack;

import static carpet.api.settings.RuleCategory.FEATURE;

/**
 * Carpet FGA Addition 规则定义。
 * 所有规则通过 /carpet 命令管理，归属 FGA 分类。
 */
public class FGASettings {

    /** FGA 自定义分类，会出现在 /carpet 菜单中作为可点击选项 */
    public static final String FGA = "FGA";

    /**
     * 假人玩家名字的最大长度（字符数）。
     * -1 表示不做修改，使用原版/carpet 默认行为（服务器 16，单人 40）。
     * 设为 1-128 之间的值则覆盖原版限制。
     * 客户端需同时安装此模组以支持超过 16 字符的名字。
     */
    @Rule(
        desc = "假人名字最大长度。-1=不修改，沿用原版限制。设为 1-128 则覆盖。客户端需同时安装此模组。",
        category = {FGA, FEATURE},
        options = {"-1", "16", "32", "64", "128"},
        strict = false,
        validate = FGASettings.NameLengthValidator.class
    )
    public static int fakePlayerNameLength = -1;

    /**
     * 验证假人名字长度值的合法性。
     * -1 表示不做修改，1-128 为有效长度。
     */
    public static class NameLengthValidator extends Validator<Integer> {
        @Override
        public Integer validate(CommandSourceStack source, CarpetRule<Integer> currentRule, Integer newValue, String userInput) {
            if (newValue == -1) {
                return newValue; // -1 表示不修改
            }
            if (newValue < 1) {
                Messenger.m(source, "r 假人名字长度不能小于 1（-1 除外）");
                return null;
            }
            if (newValue > 128) {
                Messenger.m(source, "r 假人名字长度不能超过 128");
                return null;
            }
            return newValue;
        }
    }
}
