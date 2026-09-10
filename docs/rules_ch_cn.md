# Carpet FGA Addition 规则

> 文档版本：`1.5.8`

所有规则通过 `/carpet <规则名> <值>` 管理。未特别说明时，规则默认关闭

提示：可以使用 `Ctrl+F` 快速查找自己想要的规则

### 夺舍操控玩家(playerPossession) · [相关指令](commands.md#cmd-player-possession)

使用 `/player <名字> possess` 夺舍操控在线玩家或假人，无需安装 FGA 客户端
规则值为 `false` 时关闭本功能
规则值为 `true` 时所有玩家可控制假人或真人
规则值为 `onlyfake` 时所有玩家仅可控制假人
规则值为 `opreal` 时普通玩家仅可控制假人，OP 可控制假人或真人
规则值为 `ops` 时仅 OP 可控制假人或真人
本功能同时遵守 Carpet `commandPlayer` 入口权限，功能来源为 PlayerControl 模组实现（CC0-1.0），本功能为纯服务端实现

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`，`true`，`onlyfake`，`opreal`，`ops`
- 分类：`FGA`，`特性`，`命令`

## 假人与通用功能

### 轻松放置实体(quickCraftEasyPlaceEntities)

允许 QuickCraft 客户端请求放置投影实体；服务端负责校验距离、实体数据和材料，并在成功生成时扣除材料

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.21-26.2`

### 假人名字最大长度(fakePlayerNameLength)

设置假人玩家名字的最大字符长度（1-128），超过 16 字符的名字会以兼容别名发送给客户端，无需客户端安装模组

