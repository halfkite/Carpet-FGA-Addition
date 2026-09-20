# Carpet FGA Addition 命令

> 文档版本：`1.5.11`

## 玩家状态指令 (food)

### `/food clear [玩家]` 与 `/fga food clear [玩家]`

生效版本：`1.21.1`

```text
/food clear [player]        # 清空执行者或指定在线玩家的饱食度和饱和度
/fga food clear [player]    # 与 /food clear [player] 相同
```

- 入口权限沿用 Carpet `commandPlayer`

<a id="cmd-map-load"></a>

## 地图指令 (mapLoad)

### `/mapLoad` 与 `/fga mapLoad`

相关规则：`mapLoadCommandPermission`

生效版本：`1.21.1`

客户端要求：无（纯服务端），玩家可见消息在服务端按服务器语言解析

```text
/mapLoad start <smooth|fast|loaded>    # 开始加载手持地图
/mapLoad list                          # 列出正在进行的加载任务
/mapLoad pause [player]                # 暂停自己或指定玩家的加载任务
/mapLoad resume [player]               # 继续自己或指定玩家的加载任务
/mapLoad stop [player]                 # 停止自己或指定玩家的加载任务
/fga mapLoad ...                       # 与 /mapLoad ... 相同
```

必须显式给出加载模式，模式支持 Tab 补全：

```text
smooth    # 默认推荐，同时在途最多 384 个区块，优先减少卡顿
fast      # 速度优先，同时在途最多 1024 个区块
loaded    # 只刷新已加载区块，不请求或生成区块
```


## 玩家与假人指令 (player)

<a id="cmd-player-possession"></a>

### `/player <名字> possess`

生效版本：`1.21+`，服务端安装 FGA 即可，双方客户端均可使用原版。

```text
/player <name> possess         # 操控指定名称的在线玩家或假人
/player <name> possess stop    # 结束当前夺舍关系
```

<a id="cmd-control-player"></a>

### `/controlPlayer` 与 `/fga controlPlayer`

仅 `playerPossession` 开启后提供，查询命令不额外要求 OP

```text
/controlPlayer list        # 列出全部正在进行的夺舍关系
/controlPlayer             # 查看执行者自己的夺舍关系
/controlPlayer <player>    # 查看指定在线玩家的夺舍关系
/fga controlPlayer ...     # 与 /controlPlayer ... 相同
```

<a id="cmd-playertpend"></a>

### `/playertpend`

先执行 `/carpet PlayerTpEndControl control`。`enter` 是进入末地门，`exit` 是末地主岛出口，`gateway` 是末地折跃门。

```text
/playertpend status [player]                                   # 查看自己或指定玩家的传送设置
/playertpend set <enter|exit|gateway> <allow|deny>             # 设置自己的传送权限
/playertpend set <player> <enter|exit|gateway> <allow|deny>    # 设置指定玩家的传送权限
/playertpend reset [enter|exit|gateway]                        # 重置自己的设置
/playertpend reset <player> [enter|exit|gateway]               # 重置指定玩家的设置
```

偏好按 UUID 保存到 `world/config/carpetfgaaddition/player-tp-end-control.json`。OP 能修改任意在线玩家；非 OP 只能修改自己与在线 Carpet 假人。

## 世界与地形指令 (world)

<a id="cmd-regenerate-terrain"></a>

### `/regenerateTerrain`

相关规则：`voidWorldGeneration`、`terrainRegenerationCommandPermission`

```text
/regenerateTerrain create from <x1> <z1> <x2> <z2>                       # 创建地形重生成任务
/regenerateTerrain create radius <radius>                                # 按区块半径创建重生成任务
/regenerateTerrain clear from <x1> <z1> <x2> <z2>                        # 创建地形清空任务
/regenerateTerrain clear radius <radius>                                 # 按区块半径创建清空任务
/regenerateTerrain create|clear dimension <dimension> from|radius ...    # 指定维度创建任务
/regenerateTerrain list [page]                                           # 列出任务
/regenerateTerrain confirm <task>                                        # 确认并开始任务
/regenerateTerrain run <task>                                            # 执行已确认任务
/regenerateTerrain cancel <task>                                         # 取消任务
/regenerateTerrain retry <task>                                          # 重试失败任务
```



