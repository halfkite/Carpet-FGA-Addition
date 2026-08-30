# Carpet FGA Addition

Minecraft 1.21.1 adds the server-side `resilientPlants` rule for placing selected plants without normal survival support.
Minecraft 1.21.1 also adds `comparatorThroughBlocks`, allowing comparators to read a container signal through configured blocks such as chains or pistons.
It also adds the opt-in `woodStonecuttingRecipes` rule, allowing wood products to be crafted in the stonecutter with bidirectional wood conversion, log-to-stair/slab yields of 4/8, plank-to-stair/slab yields of 1/2, bamboo mosaics, boats, bamboo rafts, and server-authoritative bamboo input counts, with bamboo blocks, stripped bamboo blocks, and 9 bamboo accepted as equivalent bamboo inputs.

Carpet FGA Addition 是一个面向服务器的 Fabric Carpet 扩展，提供假人、掉落物、村民、命令兼容、生命显示和性能辅助功能。除特别说明外，功能默认关闭，通过 `/carpet` 规则启用。

## 支持版本

当前维护线从 Minecraft `1.21` 系列开始，包含 `1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2` 和 `26.2` 共 9 个构建平台，覆盖 Minecraft `1.21`-`1.21.11` 与 `26.1`-`26.2` 之间的全部兼容版本。Minecraft `1.21` 与 `1.21.1` 共用标记为 `mc1.21-1.21.1` 的发行 JAR，不提供独立 `mc1.21` 构建。Minecraft `1.20.6` 及更早版本已停止维护，历史版本可从 Release 页面下载 `1.5.5` 及更早版本。

## 文档

全部支持版本提供虚空新区块生成，Minecraft `1.21-26.2` 还提供可排队、重启执行的正常地形重生成和全空气区块清除功能

- [中文规则说明](https://github.com/halfkite/Carpet-FGA-Addition/blob/main/docs/rules.md)
- [English rules](https://github.com/halfkite/Carpet-FGA-Addition/blob/main/docs/rules_en.md)
- [中文命令说明](https://github.com/halfkite/Carpet-FGA-Addition/blob/main/docs/commands.md)
- [English commands](https://github.com/halfkite/Carpet-FGA-Addition/blob/main/docs/commands_en.md)
- [发布流程](https://github.com/halfkite/Carpet-FGA-Addition/blob/main/docs/releasing.md)

## 功能概览

- 假人区域操作、名称兼容、Unicode 参数和末地折跃门再生。
- Minecraft `1.21.1` 的玩家末地门传送控制：可统一阻止三类末地门传送，或通过 `/playertpend` 为每名玩家设置进入末地门、出口和折跃门的允许/阻止偏好。
- 村民交易/赠礼优化、流浪商人保护和敌对生物装备栏访问。
- Minecraft `1.21-26.2` 幼年生物成长锁定：支持 `true`、`mini` 和完整自定义名称，包括蝌蚪，纯服务端生效。
- Minecraft `1.21.1` 基岩版潜影贝复制：潜影贝被潜影贝子弹击杀时必定在原地重生一只新潜影贝，继承颜色与附着面，纯服务端生效。
- 地面物品堆叠上限、掉落物预堆叠、掉落距离和 `/fill` 兼容修复。
- 配方书解锁、隐藏的背包进度优化、Tab 列表生命显示和客户端维度 ID 映射。
- Minecraft `1.21.1` 展示框方块化：将展示框移出服务端实体 tick 调度，以支撑方块更新驱动存活验证，降低大量展示框的服务端开销。
- Minecraft `1.21.1` 烟花矿车与锁链列车：乘车使用烟花维持可配置高速，使用锁链将普通矿车连接为持久线性列车。
- 全部支持版本载具离开急停：可按矿车、船或每名玩家的个人设置，在驾驶者离开时清除载具水平速度。
- Minecraft `1.21-1.21.11` 深板岩切石补全：让深板岩直接切制为 26.1+ 原版提供的全部深板岩制品。
- Minecraft `1.21-26.2` 满潜影盒复杂合成：直接读取运行时普通配方，支持多材料、标签、数据包配方和 Shift/Q 补货，并软兼容 Carpet AMS Addition 的 `largeShulkerBox` 54 格大潜影盒。
- Minecraft `1.21-26.2`（不含 `1.21.3`）假人全物品分类核心：提供离线 `quickopen`、在线 `summon`、白名单、命名和潜影盒分类；`1.21.1` 额外保留补货、重构、磁盘缓存、线程配置和 Dashboard/API。
- Minecraft `1.21-1.21.11` 深板岩直接切石开关 `deepslateStonecuttingRecipes`，让深板岩在切石机中的表现与 26.1+ 一样
- Minecraft `1.21-26.2` 农民不再把小麦合成面包，以及交易界面保持打开时完成村民升级并立即刷新交易
- Minecraft `1.21.1` 玩家加载距离规则与 `/playerLoadDistance`，支持临时、持久、`-1`、`0`、`1-32` 和 `none`
- Minecraft `1.21.1` 潜影贝攻击盔甲架：可攻击全部盔甲架或仅头戴雕刻南瓜的盔甲架（`pumpkin`）
- Minecraft `1.21.1` 铁砧惩罚取消：`anvilNoPriorWorkPenalty` 可取消重复工作费用增长和“过于昂贵”限制，同时保留附魔冲突检查与材料消耗
- Minecraft `1.21.1` 经验升级消耗扁平化：`experienceLevelCost` 可让 30 级后固定使用 29 到 30 的经验，或让所有等级固定使用 0 到 1 的经验
- Minecraft `1.21.1` 潜影壳掉落同步基岩版：固定 50% 掉落，掉落时 1 至 1+抢夺等级 个均匀分布
- Minecraft `1.21-26.2` 试炼刷怪笼等效人数与一次性截停：可按玩家名称前缀放大刷怪和奖励规模，并按水平半径或 XYZ 方框截停、刷新已加载刷怪笼

## 重要说明

假人全物品分类核心在 Minecraft `1.21-26.2` 注册。`quickopen` 直接读写目标假人的离线 `playerdata`，不会为了访问背包召唤假人；`summon` 使用在线 Carpet 假人。分类器从不读取或写入装备栏。除 `1.21.1` 外只使用运行期内存索引，不启动 Dashboard/API、磁盘路由缓存、库存重构、自动补货或线程调优。

世界配置保存在 `world/config/carpetfgaaddition/`。从旧版本升级时，`world/carpet/carpetfgaaddition/` 中的文件会在验证成功后迁移，并改名为 `.migrated` 备份；损坏文件不会被覆盖或删除。

## 主要命令

完整语法、权限、版本限制、分页和点击帮助请查看[命令文档](https://github.com/halfkite/Carpet-FGA-Addition/blob/main/docs/commands.md)。常用入口包括：

```text
/dropPreStack help
/villagerPerformance help
/fakePlayerItemSort status
/fakePlayerItemSort help
/fakePlayerItemSort mode summon|quickopen
/minecart help
/vehicleStop help
/fga trialStop help
/player <假人> bot_sort
/log playerHealth
/fga help
```

## 许可证与致谢

项目主体按 [MIT License](LICENSE) 发布。来自 LGPL 项目的适配部分继续遵守对应 LGPL 条款。部分设计参考或移植自 MIT 许可的 Org Addition、SaveMyRecipeBook、InventoryAdvancementAccelerator 和 StackSizeTweaks，归属说明随 JAR 的 `META-INF/NOTICE-*` 文件分发。
