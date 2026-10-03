# 玩家、载具与世界功能验收（1.6.1）

本批九项规则已适配当前全部十个构建节点，源码条件为 `MC >= 1.21.1 && MC <= 26.3`，逐节点构建、单测和隔离启动检查均已完成。真人客户端输入、显示及实际世界行为仍需按下方步骤验证

使用新建测试存档或真实存档的离线副本，不使用生产世界执行区块重生成

## 代码级验证

```powershell
.\gradlew.bat :1.21.1:build --no-daemon --configure-on-demand --max-workers=1 --offline
.\gradlew.bat :1.21.3:build --no-daemon --configure-on-demand --max-workers=1 --offline
.\gradlew.bat :1.21.4:build --no-daemon --configure-on-demand --max-workers=1 --offline
.\gradlew.bat :1.21.5:build --no-daemon --configure-on-demand --max-workers=1 --offline
.\gradlew.bat :1.21.8:build --no-daemon --configure-on-demand --max-workers=1 --offline
.\gradlew.bat :1.21.10:build --no-daemon --configure-on-demand --max-workers=1 --offline
.\gradlew.bat :1.21.11:build --no-daemon --configure-on-demand --max-workers=1 --offline
.\gradlew.bat :26.1.2:build --no-daemon --configure-on-demand --max-workers=1 --offline
.\gradlew.bat :26.2:build --no-daemon --configure-on-demand --max-workers=1 --offline
.\gradlew.bat :26.3:build --no-daemon --configure-on-demand --max-workers=1 --offline
.\scripts\powershell\new-feature-port-smoke.ps1 -MinecraftVersion <版本> -Offline
git diff --check
```

跨版本启动冒烟脚本在 `build/new-feature-port-smoke-<版本>-<时间>/` 创建独立开发服务器和测试世界，并在 `scripts/logs/` 保存服务器日志及结果摘要。它验证服务器启动、九条规则注册/设置和干净停服，不代替真人客户端或实际地形行为测试

26.3 还保留早期专项探针，用于验证容器与载具内部行为及 TIS 组合：

```powershell
.\gradlew.bat :26.3:build --no-daemon --configure-on-demand --max-workers=1 --offline
.\scripts\powershell\player-vehicle-world-smoke-26.3.ps1 -Java25Home <Java25目录>
git diff --check
```

可传 `-TisJar <匹配26.3的TIS文件>` 检查同装环境，基础探针检查原版木桶存档扩容、保存54格、模拟下一次启动快照关闭后的扩展格保护，及空扩展格在下次加载恢复27格；这个快照检查不等于实际停服与重启验收

## 26.3 专项探针结果（1.6.0 基线）

- Minecraft 26.3 完整构建及68项单元测试成功，新增6项，0失败、错误或跳过
- 基础隔离开发服务端36项检查通过：`scripts/logs/player-vehicle-world-smoke-26.3-20261003-004529-922/summary.txt`
- 同装TIS 1.82.4的隔离开发服务端37项检查通过：`scripts/logs/player-vehicle-world-smoke-26.3-20261003-004737-413/summary.txt`
- 归档包：`mod-builds/20261003-004529/carpet-fga-addition-1.6.0+v2610030042-mc26.3.jar`
- 探针模拟客户端加载完成信号后检查伤害，测试不包含真人客户端输入、显示或实际重启加载同一世界；本节是初始基线记录，不代表后续跨版本真人客户端验收

## 1.6.1 跨版本构建与启动结果

所有十个构建节点的 `build`（包含单元测试）均通过，共 427 项测试，失败、错误、跳过均为 0。隔离服务端均到达 `Done`，九条规则均接受设置，并干净关闭；未发现新 Mixin 注入错误。归档目录含对应 JAR 和 SHA-256 清单。

