# Carpet FGA Addition 规则

> 文档版本：`1.4.8`

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
| `itemFrameBlockification` | 布尔 | `false` | `false`、`true` | 1.21.1 | 将普通与荧光展示框移出服务端实体 tick 调度，改为支撑方块变化时验证；保留原版客户端显示、交互、掉落、地图和比较器行为。 |
| `fireworkMinecartBoost` | 布尔 | `false` | `false`、`true` | 1.21.1 | 玩家乘坐普通矿车时可使用烟花火箭，以配置速度满速运行并在结束后线性减速。 |
| `chainMinecartBinding` | 布尔 | `false` | `false`、`true` | 1.21.1 | 使用锁链依次右击两辆普通矿车，将其连接为持久保存的线性列车。 |
| `minecartFeatureCommandPermission` | 权限 | `false` | `false`、`true`、`ops`、`0-4` | 1.21.1 | 控制 `/minecart` 与 `/fga minecart`；`false` 隐藏命令，`true`/`0` 允许所有玩家。 |
| `vehicleStopOnDismount` | 枚举 | `false` | `false`、`minecart`、`boat`、`all`、`custom` | 全部支持版本 | 驾驶者离开载具时清除水平速度；`custom` 使用 `/vehicleStop` 保存的个人设置。 |
| `voidWorldGeneration` | 布尔 | `false` | `false`、`true` | 全部支持版本 | 让新生成区块为空白，同时保留群系和结构定位数据；已有区块不变。 |
| `terrainRegenerationCommandPermission` | 权限 | `ops` | `false`、`true`、`ops`、`0-4` | 1.21-26.2 | 控制 `/regenerateTerrain` 与 `/fga regenerateTerrain`，包括有破坏性的清除和重生成任务。 |
| `fullShulkerBoxCrafting` | 布尔 | `false` | `false`、`true` | 1.21-26.2 | 使用服务器当前普通配方处理满潜影盒，支持多材料、标签、数据包配方、Shift/Q 补货和 AMS 54 格大潜影盒软兼容。 |
| `spectatorFreeTeleport` | 布尔 | `false` | `false`、`true` | 1.21.1-1.21.5 | 允许非 OP 旁观者只传送自己。 |
| `clientDimensionIds` | 列表 | `[overworld,the_nether,the_end]` | 三个客户端维度 ID | 1.21.1+ | 修改客户端看到的维度 ID，不改变服务端维度。 |
| `removeDialogWarning` | 布尔 | `false` | `false`、`true` | 1.21.8+ | 移除服务端发送的命令/对话框确认警告。 |
| `restorePre26BeeCollisionBox` | 布尔 | `false` | `false`、`true` | 26.2 | 恢复 26.2 之前的蜜蜂碰撞箱。 |

## 村民、生物与掉落物

