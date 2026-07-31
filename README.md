# Carpet FGA Addition

Carpet FGA Addition 是一个面向服务器的 Fabric Carpet 扩展，提供假人、掉落物、村民、命令兼容和性能辅助功能。除特别说明外，功能默认关闭，使用 `/carpet` 规则启用。

## 支持版本

当前版本为 `1.4.0`，提供 Minecraft `1.16.5`、`1.17.1`、`1.18.2`、`1.19.2`、`1.19.4`、`1.20.1`、`1.20.4`、`1.20.6`、`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2` 和 `26.2` 构建。

## Carpet 规则

### 假人与通用功能

| 规则 | 默认值 | 用法与范围 |
| --- | --- | --- |
| `fakePlayerNameLength` | `-1` | 假人名称长度。`-1` 使用原版限制；也可设为 `1-128`。1.18+。 |
| `fakePlayerRangeControl` | `false` | 启用假人区域放置、交互、破坏和连续任务。 |
| `endGatewayRegeneration` | `false` | 记录并再生被破坏的原版末地折跃门，只恢复折跃门方块，不改动周围方块。1.16.5+。 |
| `wanderingTraderNoDespawn` | `false` | `false` 为原版行为，`true` 保护全部流浪商人，`controlled` 只保护名单命中的流浪商人。1.16.5-26.1.2；26.x 当前不注册保护 Mixin。 |
| `fakePlayerProfilePreload` | `false` | `false`、`always` 或 `adaptive`，异步预加载假人档案。仅 1.21.1。 |
| `fgaUnicodeArgumentsSupport` | `false` | 允许未加引号的指令参数包含中文和其他 Unicode 字符。 |
| `recipeBookAlwaysUnlocked` | `false` | 让配方书保持可用，不为每名玩家保存逐条解锁数据。1.19.4+。 |
| `inventoryAdvancementOptimization` | `false` | `false` 保持原版，`exact` 使用精确候选索引优化 `inventory_changed` 进度。1.19.4+。 |
| `playerHealthDisplay` | `true` | 多人游戏列表最右侧显示生命值：`true` 显示真人和假人，`false` 默认隐藏但可由 `/log playerHealth` 订阅，`nofake` 只显示真人。1.19.4+。 |
| `spectatorFreeTeleport` | `false` | 允许非 OP 旁观者使用 `/tp` 或 `/teleport` 传送自己。1.21.1-1.21.5。 |
| `restorePre26BeeCollisionBox` | `false` | 恢复 Minecraft 26.2 以前的蜜蜂碰撞箱。仅 26.2。 |
| `clientDimensionIds` | `[overworld,the_nether,the_end]` | 设置客户端看到的主世界、下界和末地 ID，不改变服务端维度。1.21.1+。 |
| `removeDialogWarning` | `false` | 移除服务器发送的运行命令/对话框确认警告。1.21.8+。 |

### 村民、生物与掉落物

| 规则 | 默认值 | 用法与范围 |
| --- | --- | --- |
| `villagerBreedingAnimalization` | `false` | `false` 原版行为，`true` 保留原版并允许玩家直接喂食，`only` 只允许玩家直接喂食。 |
| `villagerPerformanceOptimization` | `false` | `false`、`true`、`ops` 或 `1-4`，控制村民交易优化和 `/villagerPerformance` 权限。1.21.1+。 |
| `hostileMobInventoryAccess` | `false` | 启用后，空手潜行右键敌对生物可打开其原版装备栏。 |
| `droppedItemStackLimit` | `false` | `false`、`true`、`ops` 或 `0-4`，启用地面物品堆叠上限配置。1.21.1+。 |
| `droppedItemMergeDistance` | `-1` | 地面物品水平合并距离；`-1` 保持原版，范围 `0-16`。1.21.1+。 |
| `unlimitedFillCommands` | `false` | 移除 `/fill` 和 `/fillbiome` 的体积限制，同时保留区块、边界和权限检查。1.21.8+。 |
| `preStackDroppedItems` | `false` | 启用 `/dropPreStack` 的生物、方块和容器掉落预堆叠。1.21.1-26.1.2。 |
| `zombifiedPiglinDropReduction` | `false` | `false`、`goldEquipment`、`rottenFlesh` 或 `all`，减少僵尸猪灵指定掉落。 |
| `piglinBarterItemExclusions` | `false` | `false`、预设 `ironBoots`/`potions`，或使用物品 ID 列表排除猪灵交易结果。 |

旧版 `preStackMobDeathDrops` 和 `preStackMobDeathDropsRange` 已从规则列表隐藏，仅为兼容旧存档保留；请改用 `/dropPreStack entity ...`。

### 1.21.1 假人全物品分类

以下规则只在 Minecraft `1.21.1` 注册：

| 规则 | 默认值 | 用法 |
| --- | --- | --- |
| `fakePlayerItemSortMode` | `false` | `false`、`summon` 或 `quickopen`。 |
| `fakePlayerItemSortWhitelist` | `false` | `false`、`vanillaWhitelist` 或 `modWhitelist`。 |
| `fakePlayerItemSortQuickShulker` | `false` | 使用快捷潜影盒分类规则。 |
| `fakePlayerItemSortNameFormat` | `false` | `false`、`autoDetect`、`prefix` 或 `suffix`。 |
| `fakePlayerItemSortTargetLanguage` | `english` | `english`、`chinese` 或 `custom`。 |
| `fakePlayerItemSortShulkerRestock` | `false` | 允许 `box_restock` 补货假人合成普通空潜影盒。 |
| `fakePlayerItemSortCleanOpenedTarget` | `false` | 打开分类目标时，自动移走非本分类异物。 |
| `fakePlayerItemSortInventoryRebuild` | `false` | `false`、`true` 或 `opall`，控制库存重构命令。 |
| `fakePlayerItemSortDashboard` | `false` | 启用本地分类网页和缓存 API。 |
| `fakePlayerItemSortCpuThreads` | `0` | 异步 CPU 档位 `0`、`1`、`2`。 |
| `fakePlayerItemSortSpeed` | `8` | 每次提交的速度档位 `4`、`8`、`16`。 |

