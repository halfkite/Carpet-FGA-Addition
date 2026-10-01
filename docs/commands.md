# Carpet FGA Addition 命令

> 文档版本：`1.5.16`

## 旁观者跨维度传送

`/carpet spectatorFreeTeleport true` 保持旁观者自身传送行为；设置为 `full` 后，所有游戏模式的玩家都可使用完整 `/tp` 与 `/teleport` 命令，包括传送其他玩家或实体和跨维传送。`full` 仅对这两个命令覆盖 TIS 与 AMS 权限拦截；服务端安装 FGA 即可，客户端无需安装

```text
/tp in <维度> <x> <y> <z>
/teleport in <维度> <x> <y> <z>
```

示例：`/tp in minecraft:the_nether 100 64 -20`。此 FGA 扩展语法传送命令执行者自身，目标位置仍受世界边界限制；`full` 下也可在任意模式使用。原版 `/tp <目标> <玩家>` 可将目标跨维传送到指定在线玩家

当前支持的构建版本也支持把维度放在坐标末尾，传送命令执行者自身：

```text
/tp <x> <y> <z> <维度>
/teleport <x> <y> <z> <维度>
```

例如 `/tp 1 1 1 minecraft:overworld`。此写法沿用 `/tp` 的现有权限：原版有权限的玩家可用，`spectatorFreeTeleport=true` 的旁观者可传送自己，`full` 下所有模式的玩家可用；相对坐标以执行者原位置为准，目标受世界边界限制

<a id="cmd-join-notice"></a>

## 进服提示指令 (joinNotice)

适用于当前全部十个构建版本，服务端安装 FGA 即可，客户端无需安装

```text
/carpet customJoinNotice true|false     # 进服提示总开关，默认 false
/fga joinNotice status                  # 查看当前配置
/fga joinNotice preview                 # 给自己预览当前欢迎语和日期提示
/fga joinNotice welcome set <文字>       # 设置欢迎语
/fga joinNotice welcome clear          # 清空欢迎语
/fga joinNotice date set YYYY-MM-DD     # 设置开服日期
/fga joinNotice date enabled true|false # 开启或关闭日期提示
/fga joinNotice date clear             # 清除日期并关闭日期提示
```

修改配置需要 OP 2 及以上；`status` 和 `preview` 可由普通玩家使用，`preview` 只能由玩家执行

欢迎语支持 `{player}` 替换玩家名、`&#RRGGBB` 指定 RGB 颜色、`&r` 重置颜色，以及 `\n` 换行，例如 `/fga joinNotice welcome set &#55AAFF欢迎 {player}&r！`

日期按服务器本地日期计算，开服当天显示 `0` 天；配置保存在当前存档的 `config/carpetfgaaddition/join-notice.json`

<a id="cmd-announcements"></a>

## 服务器公告指令 (announcement)

适用于当前全部十个构建版本，服务端安装 FGA 即可，客户端无需安装

```text
/carpet serverAnnouncements true|false
/fga announcement help
/fga announcement status
/fga announcement list
/fga announcement info <编号>
/fga announcement create <内容>                         # 创建永久公告并自动编号
/fga announcement create id <编号> <内容>               # 创建并指定编号
/fga announcement header set <题头>
/fga announcement header clear                          # 恢复“服务器公告如下”
/fga announcement content set <编号> <内容>
/fga announcement expiry set <编号> forever|<数字>h|<数字>d
/fga announcement enable <编号>
/fga announcement disable <编号>
/fga announcement hide <编号>
/fga announcement show <编号>
/fga announcement delete <编号>
/fga announcement trigger join <编号>
/fga announcement trigger region <编号> <维度> <x1> <y1> <z1> <x2> <y2> <z2>
/fga announcement reload
```

更改公告需要 OP 2 及以上；`help`、`status`、`list` 和 `info` 可供有 `/fga` 命令权限的玩家查看。`create` 默认自动分配编号并永久有效，内容中的 `/n` 会显示为换行。设置时效后从执行时开始倒计时；`forever` 清除时效

默认触发方式为玩家进服。设置 `trigger region` 后，公告改为玩家进入指定维度的三维坐标长方体时触发，每次进入发送一次，离开后再次进入可以再次触发。维度可按 Tab 选择当前服务器已加载的维度，例如 `minecraft:overworld`

