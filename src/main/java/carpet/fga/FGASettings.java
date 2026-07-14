package carpet.fga;

import carpet.api.settings.Validator;
import carpet.api.settings.CarpetRule;
import carpet.settings.Rule;
import carpet.utils.Messenger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

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
     * 客户端通过兼容网络别名显示超过 16 字符的名字，无需安装本模组。
     */
    @Rule(
        desc = "假人名字最大长度，-1=不修改并沿用原版限制，设为 1-128 则覆盖，长名字会以兼容别名发送给客户端",
        category = {FGA, FEATURE},
        options = {"-1", "16", "32", "64", "128"},
        strict = false,
        validate = FGASettings.NameLengthValidator.class
    )
    public static int fakePlayerNameLength = -1;

    @Rule(
        desc = "启用假人范围控制命令，可进行区域放置、右键和破坏",
        category = {FGA, FEATURE}
    )
    public static boolean fakePlayerRangeControl = false;

    @Rule(
        desc = "Allows unquoted command arguments to contain Unicode characters",
        category = {FGA, FEATURE}
    )
    public static boolean unicodeArgumentsSupport = false;

    @Rule(
        desc = "Restores the bee collision box used before Minecraft 26.2",
        category = {FGA, FEATURE},
        condition = FGASettings.Minecraft26_2OrNewerCondition.class
    )
    public static boolean restorePre26BeeCollisionBox = false;

    public static class Minecraft26_2OrNewerCondition implements carpet.api.settings.Rule.Condition {
        @Override
        public boolean shouldRegister() {
            //#if MC >= 26.2
            //$$ return true;
            //#else
            return false;
            //#endif
        }
    }

    @Rule(
        desc = "自定义去除僵尸猪灵的指定掉落物",
        category = {FGA, FEATURE},
        options = {"false", "goldEquipment", "rottenFlesh", "all"}
    )
    public static String zombifiedPiglinDropReduction = "false";

    private static final Set<ResourceLocation> VANILLA_PIGLIN_BARTER_ITEMS = Set.of(
            ResourceLocation.withDefaultNamespace("enchanted_book"),
            ResourceLocation.withDefaultNamespace("iron_boots"),
            ResourceLocation.withDefaultNamespace("potion"),
            ResourceLocation.withDefaultNamespace("splash_potion"),
            ResourceLocation.withDefaultNamespace("iron_nugget"),
            ResourceLocation.withDefaultNamespace("ender_pearl"),
            ResourceLocation.withDefaultNamespace("string"),
            ResourceLocation.withDefaultNamespace("quartz"),
            ResourceLocation.withDefaultNamespace("obsidian"),
            ResourceLocation.withDefaultNamespace("crying_obsidian"),
            ResourceLocation.withDefaultNamespace("fire_charge"),
            ResourceLocation.withDefaultNamespace("leather"),
            ResourceLocation.withDefaultNamespace("soul_sand"),
            ResourceLocation.withDefaultNamespace("nether_brick"),
            ResourceLocation.withDefaultNamespace("spectral_arrow"),
            ResourceLocation.withDefaultNamespace("gravel"),
            ResourceLocation.withDefaultNamespace("blackstone")
    );

    public static boolean blocksZombifiedPiglinGoldEquipment() {
        return zombifiedPiglinDropReduction.equals("goldEquipment")
                || zombifiedPiglinDropReduction.equals("all");
    }

    public static boolean blocksZombifiedPiglinRottenFlesh() {
        return zombifiedPiglinDropReduction.equals("rottenFlesh")
                || zombifiedPiglinDropReduction.equals("all");
    }

    @Rule(
        desc = "自定义去除猪灵交易返回的指定物品",
        category = {FGA, FEATURE},
        options = {"false", "[ironBoots]", "[potions]", "[ironBoots,potions]"},
        strict = false,
        validate = FGASettings.PiglinBarterExclusionsValidator.class
    )
    public static String piglinBarterItemExclusions = "false";

    public static Set<ResourceLocation> getPiglinBarterItemExclusions() {
        try {
            return parsePiglinBarterItemExclusions(piglinBarterItemExclusions);
        } catch (IllegalArgumentException ignored) {
            return Set.of();
        }
    }

    private static Set<ResourceLocation> parsePiglinBarterItemExclusions(String value) {
        if (value.equalsIgnoreCase("false")) {
            return Set.of();
        }
        if (value.length() < 3 || value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']') {
            throw new IllegalArgumentException("格式必须为 false 或 [物品ID,物品ID]");
        }

        Set<ResourceLocation> exclusions = new HashSet<>();
        String[] entries = value.substring(1, value.length() - 1).split(",", -1);
        for (String rawEntry : entries) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                throw new IllegalArgumentException("物品列表中不能包含空项");
            }
            if (entry.equalsIgnoreCase("ironBoots")) {
                exclusions.add(ResourceLocation.withDefaultNamespace("iron_boots"));
                continue;
            }
            if (entry.equalsIgnoreCase("potions")) {
                exclusions.add(ResourceLocation.withDefaultNamespace("potion"));
                exclusions.add(ResourceLocation.withDefaultNamespace("splash_potion"));
                exclusions.add(ResourceLocation.withDefaultNamespace("lingering_potion"));
                continue;
            }

            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id == null) {
                throw new IllegalArgumentException("无效的物品 ID：" + entry);
            }
            if (!BuiltInRegistries.ITEM.containsKey(id)) {
                throw new IllegalArgumentException("未注册的物品 ID：" + id);
            }
            exclusions.add(id);
        }
        return Set.copyOf(exclusions);
    }

    public static class PiglinBarterExclusionsValidator extends Validator<String> {
        @Override
        public String validate(CommandSourceStack source, CarpetRule<String> currentRule,
                               String newValue, String userInput) {
            try {
                Set<ResourceLocation> exclusions = parsePiglinBarterItemExclusions(newValue);
                if (exclusions.containsAll(VANILLA_PIGLIN_BARTER_ITEMS)) {
                    Messenger.m(source, "r 不能排除全部原版猪灵交易物品");
                    return null;
                }
                return newValue;
            } catch (IllegalArgumentException exception) {
                Messenger.m(source, "r " + exception.getMessage());
                return null;
            }
        }
    }

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