`quickopen` 直接读写离线 playerdata，不为访问背包召唤假人；`summon` 使用 Carpet 在线假人。首位分类假人保存散货，后续假人保存盒装物品，装备栏始终不读取、不写入。

## 指令参考

### 假人区域操作

```text
/player <假人> use range <起点> to <终点> [参数]
/player <假人> use continuous range <起点> to <终点> [参数]
/player <假人> attack range <起点> to <终点> [参数]
/player <假人> attack continuous range <起点> to <终点> [参数]
/player <假人> stop
/player <假人> use|attack range help
```

参数可组合：`pathfinding`、`reach <0.1-64>`、`airPlace`、`ignoreObstruction`、`placeBlock`、`interactBlock`、`interactSpeed <1-64>`。不填写 `placeBlock` 和 `interactBlock` 时默认使用 `placeBlock`。

### 掉落物堆叠上限

```text
/droppedItemStackLimit mode all <数量>
/droppedItemStackLimit mode black <数量>
/droppedItemStackLimit mode whitelist
/droppedItemStackLimit set black <物品ID>
/droppedItemStackLimit remove black <物品ID>
/droppedItemStackLimit set whitelist <物品ID> <数量>
/droppedItemStackLimit remove whitelist <物品ID>
/droppedItemStackLimit list [black|whitelist] [页码]
/droppedItemStackLimit clear
```

### 掉落物预堆叠

根命令为 `/dropPreStack`；1.21.1 另有 `/fga dropPreStack` 别名。

```text
/dropPreStack help
/dropPreStack status
/dropPreStack entity add <实体ID> [范围]
/dropPreStack entity remove <实体ID>
/dropPreStack entity set <实体ID> [范围]
/dropPreStack entity list [页码]
/dropPreStack block add <物品ID> [范围]
/dropPreStack block remove <物品ID>
/dropPreStack block set <物品ID> [范围]
/dropPreStack block list [页码]
/dropPreStack container add <方块或实体ID> [范围]
/dropPreStack container remove <方块或实体ID>
/dropPreStack container set <方块或实体ID> [范围]
/dropPreStack container list [页码]
```

范围为 `0-16`，省略时为 `1.0`。物品和实体 ID 支持 `minecraft:stone` 与 `stone` 形式；列表显示中文名、英文 ID 和范围。

### 村民性能与流浪商人

```text
/villagerPerformance help
/villagerPerformance status
/villagerPerformance trade false|ai|static
/villagerPerformance trade name add|remove <名称>
/villagerPerformance trade name list [页码]
/villagerPerformance trade block add|remove <方块ID>
/villagerPerformance trade block list [页码]
/villagerPerformance gift false|true
/villagerPerformance gift name add|remove <名称>
/villagerPerformance gift block add|remove <方块ID>
/villagerPerformance gift list [页码]
/villagerPerformance wanderingTrader false|true|controlled
/villagerPerformance wanderingTrader name add|remove|list <名称>
/villagerPerformance wanderingTrader block add|remove|list <方块ID>
```

`controlled` 模式下，流浪商人的自定义名称或脚下方块命中名单即可受到保护。配置保存在世界的 `carpet/carpetfgaaddition/` 目录。

### 1.21.1 假人分类

```text
/fakePlayerItemSort status
/fakePlayerItemSort whitelist add|remove <玩家名>
/fakePlayerItemSort whitelist list [页码]
/fakePlayerItemSort format prefix|suffix <文本>
/fakePlayerItemSort format status
/fakePlayerItemSort name set <物品ID> <名称>
/fakePlayerItemSort name remove <物品ID>
/fakePlayerItemSort name list [页码]
/fakePlayerItemSort name reload
/fakePlayerItemSort workers <initial> <cached>
/fakePlayerItemSort dashboard status
/fakePlayerItemSort dashboard port <1024-65535>
/player <假人> bot_sort
/player <假人> bot_sort continuous
/player <假人> bot_sort stop
/player <假人> bot_sort restart <物品名称>
/player <假人> bot_sort restart all
/player <假人> bot_sort restart all confirm
```

`restart all` 需要二次确认；`fakePlayerItemSortInventoryRebuild=opall` 时仅 OP 可执行全部重构。网页默认监听 `127.0.0.1:8766`，只读取内存缓存。

### 其他指令

```text
/inventoryAdvancementOptimization status
/inventoryAdvancementOptimization stats
/inventoryAdvancementOptimization verify
/inventoryAdvancementOptimization resetStats
/log playerHealth
/fga help
/fga status
/fga droppedItemStackLimit <子命令>
/fga dropPreStack <子命令>
/fga villagerPerformance <子命令>
/fga fakePlayerItemSort <子命令>
/fga inventoryAdvancementOptimization <子命令>
/fga player <假人> <子命令>
```

`/log playerHealth` 切换当前玩家的多人游戏列表生命值订阅。`/fga` 入口和完整帮助索引目前仅在 `1.21.1` 注册。

## 许可与致谢

本项目按仓库许可发布。部分设计参考了 MIT 许可的 Org Addition、SaveMyRecipeBook 和 InventoryAdvancementAccelerator，并在 JAR 的 `META-INF/NOTICE-*` 中保留归属说明。
