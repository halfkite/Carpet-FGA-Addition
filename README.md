# Carpet-FGA-Addition
一些可能用得上的功能

## 解除填充命令上限（全部支持版本）

```text
/carpet unlimitedFillCommands true
```

开启后，`/fill` 与 `/fillbiome` 不再受 `commandModificationBlockLimit` 的体积限制。规则默认关闭；关闭时完全沿用 gamerule。区块加载、世界边界、命令权限和其他原版检查不变。

## 生物死亡掉落预堆叠（全部支持版本）

通过 `preStackMobDeathDrops` 指定需要处理的生物。功能默认关闭，省略命名空间时按 `minecraft:` 解析：

```text
/carpet preStackMobDeathDrops false
/carpet preStackMobDeathDrops [zombified_piglin]
/carpet preStackMobDeathDrops [zombified_piglin,zombie,mymod:custom_mob]
/carpet preStackMobDeathDropsRange 3
```

选中生物死亡时，战利品表、装备和自定义死亡掉落会先按物品及数据组件完全一致进行合并，再按原版死亡时机立即生成。同一游戏刻内、三维距离处于配置范围的后续死亡可继续并入首次生成的兼容实体；范围为 `0` 时只匹配死亡位置处于同一方块的生物。

堆叠数量遵循 `droppedItemStackLimit` 当前的全部、黑名单或白名单策略；该规则关闭时使用物品原版上限。超过上限会拆成多个合法实体。不可堆叠物品、组件不同的物品、经验球、玩家死亡、方块掉落、主动丢物和未选中生物均保持原版行为。

fakePlayerNameLength|假人名称上限更改
默认值 `-1`，沿用原版 16 字符限制；可自定义为 `1-128`。客户端无需安装本模组。

服务端首次同步超过 16 字符的玩家名时，会发送“前 7 个字符 + `...`”的网络别名，保证原版客户端可以安全加入。安装本模组的客户端登录后会自动握手并刷新为完整名称；未安装客户端继续显示短别名。服务端内部和 `/player` 命令始终使用完整名称。客户端和服务端都安装本模组后，`/tp` 等原版实体参数命令也可以直接使用最长 128 字符的玩家名。

## 假人档案预加载（Minecraft 1.21 及以上）

使用 `/carpet fakePlayerProfilePreload <模式>` 控制假人召唤前的正版档案查询：

- `false`：保持 Carpet 原有同步查询，默认值。
- `always`：每次召唤前在有界后台线程中预加载档案，完成后回到服务端主线程继续召唤。
- `adaptive`：第一次召唤保持原行为；30 秒内出现第二次召唤时开启 2 分钟预加载窗口，窗口内每次召唤都会把到期时间延后到当前时刻后 2 分钟。

该规则同时覆盖 `/player <名字> spawn ...` 和其他模组直接调用 Carpet `EntityPlayerMPFake.createFake` 的召唤。相同名字的并发请求会合并，单次请求最多等待 60 秒；失败或超时不会绕过 Carpet 的离线玩家、封禁和白名单检查。

## Unicode 指令参数支持

使用 `/carpet unicodeArgumentsSupport true` 后，未加引号的指令参数可以直接包含中文及其他 Unicode 字符。例如：

```text
/team add 测试队伍
/scoreboard objectives add 中文计分 dummy
```

默认关闭。此功能移植并适配自 Yet Another Carpet Addition。

## 恢复蜜蜂碰撞箱

Minecraft 26.2 将蜜蜂碰撞箱从宽 `0.7`、高 `0.6` 修改为宽 `0.55`、高 `0.5`。使用以下规则可恢复旧尺寸：

```text
/carpet restorePre26BeeCollisionBox true
```

规则默认关闭。切换后会自动刷新当前已加载的蜜蜂。此规则仅在 26.2 注册，在更早版本的 `/carpet` 列表和命令补全中隐藏。

## 客户端维度 ID（Minecraft 1.21 及以上）

使用以下规则依次设置客户端所见的主世界、下界和末地 ID：

```text
/carpet clientDimensionIds [overworld,the_nether,the_end]
/carpet clientDimensionIds [myworld:overworld,myworld:the_nether,myworld:the_end]
```

