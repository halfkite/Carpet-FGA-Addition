# Carpet-FGA-Addition
一些可能用得上的功能
fakePlayerNameLength|假人名称上限更改
默认值 `-1`，沿用原版 16 字符限制；可自定义为 `1-128`。客户端无需安装本模组。

服务端首次同步超过 16 字符的玩家名时，会发送“前 7 个字符 + `...`”的网络别名，保证原版客户端可以安全加入。安装本模组的客户端登录后会自动握手并刷新为完整名称；未安装客户端继续显示短别名。服务端内部和 `/player` 命令始终使用完整名称。客户端和服务端都安装本模组后，`/tp` 等原版实体参数命令也可以直接使用最长 128 字符的玩家名。

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

- `1.21-1.21.1`
- `1.21.4`
- `1.21.8`
- `1.21.10`
- `1.21.11`
- `26.1.2`
- `26.2`

执行以下命令构建全部版本：

```text
gradlew.bat buildAllVersions
```

每次构建会在 `build/libs/<时间戳>/` 中生成 7 个正式 JAR。新的构建使用新的时间戳目录，不会删除或覆盖已有历史构建。