| Minecraft 节点 | 单元测试 | JAR 归档目录 | 隔离启动报告目录 |
| --- | ---: | --- | --- |
| 1.21.1 | 51 | `mod-builds/20261003-133652` | `scripts/logs/new-feature-port-smoke-1_21_1-20261003-133652-467` |
| 1.21.3 | 44 | `mod-builds/20261003-131711` | `scripts/logs/new-feature-port-smoke-1_21_3-20261003-131715-430` |
| 1.21.4 | 44 | `mod-builds/20261003-132014` | `scripts/logs/new-feature-port-smoke-1_21_4-20261003-132014-415` |
| 1.21.5 | 44 | `mod-builds/20261003-132213` | `scripts/logs/new-feature-port-smoke-1_21_5-20261003-132213-337` |
| 1.21.8 | 44 | `mod-builds/20261003-132401` | `scripts/logs/new-feature-port-smoke-1_21_8-20261003-132401-695` |
| 1.21.10 | 44 | `mod-builds/20261003-133030` | `scripts/logs/new-feature-port-smoke-1_21_10-20261003-133029-915` |
| 1.21.11 | 44 | `mod-builds/20261003-133030-2` | `scripts/logs/new-feature-port-smoke-1_21_11-20261003-133119-066` |
| 26.1.2 | 44 | `mod-builds/20261003-133030-3` | `scripts/logs/new-feature-port-smoke-26_1_2-20261003-133214-315` |
| 26.2 | 47 | `mod-builds/20261003-133030-4` | `scripts/logs/new-feature-port-smoke-26_2-20261003-133300-219` |
| 26.3 | 72 | `mod-builds/20261003-133031` | `scripts/logs/new-feature-port-smoke-26_3-20261003-133416-595` |

1.21.x 启动日志包含既有可选 VisibleTraders 类缺失警告。26.2 和 26.3 有 Windows Perflib/OSHI 系统报告诊断；没有阻止服务器启动或关闭，也不是 Mixin 注入错误。

## 真人客户端与游戏内验收

1. 异常退出：`abnormalDisconnectNotice` 设 `false`、`true`、`console` 各执行一次网络超时，验收关闭时无额外提示、公屏模式提示在线玩家、控制台模式仅额外输出控制台；正常返回标题界面、管理员踢人、停服、假人退出不应出现额外异常提示；原版普通 EOF 无法判别退出意图，因此不会报告该类断流
2. 载具跳跃：启用 `vehicleJump`，真人分别驾驶普通船、运输船与可乘坐矿车，在地面、水面、直轨、斜轨、动力轨按空格；验证载具与所有乘客一起跳，驾驶者与旁观客户端同步、不回弹、不出现 moved too quickly；空中反复按空格不能连续跳，第二乘客按空格不能操纵跳跃；检查矿车落地后恢复轨道运行，分别测试 minecart improvements 实验开启/关闭；关闭规则后原版行为恢复
3. 多人载具：`playerVehicleCapacity` 分别设 `false`、`4`、`8`、`25` 与自定义正整数，右键逐个上船、运输船与可乘坐矿车，达到总人数上限后拒绝下一人，第一乘客驾驶行为保持；非玩家生物不获得扩展座位，运输矿车等非乘坐类型不扩展；25人时有建议开启 `vehicleNoCramming` 的提示；新增玩家沿用原版乘客附着点，可能重叠显示
4. 载具挤压：在 boat/minecart 上挤压乘客，`vehicleNoCramming=true` 时不受 cramming 伤害，关闭后恢复；火焰、窒息、摔落、攻击等其他伤害仍正常，离开载具后普通挤压伤害正常
5. 木桶：使用 `/carpet setDefault doubleBarrelCapacity true` 并重启，检查新旧木桶都为54格；普通客户端六行界面、Shift移动、漏斗存取、比较器、战利品木桶及破坏掉落全部物品；填写第54格，实际停服并重启检查物品保留；保存默认 false 并重启，非空扩展格的木桶保留54格供取出，取空后在下次重启或区块加载时恢复27格；同装 TIS 时启用其 largeBarrel 后重启，控制台提示由 TIS 管理，避免重复扩容；TIS 大木桶合并与 FGA 单桶54格语义不同，切换到 TIS 大木桶前须清空 FGA 扩展格，运行中保持 TIS 容量设置不变
6. 结冰：`iceFormationChances=false` 与 `0,0` 都生成普通冰，`100,0` 只生成浮冰，`0,100` 只生成蓝冰，`30,10` 大样本接近30%浮冰、10%蓝冰、60%普通冰；测试新区块自然结冰及天气结冰，已有冰、冰霜行者暂时冰和玩家放置方块不被替换；`60,60`、负数、小数、单项超过100等输入应被拒绝
7. 进食饮用：`fastEating=true` 时面包、肉类、金苹果、药水、牛奶、蜂蜜瓶最多8 Tick完成，数量、饥饿与效果、空瓶或桶均保持原版；本来更快的自定义消耗不减速；饱腹不能吃普通食物，取消使用不消耗物品，弓盾望远镜等不加速；关闭后恢复原时长；验证无 FGA 客户端持按右键的显示与连续消耗
8. 禁止积雪：`noSnowAccumulation=true` 时新天气雪层不产生、已有雪层不加厚，新生成雪地的自然雪层不放置；已有雪层保留，人工放置与雪傀儡正常，雪块、细雪不被清理，天气及水结冰保留；关闭后降雪积累恢复
9. 平坦基岩：`flatBedrock=false`、`true`、`2`、`5` 分别生成主世界与下界的新区块，验收原版随机层或对应连续层数；主世界多余基岩变深板岩，下界上下边界多余基岩变下界岩，下界顶部以生成高度127为界而不是世界高度255；既有区块不变化；测试命令指定重生成后的基岩，启用虚空世界时新虚空区块不放置任何方块（包括构成结构的方块），结构起点与引用数据仍保留，指定正常重生成区块仍按基岩规则生成；末地及无基岩边界的自定义生成器不补基岩