## 玩家与假人区域操作 (player)

<a id="cmd-player-range"></a>

### `/player` 区域操作

#### 相关规则

`fakePlayerRangeControl`

#### 语法

```text
/player <fake_player> use range <start> to <end> [options]                  # 执行一次区域放置
/player <fake_player> use continuous range <start> to <end> [options]       # 持续执行区域放置
/player <fake_player> attack range <start> to <end> [options]               # 执行一次区域破坏
/player <fake_player> attack continuous range <start> to <end> [options]    # 持续执行区域破坏
/player <fake_player> stop                                                  # 停止区域操作
/player <fake_player> use|attack range help                                 # 查看区域操作帮助
```

参数可组合：`pathfinding`、`reach <0.1-64>`、`airPlace`、`ignoreObstruction`、`placeBlock`、`interactBlock`、`interactSpeed <1-64>`。未指定 `placeBlock` 或 `interactBlock` 时按放置模式处理。

## 物品与生物指令 (items)

<a id="cmd-dropped-item-stack-limit"></a>

### `/droppedItemStackLimit`

#### 相关规则

`droppedItemStackLimit`

#### 语法

```text
/droppedItemStackLimit mode all <count>                   # 设置掉落物总上限
/droppedItemStackLimit mode black <count>                 # 设置黑名单模式上限
/droppedItemStackLimit mode whitelist                     # 启用白名单模式
/droppedItemStackLimit mode inventory <count>             # 设置玩家背包上限
/droppedItemStackLimit mode container <count>             # 设置容器上限
/droppedItemStackLimit reset inventory                    # 重置玩家背包上限
/droppedItemStackLimit reset container                    # 重置容器上限
/droppedItemStackLimit set black <item_id>                # 添加黑名单物品
/droppedItemStackLimit remove black <item_id>             # 移除黑名单物品
/droppedItemStackLimit set whitelist <item_id> <count>    # 设置白名单物品上限
/droppedItemStackLimit remove whitelist <item_id>         # 移除白名单物品
/droppedItemStackLimit list [black|whitelist] [page]      # 查看黑名单或白名单
/droppedItemStackLimit clear                              # 清空配置
```

`list` 按页显示中文名称、完整物品 ID 和数量；列表中的删除按钮可点击执行对应命令。配置损坏时保持原版安全限制并拒绝写入。

在当前十个构建版本（`1.21.1` 至 `26.3`），`inventoryLimit` / `containerLimit` 的保存值与是否生效分开：主规则关闭（含重启后）时不应用，重新开启后恢复；关闭操作不会清空配置。

<a id="cmd-entity-drop-removal"></a>

### `/entityDropRemoval` 与 `/fga entityDropRemoval`

相关规则：`entityDropRemoval`

```text
/entityDropRemoval help                                         # 查看帮助
/entityDropRemoval status                                       # 查看当前配置
/entityDropRemoval set <entity_id> <item_id|allEquipment>       # 添加去除项
/entityDropRemoval remove <entity_id> <item_id|allEquipment>    # 删除去除项
/entityDropRemoval list                                         # 列出全部配置
/entityDropRemoval list <entity_id>                             # 查看指定生物配置
/fga entityDropRemoval ...                                      # 与 /entityDropRemoval ... 相同
```

<a id="cmd-piglin-barter-customization"></a>

### `/piglinBarterItemExclusions` 与 `/fga piglinBarterItemExclusions`

相关规则：`piglinBarterItemExclusions`

生效版本：`1.21.1`

