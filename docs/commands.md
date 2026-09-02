# Carpet FGA Addition 命令

> 文档版本：`1.5.6`

## 命令总表

| 命令 | 相关规则 | 权限/版本 | 说明 |
|---|---|---|---|
| `/player` 区域操作 | `fakePlayerRangeControl` | Carpet 玩家权限/全版本 | 让假人执行区域放置、交互、攻击或连续任务。 |
| `/droppedItemStackLimit` | `droppedItemStackLimit` | 规则权限/全部支持版本 | 配置地面、玩家背包和容器的独立堆叠上限，背包或容器上限启用时需要 FGA 客户端。 |
| `/dropPreStack` | `preStackDroppedItems` | 与掉落物上限权限/1.21-26.2 | 配置生物、方块和容器掉落预堆叠。 |
| `/entityDropRemoval` | `entityDropRemoval` | 规则权限/1.21.1 | 配置按生物和物品去除死亡掉落物。 |
| `/villagerPerformance` | `villagerPerformanceOptimization` | 规则权限/1.21+ | 配置村民交易、赠礼和流浪商人保护。 |
| `/fakePlayerItemSort` | 1.21-26.2（不含 1.21.3）分类规则 | `commandPlayer`/1.21-26.2（不含 1.21.3） | 配置分类器核心，网页、重构、补货和线程参数仅在 1.21.1 可用。 |
| `/player <name> bot_sort` | 1.21-26.2（不含 1.21.3）分类规则 | `commandPlayer`/1.21-26.2（不含 1.21.3） | 启动或停止指定假人的分类任务，重构语法仅在 1.21.1 可用。 |
| `/minecart` | 矿车烟花与锁链规则 | `minecartFeatureCommandPermission`/1.21.1 | 配置烟花矿车速度与锁链列车距离。 |
| `/vehicleStop` | `vehicleStopOnDismount` | 自己；OP 可管理在线玩家/全部支持版本 | 配置玩家自己的矿车与船离开急停。 |
| `/regenerateTerrain` | `voidWorldGeneration`、`terrainRegenerationCommandPermission` | 配置权限/1.21-26.2 | 将正常地形重生成或全空气清除任务加入下次重启队列。 |
| `/trialStop` | `trialStopCommandPermission` | 规则权限/1.21-26.2 | 一次性截停并刷新已加载试炼刷怪笼，可选择无奖励、正常奖励或立即奖励。 |
| `/playertpend` | `PlayerTpEndControl` | `control` 模式/1.21+ | 管理每名玩家的三种末地门传送偏好。 |

## `/playertpend`

先执行 `/carpet PlayerTpEndControl control`。`enter` 是进入末地门，`exit` 是末地主岛出口，`gateway` 是末地折跃门。

```text
/playertpend status [玩家]
/playertpend set <enter|exit|gateway> <allow|deny>
/playertpend set <玩家> <enter|exit|gateway> <allow|deny>
/playertpend reset [enter|exit|gateway]
/playertpend reset <玩家> [enter|exit|gateway]
```

偏好按 UUID 保存到 `world/config/carpetfgaaddition/player-tp-end-control.json`。OP 能修改任意在线玩家；非 OP 只能修改自己与在线 Carpet 假人。

## `/regenerateTerrain`

相关规则：`voidWorldGeneration`、`terrainRegenerationCommandPermission`

```text
/regenerateTerrain regenerate box <x1> <z1> <x2> <z2>
/regenerateTerrain clear radius <x> <z> <半径>
/regenerateTerrain regenerate|clear dimension <维度> box|radius ...
/regenerateTerrain confirm <任务ID>
/regenerateTerrain cancel <任务ID>
/regenerateTerrain retry <任务ID>
/regenerateTerrain list [页码]
```

