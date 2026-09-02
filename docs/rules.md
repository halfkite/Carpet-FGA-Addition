# Carpet FGA Addition 规则

> 文档版本：`1.5.6`

所有规则通过 `/carpet <规则名> <值>` 管理。未特别说明时，规则默认关闭。

## 假人与通用功能

| 规则 | 类型 | 默认值 | 可选值 | 生效版本 | 说明 |
|---|---|---|---|---|---|
| `fakePlayerNameLength` | 整数 | `-1` | `-1`、`1-128` | 1.21+ | 控制假人名称长度；`-1` 使用原版限制。 |
| `fakePlayerRangeControl` | 布尔 | `false` | `false`、`true` | 全版本 | 启用假人区域放置、交互、破坏和连续任务。 |
| `endGatewayRegeneration` | 布尔 | `false` | `false`、`true` | 1.21+ | 记录并再生被破坏的原版末地折跃门，只恢复折跃门方块。 |
| `wanderingTraderNoDespawn` | 枚举 | `false` | `false`、`true`、`controlled` | 1.21+ | 控制流浪商人自然消失；`controlled` 只保护命中名单的名称或脚下方块。 |
| `fakePlayerProfilePreload` | 枚举 | `false` | `false`、`always`、`adaptive` | 1.21.1 | 异步预加载假人档案，减少召唤时的阻塞。 |
| `fgaUnicodeArgumentsSupport` | 布尔 | `false` | `false`、`true` | 全版本 | 允许未加引号的命令参数包含中文和其他 Unicode 字符。 |
| `recipeBookAlwaysUnlocked` | 布尔 | `false` | `false`、`true` | 1.21+ | 玩家进入服务器时自动获得全部已注册配方，每名玩家自动发放冷却一分钟；同时保留已保存的配方解锁进度，不会清空配方数据。 |
| `playerHealthDisplay` | 枚举 | `false` | `true`、`false`、`nofake` | 1.21+ | 只在多人游戏列表最右侧显示生命值；不创建计分板或头顶显示。默认关闭，可通过 `/carpet playerHealthDisplay true` 开启。 |
| `itemFrameBlockification` | 布尔 | `false` | `false`、`true` | 1.21.1 | 将普通与荧光展示框移出服务端实体 tick 调度，改为支撑方块变化时验证；保留原版客户端显示、交互、掉落、地图和比较器行为。 |
| `fireworkMinecartBoost` | 布尔 | `false` | `false`、`true` | 1.21.1 | 玩家乘坐普通矿车时可使用烟花火箭，以配置速度满速运行并在结束后线性减速。 |
| `chainMinecartBinding` | 布尔 | `false` | `false`、`true` | 1.21.1 | 使用锁链依次右击两辆普通矿车，将其连接为持久保存的线性列车。 |
| `minecartFeatureCommandPermission` | 权限 | `false` | `false`、`true`、`ops`、`0-4` | 1.21.1 | 控制 `/minecart` 与 `/fga minecart`；`false` 隐藏命令，`true`/`0` 允许所有玩家。 |
| `vehicleStopOnDismount` | 枚举 | `false` | `false`、`minecart`、`boat`、`all`、`custom` | 全部支持版本 | 驾驶者离开载具时清除水平速度；`custom` 使用 `/vehicleStop` 保存的个人设置。 |
| `voidWorldGeneration` | 布尔 | `false` | `false`、`true` | 全部支持版本 | 让新生成区块为空白，同时保留群系和结构定位数据；已有区块不变。 |
| `terrainRegenerationCommandPermission` | 权限 | `ops` | `false`、`true`、`ops`、`0-4` | 1.21-26.2 | 控制 `/regenerateTerrain` 与 `/fga regenerateTerrain`，包括有破坏性的清除和重生成任务。 |
| `fullShulkerBoxCrafting` | 字符串 | `false` | `false`、`only64`、`any` | 1.21+ | 使用服务器当前普通配方处理满潜影盒；`only64` 要求恰好满盒且产出为整数个满盒，`any` 接受任意数量、一次点满并允许最后一个非满盒；1.21+ 同时适配切石机与本模组切石配方，支持多材料、标签、数据包配方、Shift/Q 补货和 AMS 54 格大潜影盒软兼容。 |
| `spectatorFreeTeleport` | 布尔 | `false` | `false`、`true` | 1.21+ | 允许非 OP 旁观者只传送自己。 |
| `PlayerTpEndControl` | 枚举 | `false` | `false`、`true`、`control` | 1.21+ | 控制玩家通过进入末地门、末地主岛出口和末地折跃门传送；`true` 全部阻止，`control` 使用 `/playertpend` 的个人设置，未设置默认允许，非玩家实体不受影响。 |
| `clientDimensionIds` | 列表 | `[overworld,the_nether,the_end]` | 三个客户端维度 ID | 1.21.1+ | 修改客户端看到的维度 ID，不改变服务端维度。 |
| `removeDialogWarning` | 布尔 | `false` | `false`、`true` | 1.21.8+ | 移除服务端发送的命令/对话框确认警告。 |
| `restorePre26BeeCollisionBox` | 布尔 | `false` | `false`、`true` | 26.2 | 恢复 26.2 之前的蜜蜂碰撞箱。 |