```text
/piglinBarterItemExclusions list                                          # 列出当前交易条目
/piglinBarterItemExclusions add <entry>                                   # 添加交易条目
/piglinBarterItemExclusions enable <entry>                                # 启用交易条目
/piglinBarterItemExclusions disable <entry>                               # 禁用交易条目
/piglinBarterItemExclusions set <entry> <概率> <min_count>-<max_count>    # 设置概率和数量范围
/piglinBarterItemExclusions reset <entry>                                 # 重置概率和数量
/fga piglinBarterItemExclusions ...                                       # 与直接命令相同
```

<a id="cmd-drop-pre-stack"></a>

### `/dropPreStack` 与 `/fga dropPreStack`

#### 相关规则

`preStackDroppedItems`

#### 语法

```text
/dropPreStack help                                          # 查看帮助
/dropPreStack status                                        # 查看当前配置
/dropPreStack entity add <entity_id> [range]                # 添加生物掉落预堆叠
/dropPreStack entity remove <entity_id>                     # 删除生物掉落预堆叠
/dropPreStack entity set <entity_id> [range]                # 设置生物掉落预堆叠范围
/dropPreStack entity list [page]                            # 列出生物配置
/dropPreStack block add <item_id> [range]                   # 添加方块掉落预堆叠
/dropPreStack block remove <item_id>                        # 删除方块掉落预堆叠
/dropPreStack block set <item_id> [range]                   # 设置方块掉落预堆叠范围
/dropPreStack block list [page]                             # 列出方块配置
/dropPreStack container add <block_or_entity_id> [range]    # 添加容器掉落预堆叠
/dropPreStack container remove <block_or_entity_id>         # 删除容器掉落预堆叠
/dropPreStack container set <block_or_entity_id> [range]    # 设置容器掉落预堆叠范围
/dropPreStack container list [page]                         # 列出容器配置
/fga dropPreStack ...                                       # 与 /dropPreStack ... 相同
```

范围为 `0-16`，省略时为 `1.0`。ID 支持 `minecraft:stone` 和 `stone`；物品侧也支持官方中文名称。`list` 显示中文名称、英文 ID 和范围，并提供可点击修改/删除命令。新配置仅在 `preStackDroppedItems=true` 时生效；旧版生物规则独立兼容。

<a id="cmd-villager-performance"></a>

### `/villagerPerformance`

#### 相关规则

`villagerPerformanceOptimization`、`wanderingTraderNoDespawn`

#### 语法

```text
/villagerPerformance help                                                # 查看帮助
/villagerPerformance status                                              # 查看当前状态
/villagerPerformance trade false|ai|static                               # 设置交易性能模式
/villagerPerformance trade name add|remove <name>                        # 修改交易名称名单
/villagerPerformance trade name list [page]                              # 查看交易名称名单
/villagerPerformance trade block add|remove <block_id>                   # 修改交易方块名单
/villagerPerformance trade block list [page]                             # 查看交易方块名单
/villagerPerformance gift false|true                                     # 设置赠礼功能
/villagerPerformance gift name add|remove <name>                         # 修改赠礼名称名单
/villagerPerformance gift block add|remove <block_id>                    # 修改赠礼方块名单
/villagerPerformance gift list [page]                                    # 查看赠礼名单
/villagerPerformance wanderingTrader false|true|controlled               # 设置流浪商人保护模式
/villagerPerformance wanderingTrader name add|remove|list <name>         # 修改或查看名称名单
/villagerPerformance wanderingTrader block add|remove|list <block_id>    # 修改或查看方块名单
```

名单操作立即生效并保存到世界配置。`controlled` 使用名称或脚下一格方块的“或”匹配；名单为空时不保护流浪商人。列表命令支持分页。

<a id="cmd-fake-player-item-sort"></a>

### `/fakePlayerItemSort` 与 `bot_sort`