`list` 每条公告单独一行，点击行可查看详情；详情页的删除、启用/停用和隐藏/显示按钮会直接执行，内容、时效和范围按钮会将命令填入聊天栏。玩家进服时只发送总规则已开启、单条公告已启用、未隐藏且未过期的公告

公告保存在当前存档的 `config/carpetfgaaddition/announcements.json`。可以编辑 JSON 中的 `header` 和公告 `content` 字段，再执行 `/fga announcement reload`；损坏的文件会被保留且拒绝覆盖，需先人工修复或备份

<a id="cmd-food"></a>

## 玩家状态指令 (food)

### `/food clear [玩家]` 与 `/fga food clear [玩家]`

生效版本：`1.21+`

```text
/food clear [player]        # 清空执行者或指定在线玩家的饱食度和饱和度
/fga food clear [player]    # 与 /food clear [player] 相同
```

- 相关规则：`foodCommandPermission`
- 默认值为 `ops`；`false` 关闭命令，`true` 允许所有玩家清空自己或指定玩家，`onlyself` 允许非 OP 只清空自己，`ops` 需要 OP 2 及以上，`0-4` 设置最低权限等级

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

<a id="cmd-player-rejoin"></a>

### `/player <名字> rejoin` 增强

需服务端安装 Carpet TIS；`enhancedFakePlayerRejoin=true` 时生效，权限沿用 Carpet 的 `commandPlayer`

```text
/player <名字> rejoin
/player <名字> rejoin at <x> <y> <z> [in <维度>]
/player <名字> rejoin at <x> <y> <z> facing <水平角> <俯仰角> [in <维度>]
```

无参数命令在原存档位置上线，连同存档中的载具及非玩家乘客恢复；`at` 分支在完成存档恢复后将整个载具及乘客移到目标位置。未指定维度时使用命令执行者所在维度，未指定朝向时保留存档朝向。关闭规则时，TIS 原有无参数命令仍可使用。

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
/entityDropRemoval enableAllDrops <entity_id>                  # 开启此生物所有掉落物
/entityDropRemoval disableAllDrops <entity_id>                 # 关闭此生物所有掉落物
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

### `/fakePlayerItemSort`、`/fga playersort` 与 `bot_sort`

26.3 提供客户端主动查询与轻松放置半组补货 API，详见 [假人库存 API](playersort_inventory_api.md)；查询使用 `stock` 权限，取物使用独立的 `inventoryTake` 权限，默认均要求 OP，可通过 `/fga playersort permission <权限> <玩家名> true` 单独授权

26.3 的可选 `query_amount` / `take_amount` 扩展复用上述权限，支持客户端指定0（半组）或1–2304个物品，普通/静默同用；数量越界拒绝，单个来源不足或背包空间不足时不部分扣货。halfmasa 在扩展功能栏分别设置轻松放置和打印机补货的允许取货、数量与静默子项。

26.3 开启分类规则后，普通材料首位散货不足 1152 时，自动从最大编号后位补入首位 9–35 槽（普通物品最多 1728）；材料查询首位不足也优先查最大后位。取空的盒子送到名称为 `潜影盒` 的库存假人，接收者忙碌或背包满时原处保留并重试。维护直接处理合法库存，不召唤假人；不是 `autoCraft` 的潜影盒补给站合成功能，详见 API 文档。

分类核心在 Minecraft `1.21+` 注册。现有 Dashboard/API、磁盘路由缓存、库存重构、自动补货和线程调优保留；基础管理命令适配 `1.21.1` 和 `26.3`。26.3 另外提供设置向导、逐命令权限、库存正则查询/文本导出和新的潜影盒分类路由；本轮重构仅适用于 `26.3`，其他版本行为不变。<br>

26.3 向导第 11 项为网页功能，`/fga playersort set dashboard false|true|login` 分别表示关闭（默认）、无需登录、需要登录。关闭后停止 HTTP 监听和自动网页库存快照更新；游戏内主动查询库存仍可使用。登录模式保护网页、两个库存 API 和库存文本导出，先用 `/fga playersort set dashboard password <password>` 设置密码，用户名固定为 `fga`。密码为 12–128 个无空格的 ASCII 可打印字符，建议在服务端控制台设置；配置只保存随机盐 PBKDF2 哈希。设置与密码修改无需重启，仍仅监听 `127.0.0.1`；使用此 API 的客户端在 login 模式下也必须提供认证信息。
26.3 的 `/fga playersort` 默认仅 OP 2 及以上和控制台可用，可通过 `permission` 单独调整命令权限；旧版本仍使用 Carpet 的 `commandPlayer` 权限模型。