## 村民、生物与掉落物

| 规则 | 类型 | 默认值 | 可选值 | 生效版本 | 说明 |
|---|---|---|---|---|---|
| `villagerBreedingAnimalization` | 枚举 | `false` | `false`、`true`、`only` | 全版本 | 控制玩家直接喂养村民；`only` 只允许玩家喂养。 |
| `babyMobNoGrowth` | 字符串 | `false` | `false`、`true`、`mini`、自定义名称 | 1.21-26.2 | `true` 阻止所有可成长幼体长大；`mini` 是同名名称模式预设；自定义值仅锁定完整自定义名称严格匹配且区分大小写的幼体，包括蝌蚪。 |
| `resilientPlants` | 字符串 | `false` | `false`、`true`、`[]`、方块 ID 列表 | 1.21.1+ | `true` 让 `BushBlock` 植物忽略原版存活限制；列表可选择仙人掌、甘蔗、竹子、藤蔓和水生植物等受支持植物。 |
| `resilientBlocks` | 字符串 | `false` | `false`、`[]`、方块 ID 列表 | 1.21-26.2 | 列表中的自定义方块被放置时不检查下方方块类型，收到更新时不检查自身状态，可悬空放置并保持不掉落。 |
| `comparatorThroughBlocks` | 方块列表 | `false` | `false`、`[chain]`、`[piston]`、`[chain,piston]`、自定义方块 ID 列表 | 1.21+ | 允许比较器隔着配置的前方方块读取后一格容器的模拟信号，不改变方块本身红石行为。 |
| `shulkerBedrockDuplication` | 布尔 | `false` | `false`、`true` | 1.21+ | 潜影贝被潜影贝子弹（自己的或其它潜影贝的）击杀时，必定在原地重新生成一只潜影贝，移植基岩版行为。 |
| `shulkerBedrockLooting` | 布尔 | `false` | `false`、`true` | 1.21+ | 潜影壳掉落同步基岩版：固定 50% 概率掉落，掉落时均匀掉落 1 至 1+抢夺等级 个潜影壳。 |
| `shulkerAttackArmorStand` | 枚举 | `false` | `false`、`true`、`pumpkin` | 1.21+ | 允许潜影贝瞄准并射击盔甲架；`true` 攻击所有盔甲架，`pumpkin` 仅攻击头戴雕刻南瓜的盔甲架。 |
| `anvilNoPriorWorkPenalty` | 布尔 | `false` | `false`、`true` | 1.21+ | 取消铁砧重复工作惩罚和 40 级“过于昂贵”限制；保留附魔冲突检查、材料消耗和正常附魔合并费用。 |
| `experienceLevelCost` | 字符串 | `false` | `false`、`29-30`、`0-1` | 1.21+ | 扁平化升级经验消耗。`29-30` 模式在 30 级及以后固定使用 29 到 30 的 107 点经验；`0-1` 模式所有等级固定使用 0 到 1 的 7 点经验。 |
| `villagerDoNotCraftBread` | 布尔 | `false` | `false`、`true` | 1.21-26.2（不含 1.21.3） | 让农民村民处理小麦的表现与 26.3+ 一样，不再把小麦合成面包，不影响其他农民行为 |
| `villagerUpgradeWhileTrading` | 布尔 | `false` | `false`、`true` | 1.21-26.2 | 让村民在交易界面保持打开时继续等待并完成升级，升级后立即刷新等级、经验和交易列表 |
| `villagerPerformanceOptimization` | 枚举 | `false` | `false`、`true`、`ops`、`1-4` | 1.21+ | 启用村民交易/赠礼优化并控制 `/villagerPerformance` 权限。 |
| `hostileMobInventoryAccess` | 布尔 | `false` | `false`、`true` | 全版本 | 空手潜行右键敌对生物时打开其原版装备栏。 |
| `droppedItemStackLimit` | 枚举 | `false` | `false`、`true`、`ops`、`0-4` | 全部支持版本 | 配置地面、玩家背包和容器三类独立堆叠上限，背包或容器上限启用时需要 FGA 客户端，只有地面上限时保持纯服务端。 |
| `droppedItemMergeDistance` | 小数 | `-1` | `-1`、`0-16` | 1.21.1+ | 设置地面物品水平合并距离；`-1` 保持原版。 |
| `unlimitedFillCommands` | 布尔 | `false` | `false`、`true` | 1.21.8+ | 移除 `/fill` 和 `/fillbiome` 体积限制，保留原版安全检查。 |
| `preStackDroppedItems` | 布尔 | `false` | `false`、`true` | 1.21-26.2 | 启用 `/dropPreStack` 的生物、方块和容器掉落预堆叠。 |
| `zombifiedPiglinDropReduction` | 枚举 | `false` | `false`、`goldEquipment`、`rottenFlesh`、`all` | 全版本 | 减少僵尸猪灵指定掉落。 |
| `entityDropRemoval` | 权限 | `false` | `false`、`true`、`ops`、`0-4` | 1.21.1 | 按生物 ID 配置去除指定死亡掉落物或六个装备槽掉落，使用 `/entityDropRemoval` 管理。 |
| `piglinBarterItemExclusions` | 列表 | `false` | `false`、`ironBoots`、`potions`、物品 ID 列表 | 全版本 | 排除指定猪灵 barter 结果。 |