列表必须恰好包含三个互不重复的合法 ID；省略命名空间时使用 `minecraft:`。该规则只重写发给客户端的登录和切换维度数据，用于让小地图、Voxy 等按自定义 ID 分离世界数据。服务端维度注册、存档、假人召唤、`/tp` 和模组维度不受影响。修改后请重新连接服务器。

## 移除命令确认警告

Minecraft 1.21.8 及更高版本可使用以下规则移除服务器发送的运行命令点击事件和对话框操作的确认警告：

```text
/carpet removeDialogWarning true
```

规则默认关闭。启用后，服务器发送的 `run_command` 操作会作为受模组处理的自定义操作执行，因此只应在信任服务器命令内容时启用。此功能在 1.21.8 及更高版本需要 Fabric API；更早版本不会注册该规则。

## 村民繁殖动物化

使用 `/carpet villagerBreedingAnimalization <模式>` 控制玩家直接喂食村民：

### 村民性能优化（Minecraft 1.21 及以上）

- `/carpet villagerPerformanceOptimization false|true|ops|1|2|3|4`：功能总开关及管理命令权限。
- `/villagerPerformance help`：显示灰色可点击命令和金色注释。
- `/villagerPerformance trade false|ai|static`：关闭、停用普通 AI 或额外固定位置。
- `trade name|block add|remove|list`：管理交易优化的名称和脚下方块条件。
- `/villagerPerformance gift false|true` 与 `gift name|block add|remove|list`：独立管理村庄英雄职业赠礼。
- 交易优化村民无需工作站，在原版工作时段按原版冷却和次数补货。配置保存于存档的 `carpet/carpetfgaaddition/`。

- `false`：仅保留原版村民繁殖方式，默认值。
- `true`：同时保留原版拾取食物和玩家直接喂食。
- `only`：只有玩家直接喂食可以产生繁殖意愿。

潜行右键成年村民时，一次消耗 `3` 个面包，或 `12` 个胡萝卜、马铃薯、甜菜根，使其获得与动物相同的 30 秒繁殖意愿。创造模式不消耗物品；已获得意愿或处于繁殖冷却的村民不会重复消耗食物。

潜行右键幼年村民时，每次消耗 `1` 个面包、胡萝卜、马铃薯或甜菜根，并按原版幼年动物的比例加快成长。面包的效果相当于连续喂食 `4` 个胡萝卜、马铃薯或甜菜根。

## 敌对生物物品栏

启用 `/carpet hostileMobInventoryAccess true` 后，双手空手潜行右键任意敌对生物可打开其装备物品栏。前六格依次对应头部、胸部、腿部、脚部、主手和副手；其余格子禁用。放入的装备直接保存在实体原版装备槽中，不创建额外临时库存。

## 假人区域操作

```text
/player <假人> use range <x y z> to <x y z> [可选参数...]
/player <假人> use continuous range <x y z> to <x y z> [可选参数...]
/player <假人> attack range <x y z> to <x y z> [可选参数...]
/player <假人> attack continuous range <x y z> to <x y z> [可选参数...]
/player <假人> stop
```

- `use` 使用假人主手方块，从低到高填充区域内的空气方块。
- `attack` 从低到高破坏区域内的方块。
- `continuous` 会在区域完成后继续监控，持续放置或破坏后来发生变化的方块。
- `stop` 会停止 Carpet 原生动作和本模组的区域任务。
- 可选参数可以任意排序，输入空格后命令补全会显示尚未使用的参数。
- `pathfinding`：启用后假人会尝试自动走向未完成目标。
- `reach <数值>`：不填使用玩家默认交互距离，最大可填 `64`。
- `airPlace`：允许没有相邻支撑方块时凭空放置。
- `ignoreObstruction`：忽略假人与操作位置之间的方块阻挡。
- `placeBlock`：启用方块放置，默认优先使用副手方块，副手没有方块时使用主手。
- `interactBlock`：使用主手物品对区域内已有方块执行右键，可用于骨粉、蜜脾、铲子等。
- `interactSpeed <次数>`：每个位置每游戏刻执行的右键次数，默认为 `2`，范围为 `1-64`。
- `placeBlock` 与 `interactBlock` 可以单独或同时使用；都不填写时默认为只放置。
- 同一假人的放置任务和破坏任务可以同时运行；再次启动相同类型的任务会替换旧任务。
- `/carpet fakePlayerRangeControl true|false`：统一启用或关闭“假人范围控制”功能，默认关闭。
- `interactBlock` 在目标为空气时会右键相邻支撑方块并优先选择正下方，可直接框选作物所在的空气层进行播种。
- 每个游戏刻会尽可能处理所有可达目标，实际交互仍受服务端权限和距离检查限制。
- 输入 `/player <假人> use continuous range help` 或对应的 `attack` 命令可直接查看参数帮助。
- 区域挖掘会在每个游戏刻同时推进所有可达方块的挖掘进度，实际速度由方块硬度、工具和玩家状态决定。

