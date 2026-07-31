# Carpet FGA Addition

Carpet FGA Addition 是一个面向服务器的 Fabric Carpet 扩展，提供假人、掉落物、村民、命令兼容和性能辅助功能。所有功能默认关闭，按需通过 `/carpet` 规则启用。

## 支持版本

当前发布版本为 `1.4.0`，提供以下 Minecraft 构建：

`1.16.5`、`1.17.1`、`1.18.2`、`1.19.2`、`1.19.4`、`1.20.1`、`1.20.4`、`1.20.6`、`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`。

安装与服务器 Minecraft 版本匹配的 JAR。服务端功能通常不要求客户端安装本模组；长假人名称完整显示等功能需要客户端同时安装。

## 功能概览

- 假人名称长度、长名称兼容别名、档案预加载和区域操作。
- `1.21.1` 假人全物品分类：支持 `summon` 与 `quickopen`，离线 playerdata 读写、潜影盒路由、网页缓存和库存重构。
- 统一掉落物预堆叠：生物死亡、方块掉落、容器内容和漏斗矿车掉落可分别配置。
- 可配置地面掉落物堆叠上限、黑名单和白名单。
- `/fill` 与 `/fillbiome` 体积限制兼容处理。
- 村民繁殖、村民性能优化、职业赠礼和敌对生物装备栏访问。
- 流浪商人不消失、末地折跃门再生、生命显示和观察者自身传送等服务器辅助功能。
- Unicode 指令参数、客户端维度 ID、对话框确认警告控制和 26.2 蜜蜂碰撞箱兼容。

## 常用配置

```text
/carpet unlimitedFillCommands true
/carpet preStackDroppedItems true
/carpet wanderingTraderNoDespawn true
/carpet endGatewayRegeneration true
```

规则的可用版本和默认值以当前服务器的 `/carpet list` 为准。

## 掉落物预堆叠

新配置默认关闭，配置保存于世界目录：
`carpet/carpetfgaaddition/drop-pre-stack.json`。

```text
/carpet preStackDroppedItems true
/dropPreStack entity add minecraft:zombified_piglin 1.5
/dropPreStack entity add minecraft:hopper_minecart 1
/dropPreStack block add minecraft:stone 1
/dropPreStack entity list
/dropPreStack block list
```

`add`、`remove`、`set` 和 `list` 均支持 `/fga dropPreStack` 前缀。列表会显示中文名称、英文 ID 和范围；损坏的配置文件不会覆盖原文件。

## 假人全物品分类

该功能仅在 Minecraft `1.21.1` 构建中启用：

```text
/carpet fakePlayerItemSortMode quickopen
/player <假人名> bot_sort
```

`quickopen` 直接读写离线 playerdata，不为访问背包召唤假人；`summon` 使用 Carpet 在线假人。首位分类假人保存散货，后续假人保存符合规则的盒装物品；装备栏不会被分类器读取或写入。

## 构建

需要 Java 21（1.21.x）或对应版本要求的 Java。全版本构建：

```powershell
.\gradlew.bat buildAllVersions --no-daemon
```

构建脚本、兼容性脚本和本地日志位于 [`scripts/`](scripts/)。构建产物位于 `build/` 和 `mod-builds/`，默认不纳入 Git。

## 许可与致谢

本项目按仓库许可发布。部分设计参考了 MIT 许可的 Org Addition、SaveMyRecipeBook 和 InventoryAdvancementAccelerator，并在 JAR 的 `META-INF/NOTICE-*` 中保留归属说明。