输入使用方块坐标，实际向外取整到完整区块，所有 X/Z 参数都可按 Tab 补全玩家自身坐标或视线指向方块坐标，预览会显示精确区块数和实际生效范围。预览中的绿色确认按钮可直接点击执行确认；确认只加入队列，世界会在下次服务器重启时修改。可以确认多个任务并在下一次重启统一执行。`regenerate` 删除旧区块并按原版正常生成；`clear` 将每个 section 的 palette 通过全空气网络数据替换为空气，同时清除方块实体、非玩家实体、POI、计划刻、高度图和旧光照数据，并清除实际范围水平外沿八格内的相邻流体，覆盖原版水与下界熔岩的最大水平传播距离；含水方块只取消含水状态。清空范围与外沿涉及的 Region 都会在执行前备份。失败任务可在修复原因后使用 `retry` 继续，且不会覆盖原始备份。
| `/log playerHealth` | `playerHealthDisplay` | Carpet Logger/1.21+ | 切换当前玩家的 Tab 生命值订阅。 |
| `/fga` | FGA 功能入口 | 版本门控 | 查看帮助、状态并访问 FGA 命令别名。 |

## `/player` 区域操作

### 相关规则

`fakePlayerRangeControl`

### 语法

```text
/player <假人> use range <起点> to <终点> [参数]
/player <假人> use continuous range <起点> to <终点> [参数]
/player <假人> attack range <起点> to <终点> [参数]
/player <假人> attack continuous range <起点> to <终点> [参数]
/player <假人> stop
/player <假人> use|attack range help
```

参数可组合：`pathfinding`、`reach <0.1-64>`、`airPlace`、`ignoreObstruction`、`placeBlock`、`interactBlock`、`interactSpeed <1-64>`。未指定 `placeBlock` 或 `interactBlock` 时按放置模式处理。

## `/droppedItemStackLimit`

### 相关规则

`droppedItemStackLimit`

### 语法

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

`list` 按页显示中文名称、完整物品 ID 和数量；列表中的删除按钮可点击执行对应命令。配置损坏时保持原版安全限制并拒绝写入。

## `/entityDropRemoval` 与 `/fga entityDropRemoval`

相关规则：`entityDropRemoval`

```text
/entityDropRemoval help
/entityDropRemoval status
/entityDropRemoval set <生物ID> <物品ID|allEquipment>
/entityDropRemoval remove <生物ID> <物品ID|allEquipment>
/entityDropRemoval list
/entityDropRemoval list <生物ID>
```

规则值为 `false` 时命令不可用；`true`、`ops` 或 `0-4` 按规则值控制权限。生物 ID 和物品 ID 支持完整命名空间、省略 `minecraft:` 和 Tab 补全。`set` 会在原配置上增加项目，`remove` 只删除指定项目，`allEquipment` 表示头盔、胸甲、护腿、靴子、主手和副手六个装备槽。`list` 显示已配置生物与去除项，红色减号可点击删除；`list <生物ID>` 显示默认战利品表和当前可识别的掉落配置。配置保存于 `world/config/carpetfgaaddition/entity-drop-removal.json`，使用原子替换；损坏文件会保留并拒绝本次运行的写入。

## `/dropPreStack` 与 `/fga dropPreStack`

### 相关规则

`preStackDroppedItems`

### 语法

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

范围为 `0-16`，省略时为 `1.0`。ID 支持 `minecraft:stone` 和 `stone`；物品侧也支持官方中文名称。`list` 显示中文名称、英文 ID 和范围，并提供可点击修改/删除命令。新配置仅在 `preStackDroppedItems=true` 时生效；旧版生物规则独立兼容。

## `/villagerPerformance`

### 相关规则

`villagerPerformanceOptimization`、`wanderingTraderNoDespawn`

### 语法

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

名单操作立即生效并保存到世界配置。`controlled` 使用名称或脚下一格方块的“或”匹配；名单为空时不保护流浪商人。列表命令支持分页。

## `/fakePlayerItemSort` 与 `bot_sort`

