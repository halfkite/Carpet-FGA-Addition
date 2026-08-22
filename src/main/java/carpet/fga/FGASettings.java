package carpet.fga;

//#if MC >= 1.19
import carpet.api.settings.Validator;
import carpet.api.settings.CarpetRule;
//#else
//$$ import carpet.settings.Validator;
//$$ import carpet.settings.ParsedRule;
//#endif
import carpet.settings.Rule;
import carpet.utils.Messenger;
import net.minecraft.commands.CommandSourceStack;
//#if MC >= 1.19.3
import net.minecraft.core.registries.BuiltInRegistries;
//#else
//$$ import net.minecraft.core.Registry;
//#endif
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
//#if MC >= 1.21.2
//$$ import net.minecraft.world.entity.EntitySpawnReason;
//#endif
//#if MC >= 26.2
//$$ import net.minecraft.world.entity.EntitySpawnRequest;
//#endif
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

//#if MC >= 1.19
import static carpet.api.settings.RuleCategory.FEATURE;
//#else
//$$ import static carpet.settings.RuleCategory.FEATURE;
//#endif

/**
 * Carpet FGA Addition 规则定义。
 * 所有规则通过 /carpet 命令管理，归属 FGA 分类。
 */
public class FGASettings {

    /** FGA 自定义分类，会出现在 /carpet 菜单中作为可点击选项 */
    public static final String FGA = "FGA";