- 类型：`整数`
- 默认值：`-1`
- 参考选项：`-1`、`1-128`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 假人范围控制(fakePlayerRangeControl) · [相关指令](commands.md#cmd-player-range)

启用假人的区域放置、方块右键、区域破坏、持续执行和基础寻路功能，默认关闭

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`全版本`

### 末地折跃门再生(endGatewayRegeneration)

再生已被破坏的原版末地折跃门，只恢复折跃门方块和自身数据，不改变周围方块

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 流浪商人不消失(wanderingTraderNoDespawn) · [相关指令](commands.md#cmd-villager-performance)

false 保持原版；true 使全部流浪商人不消失；controlled 仅保护命中 /villagerPerformance wanderingTrader 名称或脚下方块名单的流浪商人

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`true`、`controlled`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 假人档案预加载(fakePlayerProfilePreload)

在召唤假人前异步查询玩家档案，避免正版验证请求阻塞服务端主线程；适用于 Minecraft 1.21 及以上版本

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`always`、`adaptive`
- 分类：`FGA`，`特性`
- 生效版本：`1.21.1`

### FGA Unicode 指令参数支持(fgaUnicodeArgumentsSupport)

允许未加引号的指令参数包含中文及其他 Unicode 字符；此独立 FGA 规则不与 YACA 的同名规则冲突

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`全版本`

### 配方书始终解锁(recipeBookAlwaysUnlocked)

玩家进入服务器时自动获得全部已注册配方，每名玩家自动发放冷却一分钟，同时保留已保存的配方解锁数据

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 背包进度触发优化(inventoryAdvancementOptimization)

为 inventory_changed 进度使用精确物品候选索引；false 保持原版；exact 保持原版匹配语义，并在发现异常时安全回退

- 类型：`字符串`
- 默认值：`false`
- 参考选项：`false`、`exact`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 玩家生命值显示(playerHealthDisplay) · [相关指令](commands.md#cmd-player-health)

在多人游戏列表名称最右侧显示生命值；true 显示全部玩家，false 仅向 /log playerHealth 订阅者显示，nofake 不显示假人血量

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`true`、`false`、`nofake`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 展示框方块化(itemFrameBlockification)

将展示框移出服务端实体 tick 调度并在支撑方块变化时验证，同时保留原版渲染、交互、掉落、地图与比较器输出

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.21.1`

### 烟花矿车加速(fireworkMinecartBoost) · [相关指令](commands.md#cmd-minecart)

允许玩家乘坐普通矿车时使用烟花火箭，以可配置速度维持满速后线性减速

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.21.1`

### 锁链绑定矿车(chainMinecartBinding) · [相关指令](commands.md#cmd-minecart)

允许使用锁链将普通矿车连接为可持久保存的线性列车

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.21.1`

### 矿车功能命令权限(minecartFeatureCommandPermission) · [相关指令](commands.md#cmd-minecart)

控制矿车烟花加速与锁链列车配置命令的使用权限

- 类型：`权限`
- 默认值：`false`
- 参考选项：`false`、`true`、`ops`、`0-4`
- 分类：`FGA`，`特性`，`命令`
- 生效版本：`1.21.1`

### 玩家离开载具急停(vehicleStopOnDismount) · [相关指令](commands.md#cmd-vehicle-stop)

控制驾驶者离开矿车或船时是否立即清除载具水平速度

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`minecart`、`boat`、`all`、`custom`
- 分类：`FGA`，`特性`
- 生效版本：`全部支持版本`

### 虚空世界生成(voidWorldGeneration) · [相关指令](commands.md#cmd-regenerate-terrain)

让新生成区块为空白，同时保留群系和结构定位数据

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`全部支持版本`

### 地形重生成命令权限(terrainRegenerationCommandPermission) · [相关指令](commands.md#cmd-regenerate-terrain)

控制地形重生成与虚空清除命令的使用权限

- 类型：`权限`
- 默认值：`ops`
- 参考选项：`false`、`true`、`ops`、`0-4`
- 分类：`FGA`，`特性`，`命令`
- 生效版本：`1.21-26.2`

### 满潜影盒合成(fullShulkerBoxCrafting)

允许装单种物品的潜影盒按照对应普通合成/切石配方直接合成为成品盒；only64 要求恰好满盒且产出为整数个满盒，any 接受任意数量并一次点满、允许最后一个非满盒

- 类型：`字符串`
- 默认值：`false`
- 参考选项：`false`、`only64`、`any`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 旁观者免权限自身传送(spectatorFreeTeleport)

允许旁观模式玩家使用 /tp 与 /teleport，但只能传送自己；除非 TIS 或 AMS 的禁止管理员作弊规则开启，否则 OP 保持原版完整传送权限

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 地狱门不发光(netherPortalNoLight)

控制地狱门是否发出光照，false保持原版，true关闭所有地狱门光照，onlynew仅关闭规则启用后新生成的地狱门光照，关闭规则不会主动刷新已有地狱门

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`true`、`onlynew`
- 分类：`FGA`，`特性`
- 生效版本：`1.21.1`

### 玩家末地门传送控制(PlayerTpEndControl) · [相关指令](commands.md#cmd-playertpend)

控制玩家通过末地传送门、末地主岛出口和末地折跃门传送：false 保持原版，true 阻止所有玩家传送，control 按 /playertpend 的个人设置决定

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`true`、`control`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 客户端维度 ID(clientDimensionIds)

映射客户端所见的主世界、下界和末地维度 ID，用于分离小地图和 Voxy 数据，不修改服务端维度

- 类型：`列表`
- 默认值：`[overworld,the_nether,the_end]`
- 参考选项：`三个客户端维度 ID`
- 分类：`FGA`，`特性`
- 生效版本：`1.21.1+`

### 移除命令确认警告(removeDialogWarning)

移除服务器发送的运行命令点击事件和对话框操作的确认警告；仅在 Minecraft 1.21.8 及更高版本可用

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.21.8+`

### 恢复 26.2 前蜜蜂碰撞箱(restorePre26BeeCollisionBox)

将蜜蜂碰撞箱恢复为 Minecraft 26.2 前的大小：宽 0.7 格、高 0.6 格

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`26.2`


## 村民、生物与掉落物

### 村民繁殖动物化(villagerBreedingAnimalization)

潜行右键喂食成年村民可产生繁殖意愿；喂食幼年村民可像其他动物一样加快成长

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`true`、`only`
- 分类：`FGA`，`生存`
- 生效版本：`全版本`

### 幼年生物不长大(babyMobNoGrowth)

阻止全部幼年生物或自定义名称完全匹配且区分大小写的幼年生物成长，包括蝌蚪

- 类型：`字符串`
- 默认值：`false`
- 参考选项：`false`、`true`、`mini`、自定义名称`
- 分类：`FGA`，`生存`
- 生效版本：`1.21-26.2`

### 坚韧的花草(resilientPlants)

让匹配的花草忽略原版存活限制，可以放在空气位置或任意方块上

- 类型：`字符串`
- 默认值：`false`
- 参考选项：`false`、`true`、`[]`、方块 ID 列表`
- 分类：`FGA`，`生存`
- 生效版本：`1.21.1+`

### 坚韧方块(resilientBlocks)

自定义方块被放置时不检查下方方块类型，收到更新时不检查自身状态

- 类型：`字符串`
- 默认值：`false`
- 参考选项：`false`、`[]`、方块 ID 列表`
- 分类：`FGA`，`生存`
- 生效版本：`1.21-26.2`

### 比较器隔方块检测容器信号(comparatorThroughBlocks)

允许比较器隔着配置的前方方块读取后方容器信号，例如 [chain,piston]，不改变其他红石行为

- 类型：`方块列表`
- 默认值：`false`
- 参考选项：`false`、`[chain]`、`[piston]`、`[chain,piston]`、自定义方块 ID 列表`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 基岩版潜影贝复制(shulkerBedrockDuplication)

潜影贝被潜影贝子弹（自己的或其它潜影贝的）击杀时，必定在原地重新生成一只潜影贝，移植基岩版行为

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 潜影贝基岩版掠夺(shulkerBedrockLooting)

潜影壳掉落同步基岩版：固定 50% 概率掉落，掉落时均匀掉落 1 至 1+抢夺等级 个潜影壳

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 潜影贝攻击盔甲架(shulkerAttackArmorStand)

允许潜影贝瞄准并射击盔甲架：true 攻击所有盔甲架，pumpkin 仅攻击头戴雕刻南瓜的盔甲架

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`true`、`pumpkin`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 取消铁砧附魔惩罚(anvilNoPriorWorkPenalty)

取消铁砧重复工作惩罚和过于昂贵限制，保留附魔冲突检查和材料消耗

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 附魔等级上限增加(enchantmentLevelLimitIncrease)

false 或 0 保持原版上限；直接输入数字 N 让所有附魔的原版等级上限增加 N，最高保存为255级

- 类型：`字符串`
- 默认值：`false`
- 参考选项：`false`、`0`、`1`、整数 `0-254`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 附魔升级相加(enchantmentLevelAddition)

铁砧合并同种附魔时直接相加等级，例如2+2变为4；输入达到上限时不生成结果，相加超过上限时结果封顶为上限

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 经验升级消耗扁平化(experienceLevelCost)

false 使用原版经验曲线；29-30 让30级后每级升级消耗经验与29到30一样；0-1 让每级升级消耗经验与0到1一样

- 类型：`字符串`
- 默认值：`false`
- 参考选项：`false`、`29-30`、`0-1`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 村民不合成面包(villagerDoNotCraftBread)

让农民村民处理小麦的表现与26.3+一样，不再把小麦合成面包

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 交易时村民升级(villagerUpgradeWhileTrading)

让村民升级时的表现与26.3+一样，无需关闭交易界面即可等待并完成升级

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`生存`
- 生效版本：`1.21-26.2`

### 村民性能优化(villagerPerformanceOptimization) · [相关指令](commands.md#cmd-villager-performance)

启用村民性能优化并控制 /villagerPerformance 权限：true 允许所有人，ops 需要 OP 2，1-4 表示最低权限等级

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`true`、`ops`、`1-4`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 敌对生物物品栏访问(hostileMobInventoryAccess)

双手空手潜行右键敌对生物，打开其六个装备槽

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`生存`
- 生效版本：`全版本`

### 地面掉落物堆叠上限(droppedItemStackLimit) · [相关指令](commands.md#cmd-dropped-item-stack-limit)

启用地面掉落物、玩家背包和容器的独立服务端堆叠上限；使用 /droppedItemStackLimit 配置，最大数量为 1000000000；false 关闭功能，true 允许所有玩家管理，ops 或 0-4 设置管理命令的最低权限等级

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`true`、`ops`、`0-4`
- 分类：`FGA`，`生存`
- 生效版本：`全部支持版本`

### 地面掉落物合并距离(droppedItemMergeDistance)

修改地面掉落物实体的水平合并搜索距离；-1 保持原版 0.5 格，垂直搜索范围不变

- 类型：`小数`
- 默认值：`-1`
- 参考选项：`-1`、`0-16`
- 分类：`FGA`，`生存`
- 生效版本：`1.21.1+`

### 解除填充命令上限(unlimitedFillCommands)

解除 /fill 与 /fillbiome 的体积上限；区块仍须加载，其他原版检查保持不变

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`生存`，`命令`
- 生效版本：`1.21.8+`

### 掉落物预堆叠(preStackDroppedItems) · [相关指令](commands.md#cmd-drop-pre-stack)

开启由 /dropPreStack 配置的生物死亡与方块掉落物预堆叠；新命令条目默认范围为 1

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`生存`
- 生效版本：`1.21-26.2`

### 生物死亡掉落预堆叠(preStackMobDeathDrops)

指定生物死亡时立即预堆叠兼容掉落物；使用 false 关闭，或填写 [zombified_piglin,zombie] 形式的列表

- 类型：`字符串`
- 默认值：`false`
- 参考选项：`false`、`[zombified_piglin]`
- 分类：`FGA`，`特性`
- 生效版本：`1.21-26.2`

### 生物死亡掉落预堆叠范围(preStackMobDeathDropsRange)

设置旧版生物死亡预堆叠的三维范围，可选 0-16 格，默认 1.5；请使用 /dropPreStack 迁移到逐实体范围

- 类型：`小数`
- 默认值：`1.5`
- 参考选项：`0`、`1`、`3`、`8`、`16`
- 分类：`FGA`，`特性`
- 生效版本：`1.21-26.2`

### 僵尸猪灵掉落物自定义去除(zombifiedPiglinDropReduction)

自定义去除僵尸猪灵的指定掉落物

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`goldEquipment`、`rottenFlesh`、`all`
- 分类：`FGA`，`生存`
- 生效版本：`全版本`

### 生物掉落物自定义去除(entityDropRemoval) · [相关指令](commands.md#cmd-entity-drop-removal)

按生物配置要去除的死亡掉落物；false 关闭命令，true 允许所有人，ops 或 0-4 控制配置权限

- 类型：`权限`
- 默认值：`false`
- 参考选项：`false`、`true`、`ops`、`0-4`
- 分类：`FGA`，`生存`，`命令`
- 生效版本：`1.21+`

### 猪灵交易物品自定义去除(piglinBarterItemExclusions)

自定义去除猪灵交易返回的指定物品

- 类型：`列表`
- 默认值：`false`
- 参考选项：`false`、`ironBoots`、`potions`、物品 ID 列表`
- 分类：`FGA`，`生存`
- 生效版本：`全版本`



## 深板岩切石与玩家加载距离

### 深板岩切石配方(deepslateStonecuttingRecipes)

让深板岩在切石机中的表现与26.1+一样

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 木材切石机配方(woodStonecuttingRecipes)

允许使用切石机合成木制品

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 玩家加载距离(playerLoadDistance) · [相关指令](commands.md#cmd-player-load-distance)

控制每名玩家的区块发送与跟踪距离，不改变模拟距离

- 类型：`权限字符串`
- 默认值：`false`
- 参考选项：`false`、`true`、`ops`、`0-4`
- 分类：`FGA`，`特性`，`命令`
- 生效版本：`1.21.1`

### 试炼刷怪笼等效人数(trialSpawnerPlayerMultiplier)

让每名符合筛选条件的试炼参与玩家按指定人数计算，仅影响试炼刷怪和奖励规模

- 类型：`整数`
- 默认值：`100`
- 参考选项：`1-10000`
- 分类：`FGA`，`特性`，`命令`
- 生效版本：`1.21-26.2`

### 试炼刷怪笼多倍触发(trialSpawnerPlayerFilter)

选择哪些玩家触发试炼等效人数：false、true、bot_ 或自定义名称前缀

- 类型：`字符串`
- 默认值：`false`
- 参考选项：`false`、`true`、`bot_`、自定义前缀`
- 分类：`FGA`，`特性`，`命令`
- 生效版本：`1.21-26.2`

### 试炼截停命令权限(trialStopCommandPermission) · [相关指令](commands.md#cmd-trial-stop)

启用并控制 /trialStop 截停刷新命令，支持 false、true、ops 和 0-4

- 类型：`权限字符串`
- 默认值：`false`
- 参考选项：`false`、`true`、`ops`、`0-4`
- 分类：`FGA`，`特性`，`命令`
- 生效版本：`1.21-26.2`



## 1.21+ 假人全物品分类

以下规则在 Minecraft `1.21-26.2` 注册：

### 假人物品分类(fakePlayerItemSort) · [相关指令](commands.md#cmd-fake-player-item-sort)

启用假人物品分类，使用 /fakePlayerItemSort 管理模式和分类配置

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`，`命令`
- 生效版本：`1.21+`


分类配置保存在 `world/config/carpetfgaaddition/fake-player-item-sort.json`。`/fakePlayerItemSort mode summon` 使用在线 Carpet 假人，`mode quickopen` 直接读写离线 playerdata。旧版 `fakePlayerItemSort*` Carpet 配置只在首次启动时迁移到该 JSON，不再注册为规则。


## 配置文件

世界配置位于 `world/config/carpetfgaaddition/`。升级时会从旧目录 `world/carpet/carpetfgaaddition/` 安全迁移；迁移成功后旧文件改名为 `.migrated`。损坏文件会保留原位置，不会覆盖新文件。