`babyMobNoGrowth` 是纯服务端规则。`mini` 预设仅匹配自定义名称完整等于小写 `mini` 的幼体，`Mini` 不匹配。其他名称模式只读取实体明确设置的自定义名称，并按 `Component#getString()` 的完整文本比较；带空格的名称需要用引号传入，例如 `/carpet babyMobNoGrowth "永远年幼"`。规则只阻止自然成长和喂食加速，不拦截管理员使用 `/data` 或 NBT 直接修改年龄。关闭规则后，被冻结的幼体会从当前年龄继续成长。

`shulkerBedrockDuplication` 是纯服务端规则。判定条件是造成致命一击的直接伤害来源为潜影贝子弹（对应基岩版行为），被其它方式（近战、箭、摔落等）击杀不会重生。新潜影贝在受击前位置生成，继承原潜影贝的染色颜色和附着面朝向，为满血全新实体；原潜影贝照常播放死亡动画并掉落战利品。Java 版原版"受击时概率复制"机制不受影响，与本规则叠加生效。

`shulkerBedrockLooting` 是纯服务端规则。Java 版潜影壳掉落是固定掉 1 个、概率随抢夺每级 +6.25%（抢夺 III 68.75%）；基岩版则是固定 50% 概率掉落，掉落时数量在 1 至 1+抢夺等级 之间均匀分布。两种公式的期望值对比（无抢夺/抢夺 I/II/III）：Java 版 0.50/0.56/0.62/0.69，基岩版 0.50/0.75/1.00/1.25。规则开启后潜影贝的战利品表掷骰被替换为基岩版公式；抢夺等级读取击杀者主手武器（与原版战利品上下文的 ATTACKING_ENTITY 一致），`doMobLoot` 游戏规则与 `/summon` 的 CanPickUpLoot 等原版门槛不受影响；无抢夺时两种公式完全一致（50% 掉 1 个）。