分类核心在 Minecraft `1.21+` 注册，Dashboard/API、磁盘路由缓存、库存重构、自动补货和线程调优仍仅限 `1.21.1`<br>
除查询类子命令（`status`、`whitelist list`、`name list`、`format status`、`dashboard status`、`bot_sort stop`）外，配置、白名单增删、排序启动等子命令均需要 OP 2 及以上权限

```text
/fakePlayerItemSort status                            # 查看当前状态
/fakePlayerItemSort help                              # 查看帮助
/fakePlayerItemSort mode summon|quickopen             # 设置运行模式
/fakePlayerItemSort setting <name> <value>            # 修改分类设置
/fakePlayerItemSort whitelist add|remove <player>     # 修改白名单
/fakePlayerItemSort whitelist list [page]             # 查看白名单
/fakePlayerItemSort format prefix|suffix <text>       # 设置名称格式
/fakePlayerItemSort format status                     # 查看名称格式
/fakePlayerItemSort name set <item_id> <name>         # 设置物品名称
/fakePlayerItemSort name remove <item_id>             # 删除物品名称
/fakePlayerItemSort name list [page]                  # 查看物品名称
/fakePlayerItemSort name reload                       # 重新加载名称
/fakePlayerItemSort workers <initial> <cached>        # 设置线程数，仅 1.21.1
/fakePlayerItemSort dashboard status                  # 查看 Dashboard 状态，仅 1.21.1
/fakePlayerItemSort dashboard port <1024-65535>       # 设置 Dashboard 端口，仅 1.21.1
/player <fake_player> bot_sort                        # 开始分类
/player <fake_player> bot_sort continuous             # 开始持续分类
/player <fake_player> bot_sort stop                   # 停止分类
/player <fake_player> bot_sort restart <item_name>    # 重启指定物品分类
/player <fake_player> bot_sort restart all            # 请求重构全部分类
/player <fake_player> bot_sort restart all confirm    # 确认重构全部分类
```

`restart all` 必须在确认按钮或 `confirm` 子命令有效期内再次确认；`opall` 时全量重构仅 OP 可执行。`quickopen` 不召唤目标假人，`summon` 使用在线假人。装备栏始终不读写。

## 矿车与载具指令 (vehicle)

<a id="cmd-minecart"></a>

### `/minecart` 与 `/fga minecart`

#### 相关规则

`fireworkMinecartBoost`、`chainMinecartBinding`、`minecartFeatureCommandPermission`

#### 语法

```text
/minecart help                                                     # 查看帮助
/minecart status                                                   # 查看当前配置
/minecart firework set <max_speed> <duration_gt> <deceleration>    # 设置烟花加速参数
/minecart firework reset                                           # 重置烟花加速参数
/minecart chain set <max_distance>                                 # 设置锁链连接距离
/minecart chain reset                                              # 重置锁链连接距离
/fga minecart ...                                                  # 与 /minecart ... 相同
```

默认烟花参数为 `1.2 10 0.02`。飞行等级 1/2/3 分别维持满速 10/20/30gt，随后线性减速。玩家乘坐普通矿车时使用烟花触发，生存模式消耗一枚；只生成声音和粒子，不生成烟花实体。

<a id="cmd-vehicle-stop"></a>

### `/vehicleStop` 与 `/fga vehicleStop`

#### 相关规则

`vehicleStopOnDismount`

#### 语法

```text
/vehicleStop help                                                # 查看帮助
/vehicleStop status                                              # 查看当前配置
/vehicleStop set minecart|boat|all true|false                    # 设置自己的急停模式
/vehicleStop reset                                               # 重置自己的设置
/vehicleStop player <player> status                              # 查看指定玩家设置
/vehicleStop player <player> set minecart|boat|all true|false    # 设置指定玩家的急停模式
/vehicleStop player <player> reset                               # 重置指定玩家设置
/fga vehicleStop ...                                             # 与 /vehicleStop ... 相同
```