| 规则 | 类型 | 默认值 | 可选值 | 生效版本 | 说明 |
|---|---|---|---|---|---|
| `villagerBreedingAnimalization` | 枚举 | `false` | `false`、`true`、`only` | 全版本 | 控制玩家直接喂养村民；`only` 只允许玩家喂养。 |
| `babyMobNoGrowth` | 字符串 | `false` | `false`、`true`、`mini`、自定义名称 | 1.21-26.2 | `true` 阻止所有可成长幼体长大；`mini` 是同名名称模式预设；自定义值仅锁定完整自定义名称严格匹配且区分大小写的幼体，包括蝌蚪。 |
| `farmerVillagersDoNotCraftBread` | 布尔 | `false` | `false`、`true` | 1.21-26.2 | 让农民村民处理小麦的表现与 26.3+ 一样，不再把小麦合成面包，不影响其他农民行为 |
| `villagerUpgradeWhileTrading` | 布尔 | `false` | `false`、`true` | 1.21-26.2 | 让村民在交易界面保持打开时继续等待并完成升级，升级后立即刷新等级、经验和交易列表 |
| `villagerPerformanceOptimization` | 枚举 | `false` | `false`、`true`、`ops`、`1-4` | 1.20.1+ | 启用村民交易/赠礼优化并控制 `/villagerPerformance` 权限。 |
| `hostileMobInventoryAccess` | 布尔 | `false` | `false`、`true` | 全版本 | 空手潜行右键敌对生物时打开其原版装备栏。 |
| `droppedItemStackLimit` | 枚举 | `false` | `false`、`true`、`ops`、`0-4` | 全部支持版本 | 配置地面、玩家背包和容器三类独立堆叠上限，背包或容器上限启用时需要 FGA 客户端，只有地面上限时保持纯服务端。 |
| `droppedItemMergeDistance` | 小数 | `-1` | `-1`、`0-16` | 1.21.1+ | 设置地面物品水平合并距离；`-1` 保持原版。 |
| `unlimitedFillCommands` | 布尔 | `false` | `false`、`true` | 1.21.8+ | 移除 `/fill` 和 `/fillbiome` 体积限制，保留原版安全检查。 |
| `preStackDroppedItems` | 布尔 | `false` | `false`、`true` | 1.20.5-26.2 | 启用 `/dropPreStack` 的生物、方块和容器掉落预堆叠。 |
| `zombifiedPiglinDropReduction` | 枚举 | `false` | `false`、`goldEquipment`、`rottenFlesh`、`all` | 全版本 | 减少僵尸猪灵指定掉落。 |
| `piglinBarterItemExclusions` | 列表 | `false` | `false`、`ironBoots`、`potions`、物品 ID 列表 | 全版本 | 排除指定猪灵 barter 结果。 |

`babyMobNoGrowth` 是纯服务端规则。`mini` 预设仅匹配自定义名称完整等于小写 `mini` 的幼体，`Mini` 不匹配。其他名称模式只读取实体明确设置的自定义名称，并按 `Component#getString()` 的完整文本比较；带空格的名称需要用引号传入，例如 `/carpet babyMobNoGrowth "永远年幼"`。规则只阻止自然成长和喂食加速，不拦截管理员使用 `/data` 或 NBT 直接修改年龄。关闭规则后，被冻结的幼体会从当前年龄继续成长。

旧版 `preStackMobDeathDrops` 与 `preStackMobDeathDropsRange` 已隐藏，仅保留旧存档兼容；新配置使用 `/dropPreStack entity ...`。

## 深板岩切石与玩家加载距离

| 规则 | 类型 | 默认值 | 可选值 | 生效版本 | 说明 |
|---|---|---|---|---|---|
| `deepslateStonecuttingRecipes` | 布尔 | `false` | `false`、`true` | `1.17.1-1.21.11` | 让深板岩在切石机中的表现与 26.1+ 一样；只控制 FGA 新增配方，不过滤原版、数据包和模组配方。`1.16.5`、`26.1.2`、`26.2` 不注册该规则 |
| `playerLoadDistance` | 权限字符串 | `false` | `false`、`true`、`ops`、`0-4` | `1.21.1` | 启用每名玩家独立的区块发送与跟踪覆盖，不改变模拟距离。`false` 时命令不可用 |
| `trialSpawnerPlayerMultiplier` | 整数 | `100` | `1-10000` | `1.21-26.2` | 每名命中筛选的玩家按该人数参与普通与不祥试炼的刷怪和奖励规模，`1` 为原版 |
| `trialSpawnerPlayerFilter` | 字符串 | `false` | `false`、`true`、`bot_`、自定义前缀 | `1.21-26.2` | `false` 关闭多倍；`true` 匹配所有玩家；其他值按玩家名称区分大小写的前缀匹配，`bot_` 是预选项 |
| `trialStopCommandPermission` | 权限字符串 | `false` | `false`、`true`、`ops`、`0-4` | `1.21-26.2` | 控制 `/trialStop` 的使用权限 |