分类核心在 Minecraft `1.21-26.2` 注册，Dashboard/API、磁盘路由缓存、库存重构、自动补货和线程调优仍仅限 `1.21.1`

```text
/fakePlayerItemSort status
/fakePlayerItemSort help
/fakePlayerItemSort mode summon|quickopen
/fakePlayerItemSort setting <名称> <值>
/fakePlayerItemSort whitelist add|remove <玩家>
/fakePlayerItemSort whitelist list [页码]
/fakePlayerItemSort format prefix|suffix <文本>
/fakePlayerItemSort format status
/fakePlayerItemSort name set <物品ID> <名称>
/fakePlayerItemSort name remove <物品ID>
/fakePlayerItemSort name list [页码]
/fakePlayerItemSort name reload
/fakePlayerItemSort workers <initial> <cached>  # 仅 1.21.1
/fakePlayerItemSort dashboard status  # 仅 1.21.1
/fakePlayerItemSort dashboard port <1024-65535>  # 仅 1.21.1
/player <假人> bot_sort
/player <假人> bot_sort continuous
/player <假人> bot_sort stop
/player <假人> bot_sort restart <物品名称>
/player <假人> bot_sort restart all
/player <假人> bot_sort restart all confirm
```

`restart all` 必须在确认按钮或 `confirm` 子命令有效期内再次确认；`opall` 时全量重构仅 OP 可执行。`quickopen` 不召唤目标假人，`summon` 使用在线假人。装备栏始终不读写。

## `/minecart` 与 `/fga minecart`

### 相关规则

`fireworkMinecartBoost`、`chainMinecartBinding`、`minecartFeatureCommandPermission`

### 语法

```text
/minecart help
/minecart status
/minecart firework set <最高速度> <每级持续gt> <减速度>
/minecart firework reset
/minecart chain set <最大距离>
/minecart chain reset
```

默认烟花参数为 `1.2 10 0.02`。飞行等级 1/2/3 分别维持满速 10/20/30gt，随后线性减速。玩家乘坐普通矿车时使用烟花触发，生存模式消耗一枚；只生成声音和粒子，不生成烟花实体。

默认锁链距离为 `1.0` 格。手持锁链依次右击两辆普通矿车可连接或解除；每辆最多两个连接，禁止闭环与分叉。连接超过 16 格、跨维度或矿车被破坏时断裂并返还已消耗的锁链。连接存档不会强加载区块。

权限规则为 `false` 时命令隐藏；`true`/`0` 允许所有玩家，`ops` 允许 OP，`1-4` 对应权限等级。参数范围分别为速度 `0.1-4.0`、每级持续时间 `1-24000gt`、减速度 `0.001-1.0`、链距 `1.0-8.0`。

## `/vehicleStop` 与 `/fga vehicleStop`

### 相关规则

`vehicleStopOnDismount`

### 语法

```text
/vehicleStop help
/vehicleStop status
/vehicleStop set minecart|boat|all true|false
/vehicleStop reset
/vehicleStop player <在线玩家> status
/vehicleStop player <在线玩家> set minecart|boat|all true|false
/vehicleStop player <在线玩家> reset
```

普通玩家只能管理自己；OP 与控制台可管理在线玩家。个人设置始终保存，但只在规则为 `custom` 时决定实际行为，未配置默认关闭。驾驶者下车时只清除水平速度；普通乘客下车不触发。无人乘坐的锁链列车会整列停止，有其他玩家时整列不停。

## `/log playerHealth`

### 相关规则

`playerHealthDisplay`

### 语法

```text
/log playerHealth
```

### 行为

- `playerHealthDisplay=true`：所有查看者看到真人和假人的 Tab 生命值。
- `playerHealthDisplay=false`：默认不显示；执行 `/log playerHealth` 的玩家订阅后，只该玩家看到生命值。
- `playerHealthDisplay=nofake`：所有查看者只看到真人生命值，订阅不会显示假人生命值。
- 再次执行命令会取消当前玩家的订阅。
- 生命值固定追加在多人游戏列表名称最右侧，吸收生命值大于 0 时同时显示金色吸收段。
- 不创建计分板、头顶文本实体或其他聊天输出。