## 地面掉落物堆叠与合并

这两个规则在本项目列出的全部 Minecraft 支持版本中注册，互相独立：

- `droppedItemStackLimit`：地面掉落物堆叠策略开关，默认 `false`。启用后使用 `/droppedItemStackLimit` 配置模式，最大数量为 `8192`。
- `droppedItemMergeDistance`：地面掉落物的水平合并搜索距离。默认 `-1`，保持原版 `0.5` 格；可设置 `0-16` 格，只扩大 X/Z 范围，Y 方向保持原版行为。

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

- `all`：所有原版可堆叠物品使用指定数量。
- `black`：非黑名单物品使用指定数量，黑名单物品保持原版上限。
- `whitelist`：白名单物品使用各自配置的数量，其他物品保持原版上限。
- `list` 显示物品 ID、本地化名称和可点击的 `[-]` 删除按钮。
- 模式和名单保存在服务器全局的 `config/carpet-fga-addition/dropped-item-stack-limit.json`。
- 不可堆叠的工具、盔甲等仍不可合并；不影响背包、容器、漏斗或物品本身的最大堆叠数。

运行时降低数量上限或关闭规则不会截断已有掉落物，缩小距离也不会拆分已合并实体；玩家拾取时背包仍按原版每格上限接收，剩余物品留在地面。

普通漏斗和漏斗矿车会按原版单槽上限分批吸取超量地面物品；容器装满后余量继续留在地面，不会被截断。Carpet 漏斗计数器会累计完整数量。

## 僵尸猪灵掉落物精简

使用 `/carpet zombifiedPiglinDropReduction <模式>` 控制僵尸猪灵的掉落物：

- `false`：保持原版掉落，默认值。
- `goldEquipment`：不掉落金制盔甲、金剑和金矛。
- `rottenFlesh`：不掉落腐肉。
- `all`：同时不掉落上述金制装备和腐肉。

此规则不影响金粒、金锭、经验、诡异菌钓竿及其他非金制装备。

## 猪灵交易物品排除

使用 `/carpet piglinBarterItemExclusions <值>` 排除猪灵交易返回的物品。抽中被排除物品时会重新抽取其他物品。

- `false`：保持原版交易，默认值。
- `[ironBoots]`：排除铁靴子。
- `[potions]`：排除普通、喷溅和滞留药水物品。
- `[ironBoots,potions]`：同时应用两个预设。
- `[iron_boots,ender_pearl]`：使用省略 `minecraft:` 的物品 ID。
- `[minecraft:iron_boots,modid:item_name]`：使用完整物品 ID，可包含其他模组物品。

方括号内使用英文逗号分隔。无效、未注册或会排除全部原版交易结果的配置会被拒绝。

## 多版本构建

支持以下 Minecraft 版本：

- `1.17.1`
- `1.21-1.21.1`
- `1.21.2-1.21.3`
- `1.21.4`
- `1.21.5`
- `1.21.6-1.21.8`
- `1.21.9-1.21.10`
- `1.21.11`
- `26.1-26.1.2`
- `26.2`

执行以下命令构建全部版本：

```text
gradlew.bat buildAllVersions
```

每次构建会在 `build/libs/<时间戳>/` 中生成 8 个正式 JAR。新的构建使用新的时间戳目录，不会删除或覆盖已有历史构建。
