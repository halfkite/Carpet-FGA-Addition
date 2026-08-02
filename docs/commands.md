# Carpet FGA Addition 命令

> 文档版本：`1.4.3`

## 命令总表

| 命令 | 相关规则 | 权限/版本 | 说明 |
|---|---|---|---|
| `/player` 区域操作 | `fakePlayerRangeControl` | Carpet 玩家权限/全版本 | 让假人执行区域放置、交互、攻击或连续任务。 |
| `/droppedItemStackLimit` | `droppedItemStackLimit` | 规则权限/1.21.1+ | 配置地面物品堆叠上限。 |
| `/dropPreStack` | `preStackDroppedItems` | 与掉落物上限权限/1.20.5-26.1.2 | 配置生物、方块和容器掉落预堆叠。 |
| `/villagerPerformance` | `villagerPerformanceOptimization` | 规则权限/1.20.1+ | 配置村民交易、赠礼和流浪商人保护。 |
| `/fakePlayerItemSort` | 1.21.1 分类规则 | `commandPlayer`/1.21.1 | 配置分类器、白名单、名称和网页。 |
| `/player <name> bot_sort` | 1.21.1 分类规则 | `commandPlayer`/1.21.1 | 启动、停止或重构指定假人的分类任务。 |
| `/log playerHealth` | `playerHealthDisplay` | Carpet Logger/1.16.5+ | 切换当前玩家的 Tab 生命值订阅。 |
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

仅在 Minecraft `1.21.1` 注册。

```text
/fakePlayerItemSort status
/fakePlayerItemSort whitelist add|remove <玩家>
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

`restart all` 必须在确认按钮或 `confirm` 子命令有效期内再次确认；`opall` 时全量重构仅 OP 可执行。`quickopen` 不召唤目标假人，`summon` 使用在线假人。装备栏始终不读写。

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

这是 Carpet Logger 的玩家订阅命令，订阅状态只影响执行命令的玩家。规则生效版本为 `1.16.5+`；需要服务端安装 Carpet。

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