### 权限与版本

这是 Carpet Logger 的玩家订阅命令，订阅状态只影响执行命令的玩家。规则生效版本为 `1.21+`；需要服务端安装 Carpet。

## `/playerLoadDistance` 与 `/fga playerLoadDistance`

相关规则：`playerLoadDistance`，仅 Minecraft `1.21.1`

```text
/playerLoadDistance help
/playerLoadDistance status <在线玩家>
/playerLoadDistance set <在线玩家> <距离> [persistent]
/playerLoadDistance reset <在线玩家> [persistent]
```

`<距离>` 支持 `-1`、`0`、`1-32` 和 `none`。临时设置在重启后失效，末尾加 `persistent` 需要 OP 并按 UUID 写入 `world/config/carpetfgaaddition/player-load-distance.json`。普通玩家只能修改自己，修改其他在线玩家或删除其他玩家持久记录需要 OP。玩家名和距离均支持 Tab 补全。帮助和状态会说明该功能只控制区块发送与跟踪，不改变模拟距离。当前覆盖会显示在多人游戏列表最左侧，每名玩家入服时会收到持久记录汇总

## `/trialStop` 与 `/fga trialStop`

### 相关规则

`trialStopCommandPermission`

### 语法

```text
/trialStop help
/trialStop range <半径> [none|reward|fast] [clear]
/trialStop range from <起点XYZ> <终点XYZ> [none|reward|fast] [clear]
/trialStop dimension <维度ID> range <半径> [none|reward|fast] [clear]
/trialStop dimension <维度ID> range from <起点XYZ> <终点XYZ> [none|reward|fast] [clear]
/fga trialStop help
/fga trialStop range <半径> [none|reward|fast] [clear]
/fga trialStop range from <起点XYZ> <终点XYZ> [none|reward|fast] [clear]
/fga trialStop dimension <维度ID> range <半径> [none|reward|fast] [clear]
/fga trialStop dimension <维度ID> range from <起点XYZ> <终点XYZ> [none|reward|fast] [clear]
```

`range <半径>` 以命令执行源位置为中心，按忽略 Y 的水平圆柱扫描，半径单位为格；Tab 提供 `16`、`32`、`64` 三个预设，也可手动输入其他合法数值。控制台可使用 `/execute positioned` 指定中心。`range from` 使用完整 XYZ 方框，坐标支持相对坐标、玩家自身位置和视线指向方块 Tab 补全。命令只遍历当前已经加载的区块，不强制加载

奖励模式省略时默认为 `none`。`none` 不发奖励并立即刷新，`reward` 按原版开门及逐次喷出节奏发完后立即刷新，`fast` 立即发完奖励并刷新。`clear` 只清理该刷怪笼登记且当前已加载的怪物。省略 `dimension` 分支时使用当前维度；指定其他维度时使用前置的 `dimension <维度ID>`，维度 ID 支持 Tab 补全。`INACTIVE` 刷怪笼只清理残留数据并保持未激活，其他状态刷新为等待玩家且跳过完整冷却

## 深板岩切石规则

使用 `/carpet deepslateStonecuttingRecipes false|true` 控制，在 `1.21+` 注册，只过滤 FGA 自己的深板岩直接切石配方，不提供独立命令

## 其他命令

```text
/fga help
/fga status
/fga droppedItemStackLimit <子命令>
/fga dropPreStack <子命令>
/fga villagerPerformance <子命令>
/fga fakePlayerItemSort <子命令>
/fga player <假人> <子命令>
```

帮助消息中命令为灰色、说明为金色并支持点击填充。`/log playerHealth` 只切换当前玩家的 Tab 订阅，不向聊天栏周期输出。背包进度优化是隐藏内部功能，没有独立命令入口。
