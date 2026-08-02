# Carpet FGA Addition 规则

> 文档版本：`1.4.2`

所有规则通过 `/carpet <规则名> <值>` 管理。未特别说明时，规则默认关闭。

## 假人与通用功能

| 规则 | 类型 | 默认值 | 可选值 | 生效版本 | 说明 |
|---|---|---|---|---|---|
| `fakePlayerNameLength` | 整数 | `-1` | `-1`、`1-128` | 1.18+ | 控制假人名称长度；`-1` 使用原版限制。 |
| `fakePlayerRangeControl` | 布尔 | `false` | `false`、`true` | 全版本 | 启用假人区域放置、交互、破坏和连续任务。 |
| `endGatewayRegeneration` | 布尔 | `false` | `false`、`true` | 1.16.5+ | 记录并再生被破坏的原版末地折跃门，只恢复折跃门方块。 |
| `wanderingTraderNoDespawn` | 枚举 | `false` | `false`、`true`、`controlled` | 1.16.5-26.1.2 | 控制流浪商人自然消失；`controlled` 只保护命中名单的名称或脚下方块。 |
| `fakePlayerProfilePreload` | 枚举 | `false` | `false`、`always`、`adaptive` | 1.21.1 | 异步预加载假人档案，减少召唤时的阻塞。 |
| `fgaUnicodeArgumentsSupport` | 布尔 | `false` | `false`、`true` | 全版本 | 允许未加引号的命令参数包含中文和其他 Unicode 字符。 |
| `recipeBookAlwaysUnlocked` | 布尔 | `false` | `false`、`true` | 1.16.5+ | 保持配方书解锁，不保存每名玩家的逐条配方解锁进度。 |
| `playerHealthDisplay` | 枚举 | `true` | `true`、`false`、`nofake` | 1.16.5+ | 只在多人游戏列表最右侧显示生命值；不创建计分板或头顶显示。 |
| `spectatorFreeTeleport` | 布尔 | `false` | `false`、`true` | 1.21.1-1.21.5 | 允许非 OP 旁观者只传送自己。 |
| `clientDimensionIds` | 列表 | `[overworld,the_nether,the_end]` | 三个客户端维度 ID | 1.21.1+ | 修改客户端看到的维度 ID，不改变服务端维度。 |
| `removeDialogWarning` | 布尔 | `false` | `false`、`true` | 1.21.8+ | 移除服务端发送的命令/对话框确认警告。 |
| `restorePre26BeeCollisionBox` | 布尔 | `false` | `false`、`true` | 26.2 | 恢复 26.2 之前的蜜蜂碰撞箱。 |

## 村民、生物与掉落物

| 规则 | 类型 | 默认值 | 可选值 | 生效版本 | 说明 |
|---|---|---|---|---|---|
| `villagerBreedingAnimalization` | 枚举 | `false` | `false`、`true`、`only` | 全版本 | 控制玩家直接喂养村民；`only` 只允许玩家喂养。 |
| `villagerPerformanceOptimization` | 枚举 | `false` | `false`、`true`、`ops`、`1-4` | 1.20.1+ | 启用村民交易/赠礼优化并控制 `/villagerPerformance` 权限。 |
| `hostileMobInventoryAccess` | 布尔 | `false` | `false`、`true` | 全版本 | 空手潜行右键敌对生物时打开其原版装备栏。 |
| `droppedItemStackLimit` | 枚举 | `false` | `false`、`true`、`ops`、`0-4` | 1.21.1+ | 启用地面物品堆叠上限配置。 |
| `droppedItemMergeDistance` | 小数 | `-1` | `-1`、`0-16` | 1.21.1+ | 设置地面物品水平合并距离；`-1` 保持原版。 |
| `unlimitedFillCommands` | 布尔 | `false` | `false`、`true` | 1.21.8+ | 移除 `/fill` 和 `/fillbiome` 体积限制，保留原版安全检查。 |
| `preStackDroppedItems` | 布尔 | `false` | `false`、`true` | 1.20.5-26.1.2 | 启用 `/dropPreStack` 的生物、方块和容器掉落预堆叠。 |
| `zombifiedPiglinDropReduction` | 枚举 | `false` | `false`、`goldEquipment`、`rottenFlesh`、`all` | 全版本 | 减少僵尸猪灵指定掉落。 |
| `piglinBarterItemExclusions` | 列表 | `false` | `false`、`ironBoots`、`potions`、物品 ID 列表 | 全版本 | 排除指定猪灵 barter 结果。 |

旧版 `preStackMobDeathDrops` 与 `preStackMobDeathDropsRange` 已隐藏，仅保留旧存档兼容；新配置使用 `/dropPreStack entity ...`。

## 1.21.1 假人全物品分类

以下规则只在 Minecraft `1.21.1` 注册：

| 规则 | 类型 | 默认值 | 可选值 | 说明 |
|---|---|---|---|---|
| `fakePlayerItemSortMode` | 枚举 | `false` | `false`、`summon`、`quickopen` | 启用假人分类。`quickopen` 直接读写离线 playerdata；`summon` 使用在线 Carpet 假人。 |
| `fakePlayerItemSortWhitelist` | 枚举 | `false` | `false`、`vanillaWhitelist`、`modWhitelist` | 控制分类白名单模式。 |
| `fakePlayerItemSortQuickShulker` | 布尔 | `false` | `false`、`true` | 启用快捷潜影盒分类规则。装备栏始终不读写。 |
| `fakePlayerItemSortNameFormat` | 枚举 | `false` | `false`、`autoDetect`、`prefix`、`suffix` | 控制目标假人名称格式。 |
| `fakePlayerItemSortTargetLanguage` | 枚举 | `english` | `english`、`chinese`、`custom` | 控制分类目标名称语言。 |
| `fakePlayerItemSortShulkerRestock` | 布尔 | `false` | `false`、`true` | 允许 `box_restock` 补货假人合成普通空潜影盒。 |
| `fakePlayerItemSortCleanOpenedTarget` | 布尔 | `false` | `false`、`true` | 打开分类目标时自动移走主物品栏和副手异物。 |
| `fakePlayerItemSortInventoryRebuild` | 枚举 | `false` | `false`、`true`、`opall` | 控制分类库存重构命令及 OP 的全量重构权限。 |
| `fakePlayerItemSortDashboard` | 布尔 | `false` | `false`、`true` | 启用本地分类网页和缓存 API。 |
| `fakePlayerItemSortCpuThreads` | 枚举 | `0` | `0`、`1`、`2` | 异步 CPU 档位。 |
| `fakePlayerItemSortSpeed` | 枚举 | `8` | `4`、`8`、`16` | 每次向主线程提交的分类速度档位。 |

## 配置文件

世界配置位于 `world/config/carpetfgaaddition/`。升级时会从旧目录 `world/carpet/carpetfgaaddition/` 安全迁移；迁移成功后旧文件改名为 `.migrated`。损坏文件会保留原位置，不会覆盖新文件。

`inventoryAdvancementOptimization` 是隐藏的内部兼容字段，不注册为 Carpet 规则，也没有可用的独立命令；其行为由当前版本实现和相关生命周期自动控制。