`playerLoadDistance` 使用 `/playerLoadDistance` 和 `/fga playerLoadDistance`。距离支持 `-1`、`0`、`1-32` 和 `none`。`-1` 只弱加载中心区块，`0` 强加载中心并保持 3x3 弱加载区域可用，`1-32` 为玩家区块半径，真人实际值还会受客户端请求视距限制，`none` 移除该玩家的加载视图。`set` 默认是临时设置，末尾加 `persistent` 才按 UUID 保存到 `world/config/carpetfgaaddition/player-load-distance.json`。`reset` 恢复持久值，`reset ... persistent` 删除持久值。当前覆盖会显示在多人游戏列表最左侧，功能纯服务端生效

`trialSpawnerPlayerMultiplier` 与 `trialSpawnerPlayerFilter` 同时作用于普通和不祥试炼刷怪笼，不生成假玩家，不写入虚假 UUID。每名命中玩家分别叠加等效人数，奖励规模也按每名真实参与者展开。`trialSpawnerPlayerFilter` 的游戏内显示名称为“试炼刷怪笼多倍触发”

`trialStopCommandPermission` 同时启用并控制 `/trialStop` 与 `/fga trialStop`。命令只处理执行时已加载区块内的刷怪笼；`range <半径>` 以执行位置为中心并忽略 Y，`range from` 使用完整 XYZ 方框。奖励模式为 `none`、`reward`、`fast`，`clear` 只清理该刷怪笼登记且当前已加载的怪物。`none` 和 `fast` 立即刷新刷怪笼，`reward` 按原版节奏喷完后立即刷新，三种模式都跳过完整冷却

## 1.21+ 假人全物品分类

以下规则在 Minecraft `1.21-26.2` 注册：

| 规则 | 类型 | 默认值 | 可选值 | 说明 |
|---|---|---|---|---|
| `fakePlayerItemSort` | 布尔 | `false` | `false`、`true` | 启用假人全物品分类核心，模式、白名单、潜影盒和语言由 `/fakePlayerItemSort` 管理；补货、重构、磁盘缓存、网页和线程参数仅在 `1.21.1` 启用。 |

分类配置保存在 `world/config/carpetfgaaddition/fake-player-item-sort.json`。`/fakePlayerItemSort mode summon` 使用在线 Carpet 假人，`mode quickopen` 直接读写离线 playerdata。旧版 `fakePlayerItemSort*` Carpet 配置只在首次启动时迁移到该 JSON，不再注册为规则。

## 满潜影盒合成

`fullShulkerBoxCrafting` 在 Minecraft `1.21-26.2` 提供复杂配方实现，纯服务端可用。把分别装满对应材料的潜影盒按普通配方形状放入玩家 2×2 合成栏或工作台，系统会直接查询服务器当前配方表，因此支持多材料、标签材料、数据包和模组普通合成配方。允许输入堆叠的同组件满盒，每次从各配方格消耗一个；背包中的堆叠空盒也会按实际需求扣除。所有输入必须可堆叠、每盒容量相同并全部耗尽，主结果及配方返还物都必须恰好装成整数个满盒。盒子颜色、自定义名称和其他盒子组件会保留。检测到 Carpet AMS Addition 且 `largeShulkerBox` 开启时按 54 格大潜影盒完成输入判定、容量换算和成品填充，关闭或未安装 AMS 时仍按原版 27 格处理。QuickCraft 的 `Alt+C`、结果槽 Shift 移动和 Q 丢出会在整次操作结束后，把背包中点击前已有的匹配满盒一次性平均分配到对应配方格；Shift+左键会先执行合成栏原有材料能够完成的所有满盒配方，补货不会加入本次合成循环，普通左键不补货，客户端不需要安装 FGA。

## 配置文件

世界配置位于 `world/config/carpetfgaaddition/`。升级时会从旧目录 `world/carpet/carpetfgaaddition/` 安全迁移；迁移成功后旧文件改名为 `.migrated`。损坏文件会保留原位置，不会覆盖新文件。

`inventoryAdvancementOptimization` 是隐藏的内部兼容字段，不注册为 Carpet 规则，也没有可用的独立命令；其行为由当前版本实现和相关生命周期自动控制。
