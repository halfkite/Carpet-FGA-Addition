package carpet.fga;

//#if MC >= 1.18
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

//#if MC >= 1.18
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
            //#if MC >= 1.18
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.18
                shouldRegister() {
                //#else
                //$$ isTrue() {
                //#endif
            //#if MC >= 1.18
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
        desc = "Asynchronously preloads fake-player profiles before spawning",
        category = {FGA, FEATURE},
        options = {"false", "always", "adaptive"},
        validate = FGASettings.FakePlayerProfilePreloadValidator.class,
        condition = FGASettings.Minecraft1_21_1OnlyCondition.class
    )
    public static String fakePlayerProfilePreload = "false";

    public static class FakePlayerProfilePreloadValidator extends
            //#if MC >= 1.18
            Validator<String> {
            //#else
            //$$ Validator<String> {
            //#endif
        @Override
        public String validate(CommandSourceStack source,
                               //#if MC >= 1.18
                               CarpetRule<String> currentRule,
                               //#else
                               //$$ ParsedRule<String> currentRule,
                               //#endif
                               String newValue, String userInput) {
            if (!Set.of("false", "always", "adaptive").contains(newValue)) {
                Messenger.m(source, "r fakePlayerProfilePreload must be false, always, or adaptive");
                return null;
            }
            //#if MC >= 1.21.1
            FakePlayerProfilePreloadManager.clearAll();
            //#endif
            return newValue;
        }
    }

    public static class Minecraft1_21_1OnlyCondition implements
            //#if MC >= 1.18
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.18
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

    //#if MC >= 1.21.1
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
        public String validate(CommandSourceStack source, CarpetRule<String> currentRule,
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
            //#if MC >= 1.18
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.18
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
            //#if MC >= 1.18
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.18
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

    //#if MC >= 1.21.1
    @Rule(
        desc = "Enables villager performance optimization and controls access to /villagerPerformance",
        category = {FGA, FEATURE},
        options = {"false", "true", "ops", "1", "2", "3", "4"},
        validate = FGASettings.VillagerPerformanceOptimizationValidator.class,
        condition = FGASettings.Minecraft1_21_1Condition.class
    )
    public static String villagerPerformanceOptimization = "false";

    public static class VillagerPerformanceOptimizationValidator extends Validator<String> {
        @Override public String validate(CommandSourceStack source, CarpetRule<String> currentRule, String newValue, String userInput) {
            if (!Set.of("false", "true", "ops", "1", "2", "3", "4").contains(newValue)) {
                Messenger.m(source, "r villagerPerformanceOptimization must be false, true, ops, or 1-4");
                return null;
            }
            VillagerTradeOnlyManager.clear(); return newValue;
        }
    }
    //#endif

    @Rule(
        desc = "Allows opening hostile mob equipment with an empty-handed shift-right-click",
        category = {FGA, FEATURE}
    )
    public static boolean hostileMobInventoryAccess = false;

    @Rule(
        desc = "Enables configurable ground item entity stack limits",
        category = {FGA, FEATURE},
        condition = FGASettings.Minecraft1_21_1Condition.class
    )
    public static boolean droppedItemStackLimit = false;

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

    @Rule(
        desc = "Pre-stacks compatible death drops from selected mob entity types",
        category = {FGA, FEATURE},
        options = {"false", "[zombified_piglin]"},
        strict = false,
        validate = FGASettings.PreStackMobDeathDropsValidator.class,
        condition = FGASettings.Minecraft1_21_1Condition.class
    )
    public static String preStackMobDeathDrops = "false";

    @Rule(
        desc = "Sets the three-dimensional range for same-tick selected mob death drop pre-stacking",
        category = {FGA, FEATURE},
        options = {"0", "1", "3", "8", "16"},
        strict = false,
        validate = FGASettings.PreStackMobDeathDropsRangeValidator.class,
        condition = FGASettings.Minecraft1_21_1Condition.class
    )
    public static double preStackMobDeathDropsRange = 3.0D;

    public static boolean shouldPreStackDeathDrops(Mob mob) {
        return preStackMobTypes.contains(
                //#if MC >= 1.19.3
                BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())
                //#else
                //$$ Registry.ENTITY_TYPE.getKey(mob.getType())
                //#endif
        );
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
                entity.discard();
            }
            result.add(id);
        }
        return Set.copyOf(result);
    }

    public static class PreStackMobDeathDropsValidator extends Validator<String> {
        @Override
        public String validate(CommandSourceStack source,
                               //#if MC >= 1.18
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
                               //#if MC >= 1.18
                               CarpetRule<Double> currentRule,
                               //#else
                               //$$ ParsedRule<Double> currentRule,
                               //#endif
                               Double newValue, String userInput) {
            if (newValue != null && Double.isFinite(newValue) && newValue >= 0.0D && newValue <= 16.0D) {
                //#if MC <= 26.2
                DeathDropPreStackManager.clear();
                //#endif
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

    public static double effectiveDroppedItemMergeDistance() {
        return droppedItemMergeDistance == -1.0D ? 0.5D : droppedItemMergeDistance;
    }

    public static class Minecraft1_21_1Condition implements
            //#if MC >= 1.18
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.18
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

    public static class Minecraft1_21_8Condition implements
            //#if MC >= 1.18
            carpet.api.settings.Rule.Condition {
            //#else
            //$$ carpet.settings.Condition {
            //#endif
        @Override
        public boolean
                //#if MC >= 1.18
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
                               //#if MC >= 1.18
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
                               //#if MC >= 1.18
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
                                //#if MC >= 1.18
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
