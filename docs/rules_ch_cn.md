# Carpet FGA Addition 规则

> 文档版本：`1.5.8`

所有规则通过 `/carpet <规则名> <值>` 管理。未特别说明时，规则默认关闭

提示：可以使用 `Ctrl+F` 快速查找自己想要的规则

### 夺舍操控玩家(playerPossession) · [相关指令](commands.md#cmd-player-possession)

使用 `/player <名字> possess` 夺舍操控在线玩家或假人，无需安装 FGA 客户端<br>
使用 `/player <名字> possess stop` 结束自己或被接管者参与的会话<br>
规则值为 `false` 时关闭本功能<br>
规则值为 `true` 时所有玩家可控制假人或真人<br>
规则值为 `onlyfake` 时所有玩家仅可控制假人<br>
规则值为 `opreal` 时普通玩家仅可控制假人，OP 可控制假人或真人<br>
规则值为 `ops` 时仅 OP 可控制假人或真人<br>
本功能同时遵守 Carpet `commandPlayer` 入口权限，功能和主要代码来源于模组 [PlayerControl](https://modrinth.com/mod/playercontrol)（CC0-1.0）本功能为纯服务端实现

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`，`true`，`onlyfake`，`opreal`，`ops`
- 分类：`FGA`，`特性`，`命令`
- 生效版本：`1.21.1`

## 假人与通用功能

### 轻松放置实体(quickCraftEasyPlaceEntities)

允许 [QuickCraft](https://modrinth.com/mod/quickcraft-yiyihehe) 客户端请求放置投影实体；服务端负责校验距离、实体数据和材料，并在成功生成时扣除材料

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 假人名字最大长度(fakePlayerNameLength)

原版游戏玩家名称只允许16字符，本模组可以更改字符限制为（1-128），客户端可选，超过 16 字符的名字会以兼容别名发送给未安装本模组的客户端(例如half...)

- 类型：`整数`
- 默认值：`-1`
- 参考选项：`-1`、`1-128`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 假人范围控制(fakePlayerRangeControl) · [相关指令](commands.md#cmd-player-range)

启用假人的区域放置、方块右键、区域破坏等功能(功能未完善，不建议使用)

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.16.5+`

### 末地折跃门再生(endGatewayRegeneration)

允许已被破坏的末地折跃门再生，击杀末影龙即可，只恢复折跃门方块和自身数据，不改变周围方块

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 流浪商人不消失(wanderingTraderNoDespawn) · [相关指令](commands.md#cmd-villager-performance)

false：保持原版<br>
true：使全部流浪商人不消失<br>
controlled：仅保护命中 /villagerPerformance wanderingTrader 命名或脚下方块名单的流浪商人不消失

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`true`、`controlled`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 假人档案预加载(fakePlayerProfilePreload)

在召唤假人前异步查询玩家档案，避免正版验证请求阻塞服务端主线程<br>
false：保持 Carpet 原有的同步档案查询<br>
always：每次召唤假人前都异步预加载档案<br>
adaptive：第一次召唤保持原行为；30 秒内第二次召唤开启 2 分钟预加载窗口，窗口内每次召唤都会重置剩余时间

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`always`、`adaptive`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### FGA Unicode 指令参数支持(fgaUnicodeArgumentsSupport)

允许未加引号的指令参数包含中文及其他 Unicode 字符，可以实现中文假人等操作<br>
来源于[YACA](https://modrinth.com/mod/yaca),由于其在26.1+版本停更遂移植，由于怕26.1以下版本于原本的YACA原此功能冲突，随改规则名

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.16.5+`

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

在多人游戏列表名称最右侧显示生命值<br>
true 显示全部玩家<br>
false 仅向 /log playerHealth 订阅者显示<br>
nofake 不显示假人血量

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

允许使用锁链将普通矿车连接为可持久保存的线性列车(不建议使用)
- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.21.1`

### 矿车功能命令权限(minecartFeatureCommandPermission) · [相关指令](commands.md#cmd-minecart)

控制矿车烟花加速与锁链列车配置命令的使用权限
false：禁用相关命令<br>
true：允许所有玩家使用<br>
ops：需要 OP 2 及以上<br>
0-4：设置命令的最低权限等级

- 类型：`权限`
- 默认值：`false`
- 参考选项：`false`、`true`、`ops`、`0-4`
- 分类：`FGA`，`特性`，`命令`
- 生效版本：`1.21.1`

### 玩家离开载具急停(vehicleStopOnDismount) · [相关指令](commands.md#cmd-vehicle-stop)

驾驶者离开矿车或船时立即清除载具速度<br>
false：关闭急停<br>
minecart：驾驶者离开矿车时清除水平速度<br>
boat：驾驶者离开船时清除水平速度<br>
all：同时处理矿车和船<br>
custom：按 `/vehicleStop` 为每名玩家保存的矿车和船设置处理<br>
急停只清除水平速度，保留垂直速度；载具仍有其他玩家乘坐时保留速度

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`minecart`、`boat`、`all`、`custom`
- 分类：`FGA`，`特性`
- 生效版本：`全部支持版本`

### 虚空世界生成(voidWorldGeneration) · [相关指令](commands.md#cmd-regenerate-terrain)

让新生成区块为虚空，同时保留群系和结构定位数据，可以用指令重新生成地形

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`全部支持版本`

### 地形重生成命令权限(terrainRegenerationCommandPermission) · [相关指令](commands.md#cmd-regenerate-terrain)

控制地形重生成与地形清空命令的使用权限
false：禁用相关命令<br>
true：允许所有玩家使用<br>
ops：需要 OP 2 及以上<br>
0-4：设置命令的最低权限等级

- 类型：`权限`
- 默认值：`ops`
- 参考选项：`false`、`true`、`ops`、`0-4`
- 分类：`FGA`，`特性`，`命令`
- 生效版本：`1.21+`

### 满潜影盒合成(fullShulkerBoxCrafting)

允许装单种物品的潜影盒按照对应普通合成/切石配方直接合成为成品盒<br>
false：关闭功能<br>
only64：输入盒必须按原版堆叠上限装满，产物和配方返还物必须恰好组成整数个满盒<br>
any：输入盒内可为 1 至容器堆叠上限的相同数量，按总材料一次完成合成，最后一个成品盒可不满，余料留在输入盒<br>
所有输入盒必须装相同种类、相同数量且容量相同的可堆叠物品
旧版值 `true` 会自动按 `any` 处理

- 类型：`字符串`
- 默认值：`false`
- 参考选项：`false`、`only64`、`any`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 旁观者免权限自身传送(spectatorFreeTeleport)

允许旁观模式玩家使用 `/tp` 与 `/teleport`，但是只能控制自己传送；[TIS](https://modrinth.com/mod/carpet-tis-addition) 或 [AMS](https://modrinth.com/mod/carpet-ams-addition) 的禁止管理员作弊规则未开启时，OP 保持完整传送权限<br>
false：保持原版旁观者传送权限<br>
true：旁观者只能传送自己，不能借此传送其他实体；TIS 或 AMS 禁止管理员作弊规则开启时 OP 也受同样限制

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 地狱门不发光(netherPortalNoLight)

控制地狱门是否发出光照<br>
false：保持原版<br>
true：关闭所有地狱门光照<br>
onlynew：仅规则启用新生成的地狱门无光照<br>
关闭规则不会主动刷新地狱门光照，会自动同步客户端 [MiniHUD](https://modrinth.com/mod/minihud) 的光照显示

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`true`、`onlynew`
- 分类：`FGA`，`特性`
- 生效版本：`1.21.1`

### 玩家末地门传送控制(PlayerTpEndControl) · [相关指令](commands.md#cmd-playertpend)

控制玩家通过末地传送门、末地主岛出口和末地折跃门传送<br>
false：保持原版<br>
true：阻止所有玩家传送<br>
control：按 `/playertpend` 的个人设置决定，未设置的门默认允许传送；此纯服务端规则不阻止非玩家实体

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`true`、`control`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

### 客户端维度 ID(clientDimensionIds)

映射客户端所见的主世界、下界和末地维度 ID，用于分离小地图和 Voxy 数据，不修改服务端维度<br>
使用 `[overworld,the_nether,the_end]` 或按相同顺序填写三个客户端维度 ID，省略命名空间时默认使用 `minecraft`<br>
修改后需要重新连接；不会改变假人召唤、传送、服务端存档或服务端维度

- 类型：`列表`
- 默认值：`[overworld,the_nether,the_end]`
- 参考选项：`三个客户端维度 ID`
- 分类：`FGA`，`特性`
- 生效版本：`1.21+`

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
- 生效版本：`26.2+`


## 村民、生物与掉落物

### 村民繁殖动物化(villagerBreedingAnimalization)

潜行右键喂食成年村民可产生繁殖意愿；喂食幼年村民可像其他动物一样加快成长<br>
false：仅保留原版村民繁殖方式<br>
true：同时保留原版拾取食物和玩家直接喂食两种方式<br>
only：只有玩家直接喂食可以产生繁殖意愿<br>
幼年村民每次消耗 1 个食物；面包的成长加速效果相当于连续喂食 4 个胡萝卜、马铃薯或甜菜根

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`true`、`only`
- 分类：`FGA`，`生存`
- 生效版本：`1.16.5+`

### 幼年生物不长大(babyMobNoGrowth)

阻止幼年生物生长，包括蝌蚪<br>
false：关闭<br>
true：阻止全部幼年生物生长，包括蝌蚪<br>
mini：仅阻止自定义名称完整等于小写 `mini` 的幼体，`Mini` 不匹配<br>
其他值：按实体自定义名称的完整文本、区分大小写匹配；带空格的名称需要用引号传入<br>
只影响自然成长和喂食加速，不拦截 `/data` 或 NBT 直接修改年龄

- 类型：`字符串`
- 默认值：`false`
- 参考选项：`false`、`true`、`mini`、自定义名称`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 坚韧的花草(resilientPlants)

让匹配的花草忽略原版存活限制，可以放在空气位置或任意方块上<br>
false：关闭<br>
true：匹配全部支持的花草候选方块<br>
[]：清空匹配列表<br>
方块 ID 列表：只匹配列表中的方块，命名空间可省略

- 类型：`字符串`
- 默认值：`false`
- 参考选项：`false`、`true`、`[]`、方块 ID 列表`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 坚韧方块(resilientBlocks)

自定义方块被放置时不检查下方方块类型，收到更新时不检查自身状态<br>
false 或 `[]`：关闭<br>
方块 ID 列表：跳过放置时的支撑检查、方块更新时的存活检查和下落调度；命名空间可省略，保存时会归一化为完整 ID 列表

- 类型：`字符串`
- 默认值：`false`
- 参考选项：`false`、`[]`、方块 ID 列表`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 比较器隔方块检测容器信号(comparatorThroughBlocks)

允许比较器隔着配置的前方方块读取后方容器信号，例如 `[chain,piston]`，不改变其他红石行为<br>
false：关闭<br>
方块 ID 列表：允许比较器隔着列表中的方块读取后方容器信号，命名空间可省略

- 类型：`方块列表`
- 默认值：`false`
- 参考选项：`false`、`[chain]`、`[piston]`、`[chain,piston]`、自定义方块 ID 列表`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 基岩版潜影贝复制(shulkerBedrockDuplication)

直接致命伤害来源为潜影贝子弹时必定复制；新潜影贝在受击前位置生成并继承颜色与附着面朝向，原实体仍正常播放死亡动画并掉落战利品，原版受击复制机制不受影响

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 潜影贝基岩版掠夺(shulkerBedrockLooting)

潜影壳掉落同步基岩版：固定 50% 概率掉落，掉落时均匀掉落 1 至 1+抢夺等级 个潜影壳<br>
无抢夺时与 Java 版的期望掉落相同，启用后按基岩版公式替换战利品表掷骰

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 潜影贝攻击盔甲架(shulkerAttackArmorStand)

允许潜影贝瞄准并射击盔甲架<br>
false：保持原版，不攻击盔甲架<br>
true：攻击范围内的所有盔甲架<br>
pumpkin：仅攻击头戴雕刻南瓜的盔甲架

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

启用村民性能优化并控制 `/villagerPerformance` 权限<br>
false：关闭优化并禁用相关命令<br>
true：允许所有玩家使用<br>
ops：需要 OP 2 及以上<br>
1-4：设置命令的最低权限等级

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
- 生效版本：`1.16.5+`

### 地面掉落物堆叠上限(droppedItemStackLimit) · [相关指令](commands.md#cmd-dropped-item-stack-limit)

启用地面掉落物、玩家背包和容器的独立服务端堆叠上限；使用 `/droppedItemStackLimit` 配置，最大数量为 1000000000<br>
false：关闭规则并保持原版上限<br>
true：允许所有玩家管理配置<br>
ops：仅 OP 2 及以上可管理配置<br>
0-4：设置管理命令的最低权限等级

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`true`、`ops`、`0-4`
- 分类：`FGA`，`生存`
- 生效版本：`全部支持版本`

### 地面掉落物合并距离(droppedItemMergeDistance)

修改地面掉落物实体的水平合并搜索距离；`-1` 保持原版 0.5 格，垂直搜索范围不变，规则仍保持注册

- 类型：`小数`
- 默认值：`-1`
- 参考选项：`-1`、`0-16`
- 分类：`FGA`，`生存`
- 生效版本：`1.21.1-26.2`

### 解除填充命令上限(unlimitedFillCommands)

解除 /fill 与 /fillbiome 的体积上限；区块仍须加载，其他原版检查保持不变<br>
false：保持原版体积上限<br>
true：移除体积上限

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`生存`，`命令`
- 生效版本：`1.21+`

### 掉落物预堆叠(preStackDroppedItems) · [相关指令](commands.md#cmd-drop-pre-stack)

开启后由 `/dropPreStack` 配置的生物死亡与方块掉落物预堆叠，新命令条目默认范围为 1

- 类型：`布尔`
- 默认值：`false`
- 参考选项：`false`、`true`
- 分类：`FGA`，`生存`
- 生效版本：`1.21+`

### 僵尸猪灵掉落物自定义去除(zombifiedPiglinDropReduction)

自定义去除僵尸猪灵的指定掉落物<br>
false：保持原版掉落<br>
goldEquipment：去除金制盔甲、金剑和金矛<br>
rottenFlesh：去除腐肉<br>
all：同时去除金制装备和腐肉<br>
金粒和金锭不受影响

- 类型：`枚举`
- 默认值：`false`
- 参考选项：`false`、`goldEquipment`、`rottenFlesh`、`all`
- 分类：`FGA`，`生存`
- 生效版本：`1.16.5+`

### 生物掉落物自定义去除(entityDropRemoval) · [相关指令](commands.md#cmd-entity-drop-removal)

按生物配置要去除的死亡掉落物<br>
false：关闭命令<br>
true：允许所有玩家配置<br>
ops：需要 OP 2 及以上<br>
0-4：设置配置命令的最低权限等级
使用 `/entityDropRemoval set <生物ID> <物品ID>` 添加指定物品，或使用 `allEquipment` 去除六个装备槽掉落<br>
指定物品会过滤战利品表与装备掉落；`allEquipment` 只过滤六个装备槽，不会误删战利品表中的同名物品

- 类型：`权限`
- 默认值：`false`
- 参考选项：`false`、`true`、`ops`、`0-4`
- 分类：`FGA`，`生存`，`命令`
- 生效版本：`1.21+`

### 猪灵交易物品自定义去除(piglinBarterItemExclusions)

自定义去除猪灵交易返回的指定物品<br>
false：保持原版交易<br>
`[ironBoots]`：去除铁靴子<br>
`[potions]`：去除普通、喷溅和滞留药水<br>
`[ironBoots,potions]`：同时去除铁靴子和药水<br>
物品 ID 列表：自定义去除物品，可省略 `minecraft` 命名空间

- 类型：`列表`
- 默认值：`false`
- 参考选项：`false`、`ironBoots`、`potions`、物品 ID 列表`
- 分类：`FGA`，`生存`
- 生效版本：`1.16.5+`



## 深板岩切石与玩家加载距离

### 深板岩切石配方(deepslateStonecuttingRecipes)

让深板岩在切石机中的表现与26.1+一样，可以直接放到切石机里

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

控制每名玩家的区块发送与跟踪距离，不改变模拟距离<br>
false：关闭相关命令<br>
true：允许所有玩家使用<br>
ops：需要 OP 2 及以上<br>
0-4：设置命令的最低权限等级
使用 `/playerLoadDistance help` 查看命令，末尾加 `persistent` 才会跨重启保存<br>
`-1` 仅弱加载中心区块，`0` 强加载中心并保留 3×3 弱加载，`1-32` 设置区块半径，`none` 移除玩家加载

- 类型：`权限字符串`
- 默认值：`false`
- 参考选项：`false`、`true`、`ops`、`0-4`
- 分类：`FGA`，`特性`，`命令`
- 生效版本：`1.21.1`

### 试炼刷怪笼等效人数(trialSpawnerPlayerMultiplier)

让每名符合筛选条件的试炼参与玩家按指定人数计算，仅影响试炼刷怪和奖励规模<br>
范围为 1-10000，默认 100；设置为 1 时保持原版一人规模

- 类型：`整数`
- 默认值：`100`
- 参考选项：`1-10000`
- 分类：`FGA`，`特性`，`命令`
- 生效版本：`1.21-26.2`

### 试炼刷怪笼多倍触发(trialSpawnerPlayerFilter)

选择哪些玩家触发试炼等效人数：false、true、bot_ 或自定义名称前缀<br>
false：关闭多倍计算<br>
true：匹配所有玩家<br>
其他值：按名称区分大小写的前缀匹配

- 类型：`字符串`
- 默认值：`false`
- 参考选项：`false`、`true`、`bot_`、自定义前缀`
- 分类：`FGA`，`特性`，`命令`
- 生效版本：`1.21-26.2`

### 试炼截停命令权限(trialStopCommandPermission) · [相关指令](commands.md#cmd-trial-stop)

启用并控制 `/trialStop` 与 `/fga trialStop` 截停刷新命令<br>
false：禁用命令<br>
true：允许所有玩家使用<br>
ops：需要 OP 2 及以上<br>
0-4：设置最低权限等级；命令只处理已加载区块内的刷怪笼，奖励模式支持 `none`、`reward`、`fast`

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

此模组关于存档的配置与持久化文件位于 `world/config/carpetfgaaddition/`