`shulkerAttackArmorStand` 是纯服务端规则。原版潜影贝的目标选择只针对玩家和实现 `Enemy` 接口的生物，盔甲架两者都不是，永远不会被瞄准。本规则为潜影贝追加一个低优先级目标：`true` 时索敌范围内（跟随距离内）的所有盔甲架，`pumpkin` 时仅头部装备槽为雕刻南瓜的盔甲架——生存模式下玩家右键即可给盔甲架戴上南瓜头，无需命令。盔甲架的探测范围与原版潜影贝探测玩家的方式完全一致（全向跟随距离 + 视线判定），因此贴地、天花板和贴墙的潜影贝都能像锁定假人玩家一样锁定盔甲架。锁定目标后由原版 `ShulkerAttackGoal` 正常开火、发射子弹；对玩家的优先瞄准、报复目标、和平难度不攻击等原版行为全部保留（生存模式玩家在 16 格内会先被瞄准，创造模式玩家不会被瞄准）。切换规则值即时生效，无需重启或重 summon；目标盔甲架死亡或不再满足条件时会立即释放目标并重新索敌。旧版本使用的 `onlyWithPumpkinHead`、`onlyWithShulkerShell` 选项名会自动归一化为 `pumpkin`，旧存档配置无需手动迁移。

`resilientBlocks` 是纯服务端规则。列表中的方块在放置与存活检查中跳过原版 `canSurvive` 的支撑判定，因此可以放在空中、植物或任何非支撑方块上；同时这些方块会忽略方块更新：不执行 `updateShape` 自检（类似火把失去支撑弹落、花盆变化等）、不执行邻居更新的自身状态检查（仙人掌、栅栏等），放置时也不再调度下落（沙子、沙砾等悬空不掉）。规则只作用于列表内的方块，未列出的方块行为不变；移除方块 ID 用 `/carpet resilientBlocks []` 即可。列表项为方块 ID，命名空间可省略（如 `sand` 等价 `minecraft:sand`），保存时会归一化为排序后的完整 ID 列表。

旧版 `preStackMobDeathDrops` 与 `preStackMobDeathDropsRange` 已隐藏，仅保留旧存档兼容；新配置使用 `/dropPreStack entity ...`。

`entityDropRemoval` 是纯服务端规则，在 Minecraft 1.21+ 注册。使用 `/entityDropRemoval set <生物ID> <物品ID|allEquipment>` 增加配置，`remove` 删除单项，`list` 查看全部配置，`list <生物ID>` 查看默认战利品表和当前可识别的掉落配置。指定物品会过滤该生物死亡流程中的战利品表、装备和 `spawnAtLocation` 匹配物品；`allEquipment` 只过滤头盔、胸甲、护腿、靴子、主手和副手，不会误删战利品表中的同名物品。规则为 `false` 时命令隐藏且掉落保持原版；`true`、`ops` 或 `0-4` 控制命令权限。配置保存于 `world/config/carpetfgaaddition/entity-drop-removal.json`，采用原子替换，损坏文件会保留并在本次运行禁用配置。列表中的红色减号可点击删除对应配置。

## 深板岩切石与玩家加载距离

| 规则 | 类型 | 默认值 | 可选值 | 生效版本 | 说明 |
|---|---|---|---|---|---|
| `deepslateStonecuttingRecipes` | 布尔 | `false` | `false`、`true` | `1.21+` | 让深板岩在切石机中的表现与 26.1+ 一样；只控制 FGA 新增配方，不过滤原版、数据包和模组配方。 |
| `woodStonecuttingRecipes` | 布尔 | `false` | `false`、`true` | `1.21-26.2`（不含 1.21.3） | 允许使用切石机合成木制品；原木或菌柄可切出 4 个楼梯或 8 个台阶，木板可切出 1 个楼梯或 2 个台阶，竹马赛克台阶为 4 个、竹马赛克楼梯为 2 个；竹块、去皮竹块和 9 根竹子都可作为竹块配方的等价输入，木桶和箱子等配方按实际输入数量由服务端校验 |
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
| `fakePlayerItemSort` | 布尔 | `false` | `false`、`true` | `1.21-26.2`（不含 1.21.3） | 启用假人全物品分类核心，模式、白名单、潜影盒和语言由 `/fakePlayerItemSort` 管理；补货、重构、磁盘缓存、网页和线程参数仅在 `1.21.1` 启用。 |

