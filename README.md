# Carpet FGA Addition

Carpet FGA Addition 是一个面向服务器的 Fabric Carpet 扩展，提供假人、掉落物、村民、命令兼容、生命显示和性能辅助功能。除特别说明外，功能默认关闭，通过 `/carpet` 规则启用。

## 支持版本

当前版本为 `1.4.2`，提供 Minecraft `1.16.5`、`1.17.1`、`1.18.2`、`1.19.2`、`1.19.4`、`1.20.1`、`1.20.4`、`1.20.6`、`1.21`、`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2` 和 `26.2`。

## 文档

- [中文规则说明](https://github.com/halfkite/Carpet-FGA-Addition/blob/main/docs/rules.md)
- [English rules](https://github.com/halfkite/Carpet-FGA-Addition/blob/main/docs/rules_en.md)
- [中文命令说明](https://github.com/halfkite/Carpet-FGA-Addition/blob/main/docs/commands.md)
- [English commands](https://github.com/halfkite/Carpet-FGA-Addition/blob/main/docs/commands_en.md)

## 功能概览

- 假人区域操作、名称兼容、Unicode 参数和末地折跃门再生。
- 村民交易/赠礼优化、流浪商人保护和敌对生物装备栏访问。
- 地面物品堆叠上限、掉落物预堆叠、掉落距离和 `/fill` 兼容修复。
- 配方书解锁、隐藏的背包进度优化、Tab 列表生命显示和客户端维度 ID 映射。
- Minecraft `1.21.1` 独有的假人全物品分类、离线 `quickopen`、在线 `summon`、潜影盒处理、库存重构和网页缓存 API。

## 重要说明

假人全物品分类只在 Minecraft `1.21.1` 注册。`quickopen` 直接读写目标假人的离线 `playerdata`，不会为了访问背包召唤假人；`summon` 使用在线 Carpet 假人。分类器从不读取或写入装备栏。

世界配置保存在 `world/config/carpetfgaaddition/`。从旧版本升级时，`world/carpet/carpetfgaaddition/` 中的文件会在验证成功后迁移，并改名为 `.migrated` 备份；损坏文件不会被覆盖或删除。

## 主要命令

完整语法、权限、版本限制、分页和点击帮助请查看[命令文档](docs/commands.md)。常用入口包括：

```text
/dropPreStack help
/villagerPerformance help
/fakePlayerItemSort status
/player <假人> bot_sort
/log playerHealth
/fga help
```

## 许可证与致谢

项目主体按 [MIT License](LICENSE) 发布。来自 LGPL 项目的适配部分继续遵守对应 LGPL 条款。部分设计参考了 MIT 许可的 Org Addition、SaveMyRecipeBook 和 InventoryAdvancementAccelerator，归属说明随 JAR 的 `META-INF/NOTICE-*` 文件分发。