26.3 的 11 项设置统一收拢到 `/fga playersort set`，直接执行该命令会显示设置面板。外层只保留管理命令及语言快捷入口；`language` 同时支持 `/fga playersort set language` 与 `/fga playersort language`。26.3 的旧 `/fakePlayerItemSort` 别名采用同一布局；下面开头的 `/fakePlayerItemSort mode/setting/format/workers/dashboard` 旧语法仅用于 26.3 之前的版本。

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
/fakePlayerItemSort workers <initial> <cached>        # 旧入口，1.21.1
/fakePlayerItemSort dashboard status                  # 旧入口，1.21.1
/fakePlayerItemSort dashboard port <1024-65535>       # 旧入口，1.21.1
/fga playersort set                                  # 26.3：显示设置面板
/fga playersort set mode summon|quickopen             # 26.3：设置分类模式
/fga playersort set whitelistMode false|vanillaWhitelist|modWhitelist # 26.3：设置白名单过滤模式
/fga playersort set inventoryRebuild false|true|opall  # 26.3：设置库存重构策略
/fga playersort set format prefix|suffix <text>       # 26.3：保留的名称格式设置
/fga playersort set workers <initial> <cached>        # 26.3：保留的旧线程设置；实际线程数使用 cpu 设置
/fga playersort set dashboard status                 # 26.3：查看网页状态
/fga playersort set dashboard port <1024-65535>       # 26.3：设置网页端口
/fga playersort set dashboard false|true|login           # 26.3 网页关闭 / 无需登录 / 需要登录
/fga playersort set dashboard password <password>        # 26.3 设置网页密码，用户名为 fga
/fga playersort setup                                  # 26.3：显示设置向导
/fga playersort set language chinese|english|custom       # 26.3：设置分类语言
/fga playersort language chinese|english|custom       # 26.3：保留的语言快捷入口
/fga playersort set summonNotices true|false                # 26.3：是否向游戏公屏广播分类假人上下线消息；控制台日志始终保留
/fga playersort set prefix default|off                    # 26.3：默认 bulk_ 前缀或关闭
/fga playersort set prefix custom <text>                  # 26.3：自定义前缀
/fga playersort set quickShulker true|false                # 26.3：拆分潜影盒内容或按盒分类
/fga playersort set autoCraft true|false                   # 26.3：设置空潜影盒自动补货/合成
/fga playersort set cleanOpenedTarget true|false           # 26.3：打开目标时整理不匹配物品
/fga playersort set speed <ticks>                          # 26.3：分类间隔；范围 1-120 刻，4/8/16 可 Tab 补全
/fga playersort set cpu 0|1|2                              # 26.3：0 为可用 CPU 的一半；1、2 为工作线程数
/fga playersort set cpu custom <threads>                   # 26.3：自定义工作线程数，输入范围 1-256，实际不超过可用 CPU 数
/fga playersort stock list <regex> [page]              # 26.3：正则查询缓存库存
/fga playersort stock list all                         # 26.3：导出全部库存文本
/fga playersort permission <command> <ops|0-4|player> <true|false>  # 26.3：设置命令权限
/player <fake_player> bot_sort                        # 开始分类
/player <fake_player> bot_sort continuous             # 开始持续分类
/player <fake_player> bot_sort stop                   # 停止分类
/player <fake_player> bot_sort restart <item_name>    # 重启指定物品分类
/player <fake_player> bot_sort restart all            # 请求重构全部分类
/player <fake_player> bot_sort restart all confirm    # 确认重构全部分类
/player <fake_player> bot_sort restart stop           # 停止该假人的待处理重构
```

26.3 的 `set` 值补全按所选设置过滤；11 项设置名为 `language`、`mode`、`summonNotices`、`prefix`、`quickShulker`、`autoCraft`、`whitelistMode`、`cleanOpenedTarget`、`speed`、`cpu`、`dashboard`。`dashboard` 支持 `false|true|login`，`speed` 接受 1–120 刻，`cpu custom` 接受 1–256。高级内部设置名 `targetLanguage`、`shulkerRestock`、`cpuThreads`、`inventoryRebuild` 仍可通过 `set <name> <value>` 输入，沿用原 `settings` 权限；11 项明确子命令沿用原有对应功能权限，并兼容原 settings 授权能够修改的设置；网页密码和端口仍要求 dashboard 权限。其他版本继续使用 `setting` 旧入口和原有可选值。

26.3 首次启用后会提示设置语言；选择语言后才展示其余十项设置。第 3 项控制分类自动召唤假人的上下线消息是否广播到游戏公屏；关闭时服务端控制台仍会记录登录与断开信息。点击选项只会把命令放入聊天栏。设置项与选择状态会写入世界配置。分类速度按 N 刻间隔移动；散货也会按速度分批转移，潜影盒补货假人会在补货/清理任务结束后下线（资料预载延迟上线时也会清理）。潜影盒开启拆分时按盒内物品路由，并在首位库存满后使用编号后位；关闭时散件、满单物品盒、混合/未满盒分别进入物品、`_box` 和混合盒分类。默认前缀为 `bulk_`。白名单可使用原版服务器白名单或 FGA 内置白名单。

26.3 库存查询的 `<regex>` 匹配物品名、物品 ID 和目标假人名；`stock list all` 写出位于世界配置目录下的文本文件。`restart all` 需在 30 秒内确认，重构限速排队；`restart stop` 取消该假人尚未处理的重构。逐命令权限支持 `all`、命令名、权限等级 `0-4`、`ops` 和具体玩家名；默认 OP 2 及以上可用，控制台始终可用。

命令反馈和分类过程通知：安装 FGA 的客户端按客户端语言显示；未安装 FGA 的客户端使用服务端 `/carpet language` 设置的语言。`help` 中的命令可点击后放入聊天栏，不会立即执行。

26.3 的 `stock list all` 成功反馈保留可翻译文本，导出路径以字符串发送，避免 Path 参数导致系统聊天包编码失败。导出的 TXT 位于服务端，不会自动下载到客户端。

`restart all` 必须在确认按钮或 `confirm` 子命令有效期内再次确认；`opall` 时全量重构仅 OP 可执行。`quickopen` 不召唤目标假人，直接读写离线 playerdata；`summon` 会短暂登录在线分类假人，当前批次完成后自动下线，物品保存在该假人的 playerdata 中，下次上线仍可取回。装备栏始终不读写。分类目标假人使用按名字确定的离线档案，召唤前先写入服务端档案缓存，因此中文等本地化目标名不会触发向 Mojang 查询档案的主线程卡顿。

26.3 的 `speed` 是两次搬运尝试之间的游戏刻数，数字越小越快，失败尝试也会等待该间隔。拆盒单次最多转移 64 个同类物品；同类物品跨多个盒内槽位时合并处理。排队中的任务不会重复规划，停止或重启分类后旧任务不会继续写入。空盒分类假人会等源库存中待拆盒全部处理完再下线，避免每拆一个盒子就重新登录。提高 tick rate 会增加每秒执行次数，并不会改变这些每 tick 限制。

26.3 使用原版玩家数据目录 `world/players/data`。若旧 FGA 曾在 `world/playerdata` 写入同 UUID 的分类库存，分类器会保留旧文件并拒绝继续写入该目标；需核对两边库存后恢复，不能直接覆盖或删除任一份档案。

空潜影盒补给假人（`潜影盒补货`）以本轮分类为生命周期：只要还有分类任务在跑，它就保持在线，即使一次补货因为缺少材料失败也不会立刻下线，等到原木、潜影盒壳在后续分类中到位后再合成；本轮分类结束（源假人背包清空、没有分类任务）或规则关闭时它才下线。玩家自己召唤的同名假人不受此逻辑影响，不会被自动下线。

补给站按盒装材料取料：放进 `潜影盒补货` 背包的潜影盒只要装的是原木或潜影盒壳，就算补给站材料而不是需要清出去的杂物；合成空盒时会直接从这些盒子里取 2 原木 + 2 潜影盒壳，因此背包 36 格全被材料盒占满、没有空槽时也能继续产出空盒。被取空的盒子留在原处，本身就是一个可用空盒。该行为同样受 `shulkerRestock` 开关约束。

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