    //#if MC >= 1.21 && MC <= 26.2
    @Rule(
        desc = "允许 QuickCraft 客户端通过服务端校验放置投影实体并扣除材料",
        category = {FGA, FEATURE},
        options = {"false", "true"},
        strict = false
    )
    public static boolean quickCraftEasyPlaceEntities = false;
    //#endif

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
        validate = FGASettings.NameLengthValidator.class,
        condition = FGASettings.Minecraft1_18OrNewerCondition.class
    )
    public static int fakePlayerNameLength = -1;

    public static class Minecraft1_18OrNewerCondition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            //#if MC >= 1.19
            return true;
            //#else
            //$$ return false;
            //#endif
        }
    }

    @Rule(
        desc = "启用假人范围控制命令，可进行区域放置、右键和破坏",
        category = {FGA, FEATURE}
    )
    public static boolean fakePlayerRangeControl = false;

    @Rule(
        desc = "Regenerates destroyed vanilla End gateways without changing surrounding blocks",
        category = {FGA, FEATURE},
        condition = FGASettings.Minecraft1_16OrNewerCondition.class
    )
    public static boolean endGatewayRegeneration = false;

    @Rule(
        desc = "Controls wandering trader despawning: false, true, or controlled",
        category = {FGA, FEATURE},
        options = {"false", "true", "controlled"},
        strict = false,
        condition = Minecraft1_16OrNewerCondition.class
    )
    public static String wanderingTraderNoDespawn = "false";

    @Rule(
        desc = "Asynchronously preloads fake-player profiles before spawning",
        category = {FGA, FEATURE},
        options = {"false", "always", "adaptive"},
        validate = FGASettings.FakePlayerProfilePreloadValidator.class,
        condition = FGASettings.Minecraft1_20_1OrNewerCondition.class
    )
    public static String fakePlayerProfilePreload = "false";

    public static class FakePlayerProfilePreloadValidator extends
            //#if MC >= 1.19
            Validator<String> {
            //#else
            //$$ Validator<String> {
            //#endif
        @Override
        public String validate(CommandSourceStack source,
                               //#if MC >= 1.19
                               CarpetRule<String> currentRule,
                               //#else
                               //$$ ParsedRule<String> currentRule,
                               //#endif
                               String newValue, String userInput) {
            if (!Set.of("false", "always", "adaptive").contains(newValue)) {
                Messenger.m(source, "r fakePlayerProfilePreload must be false, always, or adaptive");
                return null;
            }
            //#if MC == 1.20.1 || MC >= 1.21.1
            FakePlayerProfilePreloadManager.clearAll();
            //#endif
            return newValue;
        }
    }

    public static class Minecraft1_21_1OnlyCondition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            //#if MC >= 1.21.1
            return true;
            //#else
            //$$ return false;
            //#endif
        }
    }

    public static class Minecraft1_21_1OrNewerCondition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            //#if MC >= 1.21.1
            return true;
            //#else
            //$$ return false;
            //#endif
        }
    }

    public static class Minecraft1_16OrNewerCondition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            return true;
        }
    }

    public static class Minecraft1_21OrNewerCondition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            //#if MC >= 1.21 && MC <= 26.2
            return true;
            //#else
            //$$ return false;
            //#endif
        }
    }

    public static class WoodStonecuttingRecipesCondition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            //#if MC >= 1.20.1 && MC <= 26.2
            return true;
            //#else
            //$$ return false;
            //#endif
        }
    }

    public static class DeepslateStonecuttingRecipesCondition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            //#if MC >= 1.20.1 && MC <= 1.21.11
            return true;
            //#else
            //$$ return false;
            //#endif
        }
    }

    @Rule(
        desc = "Makes deepslate behave in the stonecutter like it does in 26.1+",
        category = {FGA, FEATURE},
        options = {"false", "true"},
        condition = FGASettings.DeepslateStonecuttingRecipesCondition.class
    )
    public static boolean deepslateStonecuttingRecipes = false;

    //#if MC >= 1.20.1 && MC <= 26.2
    @Rule(
        desc = "Removes the anvil prior-work penalty and the 40-level too-expensive limit",
        category = {FGA, FEATURE},
        options = {"false", "true"},
        condition = FGASettings.Minecraft1_20_1OrNewerCondition.class
    )
    public static boolean anvilNoPriorWorkPenalty = false;
    //#endif

    //#if MC >= 1.20.1 && MC <= 26.2
    public static final String EXPERIENCE_LEVEL_COST_29_30 = "29-30";
    public static final String EXPERIENCE_LEVEL_COST_0_1 = "0-1";
    private static final String LEGACY_EXPERIENCE_LEVEL_COST_29_30 = "30级后每级升级消耗经验与29到30一样";
    private static final String LEGACY_EXPERIENCE_LEVEL_COST_0_1 = "每级升级消耗经验与0到1一样";

    @Rule(
        desc = "Flattens experience required for level upgrades",
        category = {FGA, FEATURE},
        options = {"false", "29-30", "0-1"},
        strict = false,
        validate = FGASettings.ExperienceLevelCostValidator.class,
        condition = FGASettings.Minecraft1_20_1OrNewerCondition.class
    )
    public static String experienceLevelCost = "false";

    public static boolean usesExperienceLevelCost29To30() {
        return EXPERIENCE_LEVEL_COST_29_30.equals(experienceLevelCost)
                || LEGACY_EXPERIENCE_LEVEL_COST_29_30.equals(experienceLevelCost);
    }

    public static class Minecraft1_20_1Or1_21_1Condition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            //#if MC == 1.20.1
            //$$ return true;
            //#elseif MC == 1.21.1
            return true;
            //#else
            //$$ return false;
            //#endif
        }
    }

    public static boolean usesExperienceLevelCost0To1() {
        return EXPERIENCE_LEVEL_COST_0_1.equals(experienceLevelCost)
                || LEGACY_EXPERIENCE_LEVEL_COST_0_1.equals(experienceLevelCost);
    }

    public static class ExperienceLevelCostValidator extends Validator<String> {
        @Override
        public String validate(CommandSourceStack source,
                               CarpetRule<String> currentRule,
                               String newValue,
                               String userInput) {
            String value = newValue == null ? "" : newValue.trim();
            if (value.length() >= 2) {
                char first = value.charAt(0);
                char last = value.charAt(value.length() - 1);
                if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                    value = value.substring(1, value.length() - 1).trim();
                }
            }
            if (value.equalsIgnoreCase("false")) return "false";
            if (value.equals(EXPERIENCE_LEVEL_COST_29_30)
                    || value.equals("30")
                    || value.equals(LEGACY_EXPERIENCE_LEVEL_COST_29_30)) {
                return EXPERIENCE_LEVEL_COST_29_30;
            }
            if (value.equals(EXPERIENCE_LEVEL_COST_0_1)
                    || value.equals("1")
                    || value.equals(LEGACY_EXPERIENCE_LEVEL_COST_0_1)) {
                return EXPERIENCE_LEVEL_COST_0_1;
            }
            Messenger.m(source, "r experienceLevelCost must be false, 29-30, or 0-1");
            return null;
        }
    }
    //#endif

    //#if MC >= 1.20.1 && MC <= 26.2
    @Rule(
        desc = "Allows wood products to be crafted in the stonecutter",
        category = {FGA, FEATURE},
        options = {"false", "true"},
        condition = FGASettings.WoodStonecuttingRecipesCondition.class
    )
    public static boolean woodStonecuttingRecipes = false;
    //#endif

    //#if MC >= 1.20.1 && MC <= 26.2
    @Rule(
        desc = "Prevents villagers from crafting wheat into bread",
        category = {FGA, FEATURE},
        options = {"false", "true"}
    )
    public static boolean villagerDoNotCraftBread = false;

    @Rule(
        desc = "Lets villagers finish profession upgrades while trading",
        category = {FGA, FEATURE},
        options = {"false", "true"}
    )
    public static boolean villagerUpgradeWhileTrading = false;
    //#endif

    //#if MC >= 1.20.1 && MC <= 26.2
    @Rule(
        desc = "Controls per-player chunk loading distance commands",
        category = {FGA, FEATURE},
        options = {"false", "true", "ops", "0", "1", "2", "3", "4"},
        strict = false,
        validate = FGASettings.PlayerLoadDistanceValidator.class,
        condition = FGASettings.Minecraft1_20_1OrNewerCondition.class
    )
    public static String playerLoadDistance = "false";

    public static class PlayerLoadDistanceValidator extends Validator<String> {
        @Override
        public String validate(CommandSourceStack source,
                //#if MC >= 1.19
                CarpetRule<String> currentRule,
                //#else
                //$$ ParsedRule<String> currentRule,
                //#endif
                String newValue, String userInput) {
            String value = newValue == null ? "" : newValue.trim().toLowerCase(java.util.Locale.ROOT);
            if (Set.of("false", "true", "ops", "0", "1", "2", "3", "4").contains(value)) return value;
            Messenger.m(source, "r playerLoadDistance must be false, true, ops, or 0-4");
            return null;
        }
    }
    //#endif

    //#if MC >= 1.21 && MC <= 26.2
    @Rule(
        desc = "Makes each matching trial-spawner participant count as multiple players",
        category = {FGA, FEATURE},
        options = {"1", "10", "50", "100", "1000", "10000"},
        strict = false,
        validate = FGASettings.TrialSpawnerPlayerMultiplierValidator.class,
        condition = FGASettings.Minecraft1_21OrNewerCondition.class
    )
    public static int trialSpawnerPlayerMultiplier = 100;

    @Rule(
        desc = "Selects players affected by the trial-spawner multiplier: false, true, or a name prefix",
        category = {FGA, FEATURE},
        options = {"false", "true", "bot_"},
        strict = false,
        validate = FGASettings.TrialSpawnerPlayerFilterValidator.class,
        condition = FGASettings.Minecraft1_21OrNewerCondition.class
    )
    public static String trialSpawnerPlayerFilter = "false";

    @Rule(
        desc = "Enables and controls the one-shot /trialStop stop-and-refresh command",
        category = {FGA, FEATURE},
        options = {"false", "true", "ops", "0", "1", "2", "3", "4"},
        strict = false,
        validate = FGASettings.TrialStopCommandPermissionValidator.class,
        condition = FGASettings.Minecraft1_21OrNewerCondition.class
    )
    public static String trialStopCommandPermission = "false";

    public static class TrialSpawnerPlayerMultiplierValidator extends Validator<Integer> {
        @Override
        public Integer validate(CommandSourceStack source, CarpetRule<Integer> currentRule,
                                Integer newValue, String userInput) {
            if (newValue != null && newValue >= 1 && newValue <= 10000) return newValue;
            Messenger.m(source, "r trialSpawnerPlayerMultiplier must be between 1 and 10000");
            return null;
        }
    }

    public static class TrialSpawnerPlayerFilterValidator extends Validator<String> {
        @Override
        public String validate(CommandSourceStack source, CarpetRule<String> currentRule,
                               String newValue, String userInput) {
            String value = newValue == null ? "" : newValue;
            if (value.length() >= 2) {
                char first = value.charAt(0);
                char last = value.charAt(value.length() - 1);
                if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                    value = value.substring(1, value.length() - 1);
                }
            }
            if (value.trim().isEmpty()) {
                Messenger.m(source, "r trialSpawnerPlayerFilter must be false, true, or a non-blank player-name prefix");
                return null;
            }
            if (value.equalsIgnoreCase("false")) return "false";
            if (value.equalsIgnoreCase("true")) return "true";
            return value;
        }
    }

    public static class TrialStopCommandPermissionValidator extends Validator<String> {
        @Override
        public String validate(CommandSourceStack source, CarpetRule<String> currentRule,
                               String newValue, String userInput) {
            String value = newValue == null ? "" : newValue.trim().toLowerCase(java.util.Locale.ROOT);
            if (Set.of("false", "true", "ops", "0", "1", "2", "3", "4").contains(value)) return value;
            Messenger.m(source, "r trialStopCommandPermission must be false, true, ops, or 0-4");
            return null;
        }
    }
    //#endif

    @Rule(
        desc = "Prevents baby mobs from growing: false, true, or an exact custom name",
        category = {FGA, FEATURE},
        options = {"false", "true", "mini"},
        strict = false,
        validate = FGASettings.BabyMobNoGrowthValidator.class,
        condition = FGASettings.BabyMobNoGrowthBaselineCondition.class
    )
    public static String babyMobNoGrowth = "false";

    public static class BabyMobNoGrowthBaselineCondition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            //#if MC >= 1.20.1 && MC <= 26.2
            return true;
            //#else
            //$$ return false;
            //#endif
        }
    }

    public static class BabyMobNoGrowthValidator extends
            //#if MC >= 1.19
            Validator<String> {
            //#else
            //$$ Validator<String> {
            //#endif
        @Override
        public String validate(CommandSourceStack source,
                               //#if MC >= 1.19
                               CarpetRule<String> currentRule,
                               //#else
                               //$$ ParsedRule<String> currentRule,
                               //#endif
                               String newValue, String userInput) {
            String unquoted = newValue == null ? "" : newValue;
            if (unquoted.length() >= 2) {
                char first = unquoted.charAt(0);
                char last = unquoted.charAt(unquoted.length() - 1);
                if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                    unquoted = unquoted.substring(1, unquoted.length() - 1);
                }
            }
            if (unquoted.trim().isEmpty()) {
                Messenger.m(source, "r babyMobNoGrowth must be false, true, or a non-blank custom name");
                return null;
            }
            return unquoted;
        }
    }

    //#if MC >= 1.20.1 && MC <= 26.2
    @Rule(
        desc = "Allows configured plants to survive without their normal support restrictions",
        category = {FGA, FEATURE},
        options = {"false", "true", "[]"},
        strict = false,
        validate = FGASettings.ResilientPlantsValidator.class,
        condition = FGASettings.Minecraft1_20_1OrNewerCondition.class
    )
    public static String resilientPlants = "false";

    public static class ResilientPlantsValidator extends Validator<String> {
        @Override
        public String validate(CommandSourceStack source,
                               //#if MC >= 1.19
                               CarpetRule<String> currentRule,
                               //#else
                               //$$ ParsedRule<String> currentRule,
                               //#endif
                               String newValue, String userInput) {
            try {
                String normalized = ResilientPlants.validate(newValue);
                ResilientPlants.setConfiguredBlocks(normalized);
                return normalized;
            } catch (IllegalArgumentException exception) {
                Messenger.m(source, "r " + exception.getMessage());
                return null;
            }
        }
    }

    @Rule(
        desc = "Allows comparators to read container signals through configured blocks",
        category = {FGA, FEATURE},
        options = {"false", "[chain]", "[piston]", "[chain,piston]"},
        strict = false,
        validate = FGASettings.ComparatorThroughBlocksValidator.class,
        condition = FGASettings.Minecraft1_20_1OrNewerCondition.class
    )
    public static String comparatorThroughBlocks = "false";

    public static class ComparatorThroughBlocksValidator extends Validator<String> {
        @Override
        public String validate(CommandSourceStack source,
                               //#if MC >= 1.19
                               CarpetRule<String> currentRule,
                               //#else
                               //$$ ParsedRule<String> currentRule,
                               //#endif
                               String newValue, String userInput) {
            try {
                String normalized = ComparatorThroughBlocks.validate(newValue);
                ComparatorThroughBlocks.setConfiguredBlocks(normalized);
                return normalized;
            } catch (IllegalArgumentException exception) {
                Messenger.m(source, "r " + exception.getMessage());
                return null;
            }
        }
    }
    //#endif

    //#if MC >= 1.20.1 && MC <= 26.2
    @Rule(
        desc = "Shulkers killed by shulker bullets always respawn a new shulker at the death spot, like Bedrock Edition",
        category = {FGA, FEATURE},
        options = {"false", "true"},
        condition = FGASettings.Minecraft1_20_1OrNewerCondition.class
    )
    public static boolean shulkerBedrockDuplication = false;

    @Rule(
        desc = "Shulker shell drops follow Bedrock Edition looting: a flat 50% chance to drop, dropping 1 to 1+Looting shells uniformly",
        category = {FGA, FEATURE},
        options = {"false", "true"},
        condition = FGASettings.Minecraft1_20_1OrNewerCondition.class
    )
    public static boolean shulkerBedrockLooting = false;

    @Rule(
        desc = "Allows shulkers to attack armor stands within targeting range",
        category = {FGA, FEATURE},
        options = {"false", "true", "pumpkin"},
        strict = false,
        validate = FGASettings.ShulkerAttackArmorStandValidator.class,
        condition = FGASettings.Minecraft1_20_1OrNewerCondition.class
    )
    public static String shulkerAttackArmorStand = "false";

    public static class ShulkerAttackArmorStandValidator extends
            //#if MC >= 1.19
            Validator<String> {
            //#else
            //$$ Validator<String> {
            //#endif
        @Override
        public String validate(CommandSourceStack source,
                               //#if MC >= 1.19
                               CarpetRule<String> currentRule,
                               //#else
                               //$$ ParsedRule<String> currentRule,
                               //#endif
                               String newValue, String userInput) {
            // Legacy option names from earlier builds normalize to pumpkin so saved configs keep working.
            if ("pumpkin".equals(newValue) || "onlyWithPumpkinHead".equals(newValue) || "onlyWithShulkerShell".equals(newValue)) {
                return "pumpkin";
            }
            if ("false".equals(newValue) || "true".equals(newValue)) {
                return newValue;
            }
            Messenger.m(source, "r shulkerAttackArmorStand must be false, true, or pumpkin");
            return null;
        }
    }
    //#endif

    @Rule(
        desc = "Allows unquoted command arguments to contain Unicode characters",
        category = {FGA, FEATURE}
    )
    public static boolean fgaUnicodeArgumentsSupport = false;

    /** Category used by the inventory sorter rules so they can be filtered together in /carpet. */
    public static final String FAKE_PLAYER_ITEM_SORT = "假人分类";

    //#if MC >= 1.16.5
    @Rule(
        desc = "Gives every player all registered recipes on login, with a one-minute per-player cooldown, without discarding saved recipe unlock data",
        category = {FGA, FEATURE}
    )
    public static boolean recipeBookAlwaysUnlocked = false;

    // Kept for binary/source compatibility with the disabled progress optimizer; it is not a Carpet rule.
    public static String inventoryAdvancementOptimization = "false";

    @Rule(
        desc = "Shows player health at the right side of the player list: true, false, or nofake",
        category = {FGA, FEATURE},
        options = {"true", "false", "nofake"},
        strict = false
    )
    public static String playerHealthDisplay = "false";

    @Rule(
        desc = "Removes item frames from server tick scheduling and validates them when support blocks change",
        category = {FGA, FEATURE},
        condition = FGASettings.Minecraft1_20_1Or1_21_1Condition.class
    )
    public static boolean itemFrameBlockification = false;

    @Rule(
        desc = "Lets players riding normal minecarts use firework rockets for configurable speed boosts",
        category = {FGA, FEATURE},
        condition = FGASettings.Minecraft1_20_1Or1_21_1Condition.class
    )
    public static boolean fireworkMinecartBoost = false;

    @Rule(
        desc = "Lets normal minecarts form persistent chain-linked trains",
        category = {FGA, FEATURE},
        condition = FGASettings.Minecraft1_20_1Or1_21_1Condition.class
    )
    public static boolean chainMinecartBinding = false;

    @Rule(
        desc = "Controls access to minecart feature configuration commands",
        category = {FGA, FEATURE},
        options = {"false", "true", "ops", "0", "1", "2", "3", "4"},
        strict = false,
        condition = FGASettings.Minecraft1_20_1Or1_21_1Condition.class
    )
    public static String minecartFeatureCommandPermission = "false";

    @Rule(
        desc = "Stops horizontal vehicle movement when the controlling player dismounts",
        category = {FGA, FEATURE},
        options = {"false", "minecart", "boat", "all", "custom"},
        strict = false,
        condition = FGASettings.Minecraft1_16OrNewerCondition.class
    )
    public static String vehicleStopOnDismount = "false";

    @Rule(
        desc = "Makes newly generated chunks void while retaining biome and structure-location data",
        category = {FGA, FEATURE}
    )
    public static boolean voidWorldGeneration = false;

    //#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.2
    @Rule(
        desc = "Controls access to terrain regeneration and clearing commands",
        category = {FGA, FEATURE},
        options = {"false", "true", "ops", "0", "1", "2", "3", "4"},
        strict = false,
        condition = FGASettings.Minecraft1_20_1OrNewerCondition.class
    )
    public static String terrainRegenerationCommandPermission = "ops";
    //#endif

    @Rule(
        desc = "Crafts full single-item shulker boxes through matching ordinary crafting recipes",
        category = {FGA, FEATURE}
    )
    public static boolean fullShulkerBoxCrafting = false;

    //#if MC >= 1.20.1 && MC <= 26.2
    @Rule(desc = "Enables fake-player inventory sorting; mode and sorter options are managed by /fakePlayerItemSort", category = {FGA, FAKE_PLAYER_ITEM_SORT},
            options = {"false", "true"}, strict = false, condition = Minecraft1_20_1OrNewerCondition.class)
    public static boolean fakePlayerItemSort = false;

    public static boolean isFakePlayerItemSortEnabled() {
        return fakePlayerItemSort;
    }
    //#endif
    //#endif

    //#if MC >= 1.20.1 && MC <= 1.21.5
    @Rule(
        desc = "Allows non-OP spectators to use /tp and /teleport on themselves only",
        category = {FGA, FEATURE},
        condition = FGASettings.Minecraft1_20_1OrNewerCondition.class
    )
    public static boolean spectatorFreeTeleport = false;

    @Rule(
        desc = "Controls whether players may use End portals and End gateways: false, true, or control",
        category = {FGA, FEATURE},
        options = {"false", "true", "control"},
        strict = false,
        validate = FGASettings.PlayerTpEndControlValidator.class,
        condition = FGASettings.Minecraft1_20_1OrNewerCondition.class
    )
    public static String PlayerTpEndControl = "false";

    public static class PlayerTpEndControlValidator extends Validator<String> {
        @Override
        public String validate(CommandSourceStack source, CarpetRule<String> currentRule,
                               String newValue, String userInput) {
            String value = newValue == null ? "" : newValue.trim().toLowerCase(java.util.Locale.ROOT);
            if (Set.of("false", "true", "control").contains(value)) return value;
            Messenger.m(source, "r PlayerTpEndControl must be false, true, or control");
            return null;
        }
    }
    //#endif



    @Rule(
        desc = "Restores the bee collision box used before Minecraft 26.2",
        category = {FGA, FEATURE},
        condition = FGASettings.Minecraft26_2OrNewerCondition.class
    )
    public static boolean restorePre26BeeCollisionBox = false;

    //#if MC == 1.20.1 || MC >= 1.21.1
    @Rule(
        desc = "Maps the client-visible ids for the Overworld, Nether, and End without changing server dimensions",
        category = {FGA, FEATURE},
        options = {"[overworld,the_nether,the_end]"},
        strict = false,
        validate = FGASettings.ClientDimensionIdsValidator.class
    )
    public static String clientDimensionIds = ClientDimensionIdMapping.DEFAULT_VALUE;

    public static class ClientDimensionIdsValidator extends Validator<String> {
        @Override
        public String validate(CommandSourceStack source,
                //#if MC >= 1.19
                CarpetRule<String> currentRule,
                //#else
                //$$ ParsedRule<String> currentRule,
                //#endif
                               String newValue, String userInput) {
            try {
                ClientDimensionIdMapping.validateAndApply(newValue, source);
                return newValue;
            } catch (RuntimeException exception) {
                Messenger.m(source, "r " + exception.getMessage());
                return null;
            }
        }
    }
    //#endif

    @Rule(
        desc = "Removes the confirmation warning for server-sent run-command actions",
        category = {FGA, FEATURE},
        condition = FGASettings.Minecraft1_21_8OrNewerCondition.class
    )
    public static boolean removeDialogWarning = false;

    public static class Minecraft1_21_8OrNewerCondition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            //#if MC >= 1.21.8
            //$$ return true;
            //#else
            return false;
            //#endif
        }
    }

    public static class Minecraft26_2OrNewerCondition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            //#if MC >= 26.2
            //$$ return true;
            //#else
            return false;
            //#endif
        }
    }

    @Rule(
        desc = "Allows shift-right-click feeding to give adult villagers breeding willingness and speed up baby growth",
        category = {FGA, FEATURE},
        options = {"false", "true", "only"}
    )
    public static String villagerBreedingAnimalization = "false";

    //#if MC >= 1.20.1
    @Rule(
        desc = "Enables villager performance optimization and controls access to /villagerPerformance",
        category = {FGA, FEATURE},
        options = {"false", "true", "ops", "1", "2", "3", "4"},
        validate = FGASettings.VillagerPerformanceOptimizationValidator.class,
        condition = FGASettings.Minecraft1_20_1OrNewerCondition.class
    )
    public static String villagerPerformanceOptimization = "false";

    public static class VillagerPerformanceOptimizationValidator extends Validator<String> {
        @Override public String validate(CommandSourceStack source,
                //#if MC >= 1.19
                CarpetRule<String> currentRule,
                //#else
                //$$ ParsedRule<String> currentRule,
                //#endif
                               String newValue, String userInput) {
            if (!Set.of("false", "true", "ops", "1", "2", "3", "4").contains(newValue)) {
                Messenger.m(source, "r villagerPerformanceOptimization must be false, true, ops, or 1-4");
                return null;
            }
            VillagerTradeOnlyManager.clear();
            return newValue;
        }
    }
    //#endif

    @Rule(
        desc = "Allows opening hostile mob equipment with an empty-handed shift-right-click",
        category = {FGA, FEATURE}
    )
    public static boolean hostileMobInventoryAccess = false;

    //#if MC >= 1.20.1
    @Rule(
        desc = "Enables configurable ground item entity stack limits and controls access to /droppedItemStackLimit",
        category = {FGA, FEATURE},
        options = {"false", "true", "ops", "0", "1", "2", "3", "4"},
        validate = FGASettings.DroppedItemStackLimitValidator.class,
        condition = FGASettings.Minecraft1_20_1OrNewerCondition.class
    )
    public static String droppedItemStackLimit = "false";

    public static boolean isDroppedItemStackLimitEnabled() {
        return !"false".equals(droppedItemStackLimit);
    }


    public static class DroppedItemStackLimitValidator extends Validator<String> {
        @Override public String validate(CommandSourceStack source,
                CarpetRule<String> currentRule, String newValue, String userInput) {
            if (!Set.of("false", "true", "ops", "0", "1", "2", "3", "4").contains(newValue)) {
                Messenger.m(source, "r droppedItemStackLimit must be false, true, ops, or 0-4");
                return null;
            }
            return newValue;
        }
    }
    //#else
    //$$ @Rule(
    //$$     desc = "Enables configurable ground item entity stack limits",
    //$$     category = {FGA, FEATURE},
    //$$     condition = FGASettings.Minecraft1_21_1Condition.class
    //$$ )
    //$$ public static boolean droppedItemStackLimit = false;

    //$$ public static boolean isDroppedItemStackLimitEnabled() {
    //$$     return droppedItemStackLimit;
    //$$ }
    //#endif

    @Rule(
        desc = "Removes the volume limit from /fill and /fillbiome while retaining vanilla safety checks",
        category = {FGA, FEATURE},
        condition = FGASettings.Minecraft1_21_8Condition.class
    )
    public static boolean unlimitedFillCommands = false;

    @Rule(
        desc = "Changes the horizontal search distance for merging ground item entities; -1 keeps vanilla 0.5 blocks",
        category = {FGA, FEATURE},
        options = {"-1", "0", "0.5", "1", "2", "4", "8", "16"},
        strict = false,
        validate = FGASettings.DroppedItemMergeDistanceValidator.class,
        condition = FGASettings.Minecraft1_21_1Condition.class
    )
    public static double droppedItemMergeDistance = -1.0D;

    private static volatile Set<ResourceLocation> preStackMobTypes = Set.of();

    //#if MC >= 1.20.1 && MC <= 26.2
    @Rule(
        desc = "Enables unified entity-death and block-drop pre-stacking configured by /dropPreStack",
        category = {FGA, FEATURE},
        options = {"false", "true"},
        strict = false,
        condition = FGASettings.Minecraft1_20_1OrNewerCondition.class
    )
    public static boolean preStackDroppedItems = false;
    //#endif

    @Rule(
        desc = "Pre-stacks compatible death drops from selected mob entity types",
        category = {FGA, FEATURE},
        options = {"false", "[zombified_piglin]"},
        strict = false,
        validate = FGASettings.PreStackMobDeathDropsValidator.class,
        condition = FGASettings.HiddenLegacyPreStackCondition.class
    )
    public static String preStackMobDeathDrops = "false";

    @Rule(
        desc = "Sets the legacy three-dimensional range for same-tick selected mob death drop pre-stacking",
        category = {FGA, FEATURE},
        options = {"0", "1", "3", "8", "16"},
        strict = false,
        validate = FGASettings.PreStackMobDeathDropsRangeValidator.class,
        condition = FGASettings.HiddenLegacyPreStackCondition.class
    )
    public static double preStackMobDeathDropsRange = 1.5D;

    /** Retains the old fields for source/config compatibility without registering old Carpet rules. */
    public static class HiddenLegacyPreStackCondition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            return false;
        }
    }

    public static ResourceLocation preStackEntityId(Entity entity) {
        return
                //#if MC >= 1.19.3
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                //#else
                //$$ Registry.ENTITY_TYPE.getKey(entity.getType());
                //#endif
    }

    public static Double preStackEntityRange(Entity entity) {
        ResourceLocation id = preStackEntityId(entity);
        //#if MC >= 1.20.1 && MC <= 26.2
        if (preStackDroppedItems) {
            Double containerConfigured = DropPreStackConfig.containerEntityRange(id);
            if (containerConfigured != null) return containerConfigured;
            Double configured = DropPreStackConfig.entityRange(id);
            if (configured != null) return configured;
        }
        //#endif
        return entity instanceof Mob && preStackMobTypes.contains(id) ? preStackMobDeathDropsRange : null;
    }

    public static boolean hasLegacyPreStackConfiguration() {
        return !preStackMobTypes.isEmpty() || !"false".equalsIgnoreCase(preStackMobDeathDrops);
    }

    public static boolean shouldPreStackDeathDrops(Entity entity) {
        return preStackEntityRange(entity) != null;
    }

    private static Set<ResourceLocation> parsePreStackMobTypes(String value, CommandSourceStack source) {
        if (value.equalsIgnoreCase("false")) {
            return Set.of();
        }
        if (value.length() < 3 || value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']') {
            throw new IllegalArgumentException("format must be false or [entity_id,entity_id]");
        }

        Set<ResourceLocation> result = new HashSet<>();
        for (String rawEntry : value.substring(1, value.length() - 1).split(",", -1)) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                throw new IllegalArgumentException("entity list cannot contain an empty entry");
            }
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id == null || !
                    //#if MC >= 1.19.3
                    BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                    //#else
                    //$$ Registry.ENTITY_TYPE.containsKey(id)
                    //#endif
            ) {
                throw new IllegalArgumentException("unknown entity id: " + entry);
            }
            EntityType<?> type =
                    //#if MC >= 1.21.2
                    //$$ BuiltInRegistries.ENTITY_TYPE.getValue(id);
                    //#else
                    //#if MC >= 1.19.3
                    BuiltInRegistries.ENTITY_TYPE.get(id);
                    //#else
                    //$$ Registry.ENTITY_TYPE.get(id);
                    //#endif
                    //#endif
            if (source != null) {
                Entity entity =
                    //#if MC >= 26.2
                    //$$ type.create(source.getLevel(), new EntitySpawnRequest(EntitySpawnReason.COMMAND, true));
                    //#elseif MC >= 1.21.2
                    //$$ type.create(source.getLevel(), EntitySpawnReason.COMMAND);
                    //#else
                    type.create(source.getLevel());
                    //#endif
                if (!(entity instanceof Mob)) {
                    throw new IllegalArgumentException("entity is not a mob: " + id);
                }
                FGACompat.discard(entity);
            }
            result.add(id);
        }
        return Set.copyOf(result);
    }

    public static class PreStackMobDeathDropsValidator extends Validator<String> {
        @Override
        public String validate(CommandSourceStack source,
                               //#if MC >= 1.19
                               CarpetRule<String> currentRule,
                               //#else
                               //$$ ParsedRule<String> currentRule,
                               //#endif
                               String newValue, String userInput) {
            try {
                Set<ResourceLocation> parsed = parsePreStackMobTypes(newValue, source);
                preStackMobTypes = parsed;
                //#if MC <= 26.2
                DeathDropPreStackManager.clear();
                //#endif
                if (source != null && !parsed.isEmpty()) {
                    Messenger.m(source, "y Legacy preStackMobDeathDrops is configured; migrate to /dropPreStack entity ... / 旧版生物掉落规则已配置，请迁移到 /dropPreStack entity ...");
                }
                return newValue;
            } catch (RuntimeException exception) {
                Messenger.m(source, "r " + exception.getMessage());
                return null;
            }
        }
    }

    public static class PreStackMobDeathDropsRangeValidator extends Validator<Double> {
        @Override
        public Double validate(CommandSourceStack source,
                               //#if MC >= 1.19
                               CarpetRule<Double> currentRule,
                               //#else
                               //$$ ParsedRule<Double> currentRule,
                               //#endif
                               Double newValue, String userInput) {
            if (newValue != null && Double.isFinite(newValue) && newValue >= 0.0D && newValue <= 16.0D) {
                //#if MC <= 26.2
                DeathDropPreStackManager.clear();
                //#endif
                if (source != null) {
                    Messenger.m(source, "y Legacy preStackMobDeathDropsRange is deprecated; migrate to per-entity ranges / 旧版生物掉落范围已弃用，请迁移到逐实体范围配置。");
                }
                return newValue;
            }
            Messenger.m(source, "r preStackMobDeathDropsRange must be between 0 and 16");
            return null;
        }
    }

    public static int effectiveDroppedItemStackLimit(ItemStack stack) {
        //#if MC <= 26.2
        //$$ return DroppedItemStackLimitConfig.effectiveLimit(stack);
        //#elseif MC >= 1.21.4
        //$$ return stack.getMaxStackSize();
        //#else
        return DroppedItemStackLimitConfig.effectiveLimit(stack);
        //#endif
    }

    public static int effectiveInventoryStackLimit(ItemStack stack) {
        return DroppedItemStackLimitConfig.effectiveInventoryLimit(stack);
    }

    public static int effectiveContainerStackLimit(ItemStack stack) {
        return DroppedItemStackLimitConfig.effectiveContainerLimit(stack);
    }

    public static double effectiveDroppedItemMergeDistance() {
        return droppedItemMergeDistance == -1.0D ? 0.5D : droppedItemMergeDistance;
    }

    public static class Minecraft1_21_1Condition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            //#if MC <= 26.2
            //$$ return true;
            //#elseif MC >= 1.21.4
            //$$ return false;
            //#else
            return true;
            //#endif
        }
    }

    public static class Minecraft1_20_1OrNewerCondition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            //#if MC >= 1.20.1
            return true;
            //#else
            //$$ return false;
            //#endif
        }
    }

    public static class Minecraft1_20_5OrNewerCondition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            //#if MC >= 1.20.5
            return true;
            //#else
            //$$ return false;
            //#endif
        }
    }

    public static class Minecraft1_21_8Condition implements
            //#if MC >= 1.19
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.19
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            //#if MC <= 26.2
            //$$ return true;
            //#elseif MC >= 1.21.4
            //$$ return false;
            //#else
            return true;
            //#endif
        }
    }

    public static class DroppedItemMergeDistanceValidator extends Validator<Double> {
        @Override
        public Double validate(CommandSourceStack source,
                               //#if MC >= 1.19
                               CarpetRule<Double> currentRule,
                               //#else
                               //$$ ParsedRule<Double> currentRule,
                               //#endif
                               Double newValue, String userInput) {
            if (newValue != null && Double.isFinite(newValue)
                    && (newValue == -1.0D || (newValue >= 0.0D && newValue <= 16.0D))) {
                return newValue;
            }
            Messenger.m(source, "r droppedItemMergeDistance must be -1 or between 0 and 16");
            return null;
        }
    }

    @Rule(
        desc = "自定义去除僵尸猪灵的指定掉落物",
        category = {FGA, FEATURE},
        options = {"false", "goldEquipment", "rottenFlesh", "all"}
    )
    public static String zombifiedPiglinDropReduction = "false";

    private static final Set<ResourceLocation> VANILLA_PIGLIN_BARTER_ITEMS = Set.of(
            FGACompat.vanillaId("enchanted_book"),
            FGACompat.vanillaId("iron_boots"),
            FGACompat.vanillaId("potion"),
            FGACompat.vanillaId("splash_potion"),
            FGACompat.vanillaId("iron_nugget"),
            FGACompat.vanillaId("ender_pearl"),
            FGACompat.vanillaId("string"),
            FGACompat.vanillaId("quartz"),
            FGACompat.vanillaId("obsidian"),
            FGACompat.vanillaId("crying_obsidian"),
            FGACompat.vanillaId("fire_charge"),
            FGACompat.vanillaId("leather"),
            FGACompat.vanillaId("soul_sand"),
            FGACompat.vanillaId("nether_brick"),
            FGACompat.vanillaId("spectral_arrow"),
            FGACompat.vanillaId("gravel"),
            FGACompat.vanillaId("blackstone")
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
                exclusions.add(FGACompat.vanillaId("iron_boots"));
                continue;
            }
            if (entry.equalsIgnoreCase("potions")) {
                exclusions.add(FGACompat.vanillaId("potion"));
                exclusions.add(FGACompat.vanillaId("splash_potion"));
                exclusions.add(FGACompat.vanillaId("lingering_potion"));
                continue;
            }

            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id == null) {
                throw new IllegalArgumentException("无效的物品 ID：" + entry);
            }
            if (!
                    //#if MC >= 1.19.3
                    BuiltInRegistries.ITEM.containsKey(id)
                    //#else
                    //$$ Registry.ITEM.containsKey(id)
                    //#endif
            ) {
                throw new IllegalArgumentException("未注册的物品 ID：" + id);
            }
            exclusions.add(id);
        }
        return Set.copyOf(exclusions);
    }

    public static class PiglinBarterExclusionsValidator extends Validator<String> {
        @Override
        public String validate(CommandSourceStack source,
                               //#if MC >= 1.19
                               CarpetRule<String> currentRule,
                               //#else
                               //$$ ParsedRule<String> currentRule,
                               //#endif
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
        public Integer validate(CommandSourceStack source,
                                //#if MC >= 1.19
                                CarpetRule<Integer> currentRule,
                                //#else
                                //$$ ParsedRule<Integer> currentRule,
                                //#endif
                                Integer newValue, String userInput) {
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