## 首版限制与待人工确认

- 真人载具跳跃、客户端预测/显示、多人座位显示、网络异常提示和自然天气统计必须人工验证
- 自定义人数以正整数表达，不提供无限人数语义；很大的容量不会主动生成玩家或占用席位，但实际乘客数量仍影响服务器性能
- 禁止积雪替代了清雪命令，未增加清雪命令、来源追踪存档或全世界扫描
- 木桶仍使用原版 Items 槽位0–53；关闭规则保留已有扩展物品，但卸载 FGA/TIS 前必须取出这些物品，因为原版27格容器无法读取扩展槽
- 十个构建节点均已完成代码级验证；真人载具跳跃、多人乘客显示/预测、网络异常提示、自然天气概率、禁雪和跨版本地形结果仍需逐版本游戏内检查；木桶需要用测试存档实际停服/重启，并继续检查 TIS 大木桶组合

## 本批文件范围

- `src/main/java/carpet/fga/FGASettings.java`：九项规则、输入校验、重启及超过24人的提示
- `src/main/java/carpet/fga/FGAExtension.java`：启动完成后读取木桶容量快照，关闭服务器时清理快照
- Manager/接口：`BarrelCapacityManager.java`、`FlatBedrockManager.java`、`NaturalIceManager.java`、`NewFeatureOptions.java`、`VehicleJumpAccess.java`
- 新增Mixin：`AbnormalDisconnectNoticeMixin.java`、`DoubleBarrelCapacityMixin.java`、`FastEatingMixin.java`、`FlatBedrockGenerationMixin.java`、`NaturalWeatherFeaturesMixin.java`、`NaturalWorldgenFeaturesMixin.java`、`PlayerBoatFeaturesMixin.java`、`PlayerVehicleCapacityMixin.java`、`PlayerMinecartInteractionMixin.java`、`PlayerMinecartJumpMixin.java`、`PlayerVehicleInputMixin.java`、`LegacyPlayerVehicleInputMixin.java`、`LegacyPlayerMinecartJumpMixin.java`、`VehicleNoCrammingMixin.java`
- `src/main/resources/carpet-fga-addition.mixins.json`：十个构建节点均注册十二项对应required钩子；1.21.1 使用两个 legacy 载具输入/跳跃钩子，后续版本注册现代实现
- `src/main/resources/assets/carpet-fga-addition/lang/en_us.json`、`zh_cn.json`、`zh_tw.json`：规则说明与服务端可见提示，不新增英文规则名称
- `docs/rules_ch_cn.md`、`docs/rules_en_us.md`、`docs/version_compatibility.md` 与本文件：行为、版本门控、实测状态及人工验收步骤
- `src/test/java/carpet/fga/NewFeatureOptionsTest.java`、`NewFeatureMixinTargetTest.java`：概率互斥/边界、数值校验及26.3对应方法签名和资源门控
- `src/test/java/carpet/fga/smoke/PlayerVehicleWorldProbe.java`、`src/test/resources/player-vehicle-world-probe/fabric.mod.json`：只进入隔离测试模组，检查真实Mixin作用后的库存、菜单、乘客、伤害和消耗
- `scripts/powershell/player-vehicle-world-smoke-26.3.ps1`、`scripts/gradle/player-vehicle-world-smoke-isolated.init.gradle`：独立测试运行目录与报告，支持可选同装TIS

上一轮审查修复的文件继续保留，本批没有修改README、依赖版本、映射、许可证或历史发布记录，未创建发布或推送