分类配置保存在 `world/config/carpetfgaaddition/fake-player-item-sort.json`。`/fakePlayerItemSort mode summon` 使用在线 Carpet 假人，`mode quickopen` 直接读写离线 playerdata。旧版 `fakePlayerItemSort*` Carpet 配置只在首次启动时迁移到该 JSON，不再注册为规则。

## 满潜影盒合成

`fullShulkerBoxCrafting` 在 Minecraft `1.21-26.2` 提供复杂配方实现，纯服务端可用。把分别装满对应材料的潜影盒按普通配方形状放入玩家 2×2 合成栏或工作台，系统会直接查询服务器当前配方表，因此支持多材料、标签材料、数据包和模组普通合成配方。允许输入堆叠的同组件满盒，每次从各配方格消耗一个；背包中的堆叠空盒也会按实际需求扣除。所有输入必须可堆叠、每盒容量相同并全部耗尽，主结果及配方返还物都必须恰好装成整数个满盒。盒子颜色、自定义名称和其他盒子组件会保留。检测到 Carpet AMS Addition 且 `largeShulkerBox` 开启时按 54 格大潜影盒完成输入判定、容量换算和成品填充，关闭或未安装 AMS 时仍按原版 27 格处理。QuickCraft 的 `Alt+C`、结果槽 Shift 移动和 Q 丢出会在整次操作结束后，把背包中点击前已有的匹配满盒一次性平均分配到对应配方格；Shift+左键会先执行合成栏原有材料能够完成的所有满盒配方，补货不会加入本次合成循环，普通左键不补货，客户端不需要安装 FGA。

Minecraft `1.21.1` 上规则为三档：`only64` 即上述严格行为，但以原版堆叠上限（忽略 FGA 堆叠上限）判定满盒与容量；`any` 放宽为盒内 1～容器堆叠上限的任意数量即可触发，按盒内总量换算合成次数并一次点满，允许最后一个成品盒不满，扣料后剩余材料留在输入盒内，扣到 0 时输入盒变为空盒并优先返还背包；`true` 旧值自动按 `any` 处理。`any` 模式下各输入盒内的物品数量必须相同。

`1.21+` 同样适配切石机。把装单种材料的潜影盒放入切石机输入槽后，配方列表会按盒内物品显示（含本模组 `woodStonecuttingRecipes` 的多输入配方，如竹子×9）；选中配方后结果槽显示 1 个成品盒预览，左键取货或 Shift 移动即完成整次合成：整盒材料按配方消耗量扣除（`any` 模式下余料留在盒内），成品第一个盒随取货给出，其余成品盒与空盒优先放入背包、背包满时掉落。支持输入槽叠放多个同组件满盒，每次消耗其中一个；`only64` 模式下产出必须恰好装成整数个满盒，否则该配方不生成预览。切石机的配方列表由客户端菜单计算：单人游戏天然一致；远程服务器上需要客户端安装 FGA 且本地规则值与服务端一致才能显示列表（服务端扣料与产出始终由服务端权威校验，不依赖客户端）。

## 配置文件

世界配置位于 `world/config/carpetfgaaddition/`。升级时会从旧目录 `world/carpet/carpetfgaaddition/` 安全迁移；迁移成功后旧文件改名为 `.migrated`。损坏文件会保留原位置，不会覆盖新文件。

`inventoryAdvancementOptimization` 是隐藏的内部兼容字段，不注册为 Carpet 规则，也没有可用的独立命令；其行为由当前版本实现和相关生命周期自动控制。