普通玩家只能管理自己；OP 与控制台可管理在线玩家。个人设置始终保存，但只在规则为 `custom` 时决定实际行为，未配置默认关闭。驾驶者下车时只清除水平速度；普通乘客下车不触发。无人乘坐的锁链列车会整列停止，有其他玩家时整列不停。

## 记录器指令 (logger)

<a id="cmd-player-health"></a>

### `/log playerHealth`

#### 相关规则

`playerHealthDisplay`

#### 语法

```text
/log playerHealth    # 订阅或取消 Tab 玩家生命值显示
```

#### 行为

- `playerHealthDisplay=true`：所有查看者看到真人和假人的 Tab 生命值。
- `playerHealthDisplay=false`：默认不显示；执行 `/log playerHealth` 的玩家订阅后，只该玩家看到生命值。
- `playerHealthDisplay=nofake`：所有查看者只看到真人生命值，订阅不会显示假人生命值。
- 再次执行命令会取消当前玩家的订阅。
- 不创建计分板、头顶文本实体或其他聊天输出。

#### 权限与版本

这是 Carpet Logger 的玩家订阅命令，订阅状态只影响执行命令的玩家。规则生效版本为 `1.21+`；需要服务端安装 Carpet。

## 玩家区块指令 (player)

<a id="cmd-player-load-distance"></a>

### `/playerLoadDistance` 与 `/fga playerLoadDistance`

相关规则：`playerLoadDistance`，仅 Minecraft `1.21.1`

```text
/playerLoadDistance help                                    # 查看帮助
/playerLoadDistance status <player>                         # 查看指定玩家区块加载距离
/playerLoadDistance set <player> <distance> [persistent]    # 设置区块加载距离
/playerLoadDistance reset <player> [persistent]             # 重置区块加载距离
/fga playerLoadDistance ...                                 # 与 /playerLoadDistance ... 相同
```

## 试炼刷怪笼指令 (trial)

<a id="cmd-trial-stop"></a>

### `/trialStop` 与 `/fga trialStop`

#### 相关规则

`trialStopCommandPermission`

#### 语法

```text
/trialStop help                                                                                 # 查看帮助
/trialStop range <radius> [none|reward|fast] [clear]                                            # 按半径截停刷怪笼
/trialStop range from <start_xyz> <end_xyz> [none|reward|fast] [clear]                          # 按方框截停刷怪笼
/trialStop dimension <dimension> range <radius> [none|reward|fast] [clear]                      # 指定维度按半径截停
/trialStop dimension <dimension> range from <start_xyz> <end_xyz> [none|reward|fast] [clear]    # 指定维度按方框截停
/fga trialStop ...                                                                              # 与 /trialStop ... 相同
```

## Carpet 规则入口 (carpet)

<a id="cmd-deepslate"></a>

### 深板岩切石规则

```text
/carpet deepslateStonecuttingRecipes false|true    # 控制深板岩直接切石配方
```

在 `1.21+` 注册，只过滤 FGA 自己的深板岩直接切石配方，不提供独立命令

## FGA 指令入口 (fga)

<a id="cmd-fga"></a>

### 其他命令

```text
/fga help                                  # 查看 FGA 帮助
/fga status                                # 查看 FGA 状态
/fga droppedItemStackLimit <subcommand>    # 执行掉落物堆叠上限命令
/fga dropPreStack <subcommand>             # 执行掉落物预堆叠命令
/fga villagerPerformance <subcommand>      # 执行村民性能命令
/fga fakePlayerItemSort <subcommand>       # 执行假人物品分类命令
/fga player <fake_player> <subcommand>     # 转发到 Carpet /player 命令
```

帮助消息中命令为灰色、说明为金色并支持点击填充。`/log playerHealth` 只切换当前玩家的 Tab 订阅，不向聊天栏周期输出。背包进度优化是隐藏内部功能，没有独立命令入口。
