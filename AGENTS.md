# AGENTS.md

本文件供 Codex / Agent 在 Carpet FGA Addition 仓库中工作时使用，只维护长期有效的工作规则；当前版本号、规则清单和适配矩阵记录在 `docs/version_compatibility.md` 等对应文档中。

项目事实以当前源码、构建配置和 `docs/` 为准。

---

## Carpet FGA Addition 项目指令

Carpet FGA Addition 是基于 Minecraft Carpet Mod 的 Carpet Extension。

仓库地址：[https://github.com/halfkite/Carpet-FGA-Addition](https://github.com/halfkite/Carpet-FGA-Addition)

分析规则、命令、兼容性、API 或项目结构时，优先按 Carpet Extension 生态理解；缺少相关信息时，可主动查询 Carpet Mod / Carpet Extension 公开资料，不要默认按普通 Fabric Mod 处理。

不确定的信息统一标记为“待确认项”。没有看到当前仓库源码、文档、日志、diff 或 Codex 分析时，不要声称已经确认具体实现。

### 网页端 GPT 与 Codex 分工

网页版 GPT 主要负责：需求澄清、方案规划、Codex 计划模式提示词、计划审查、代码 / diff / 日志审查、Codex 修复提示词。默认不直接承担实际代码修改。

实际代码修改、构建、自动化测试和发布由用户确认后的 Codex 实施流程完成。若用户明确要求当前 Agent 直接修改代码，应以用户的明确授权为准，同时遵守本文件其余约束。

---

## 默认 Codex 流程

新增或修改规则、命令、bug 修复、跨版本兼容、移植、重构、发布或文档更新等代码任务，默认采用以下流程：

1. 计划阶段

   生成 Codex 计划模式提示词，只允许分析源码、文档、日志并制定计划，不修改代码。

2. 计划审查阶段

   用户贴回 Codex 计划后，由网页版 GPT 审查正确性、完整性、风险及是否可以实施。

3. 实施阶段

   仅在计划通过且用户确认后，生成 Codex 实际修改代码的提示词。

用户首次要求“直接生成修复提示词”时，应提醒更推荐先走计划阶段；除非用户再次明确确认，否则不要跳过计划审查。

bug 排查 / 修复的计划阶段必须要求 Codex 检查 `.minecraft/versions/` 下相关实例日志，重点查看 `debug.log`，同时根据需要检查 `latest.log` 和崩溃报告。

---

## 需求澄清

收到新增规则、命令、bug、移植、重构、发布、文档更新等需求时，先判断信息是否足够，不要直接生成实施提示词。默认按以下结构回答：

1. 结论：是否适合继续规划
2. 还需要的信息：按“必须提供 / 建议提供 / 可选提供”分类
3. 需要确认的细节：
   - 规则名 / 内部名 / 中英文介绍
   - 类型 / 默认值 / 可选值 / 分类
   - 支持版本及 source set；没有说明时默认检查项目当前覆盖的所有版本
   - 服务端 / 客户端生效范围
   - 命令参数 / 权限 / 输出
   - logger / 翻译 / README / 文档
   - 版本号
   - 自动化与人工测试方式
4. 风险与待确认项：
   - 性能、存档兼容、多版本兼容、Carpet API 兼容
   - 红石 / 生电机器影响、刷怪塔效率
   - 客户端 / 服务端安装要求及其他行为兼容性
5. 是否可以进入 Codex 计划阶段

信息不足时明确说明“暂不建议写 Codex 提示词”；信息足够时询问是否生成 Codex 计划模式提示词。

---

## Codex 计划模式提示词要求

计划模式提示词应精炼，并明确要求：

- 进入计划模式，本轮只分析和规划，不修改代码
- 阅读仓库相关文档、源码、已有实现、版本 source set 和构建配置
- bug 任务检查 `.minecraft/versions/` 下相关实例日志，重点查看 `debug.log`
- 对比正常路径与异常路径，确认问题来源
- 输出可审查的实施 / 修复计划
- 多版本任务列出所有相关 source set / platform
- 涉及规则、命令、logger、翻译、README、版本号时，列出需要同步的文件
- 无法确认的实现范围、跨版本差异、兼容性或验证结果列入“待人工确认项”

默认要求 Codex 按以下结构输出：

1. 问题理解和复现现象
2. 相关文档和源码位置
3. 可能原因分析
4. 建议实施 / 修复方向
5. 涉及版本 / source set
6. 验证方案
7. 风险与待人工确认项

本文件已经规定的通用约束无需在每次提示词中重复，例如英文环境直接显示规则内部名、不单独维护英文规则名。

---

## Codex 计划审查要求

用户贴回 Codex 计划后，重点检查：

- 是否正确理解需求和现象
- 是否有源码、文档、日志依据，而非猜测
- bug 是否检查相关 `debug.log`
- 是否区分正常路径与异常路径
- 是否覆盖所有相关版本 / source set
- 修复是否最小、合理、可维护
- 是否会破坏已有规则、命令语义、权限、配置或行为
- 是否遗漏 logger、翻译、中英文文档、README、版本号、测试说明等同步项
- 是否遗漏性能、存档、多版本、Carpet API、生电 / 红石、刷怪塔、客户端 / 服务端要求等风险
- 是否存在必须人工测试或确认的内容

审查结果默认按以下结构输出：

1. 结论
2. 计划可行点
3. 需要补充或修正的地方
4. 建议追加给 Codex 的约束
5. 是否建议进入实施阶段

计划不足时明确说明“暂不建议进入修复阶段”；计划可行时说明“可以按该计划实施”，但仍等待用户确认后再生成实际修复提示词。

---

## Codex 实施 / 修复提示词要求

仅在以下条件满足后生成：

- 信息已经足够
- 计划已经审查通过，或用户明确确认跳过计划流程
- 用户确认需要实际修改代码的提示词

提示词应精炼，只写本次任务特有内容，不重复整份 `AGENTS.md`。至少包含：

- 本次目标与行为要求
- 允许修改的文件 / source set 范围
- 不修改、不过度格式化无关文件
- 多版本任务同步所有相关版本，不能只修改当前版本
- 涉及规则、命令、logger、翻译、README、版本号时同步对应资源和文档
- 不确定内容最后列入“待人工确认项”
- 执行必要的代码级验证，如编译、静态检查、测试、`git diff --check`

如果 Minecraft 游戏内验证由用户或其他 Agent 执行：

- Codex 不需要擅自启动 Minecraft 客户端或服务端
- Codex 负责代码层自动化验证
- Codex 必须输出详细的人工测试步骤、覆盖场景和验收标准

---

## 基本原则

* 修改前先阅读相关源码、文档和构建配置，不根据文件名猜实现。
* 优先做最小必要修改，不进行无关重构、格式化、依赖升级或发布调整。
* 不能验证的内容明确说明，不得写成“已确认”或“已通过”。
* 编译通过不等于游戏内测试通过。
* 无法确认的跨版本、兼容性、数据或许可证问题统一列入“待人工确认项”。

如果源码、docs、README 或构建配置互相冲突，应报告冲突，不要自行选择一方作为正确答案。

---

## 项目结构

### `src/main/java/carpet/fga/`

项目主要实现，包括：

* Carpet Extension；
* 规则；
* 命令；
* Manager / Config；
* Logger；
* 网络和兼容逻辑。

### `src/main/java/carpet/fga/mixin/`

Mixin 和 Accessor。

修改时必须确认目标版本的：

* 类和方法；
* descriptor；
* 注入点；
* `carpet-fga-addition.mixins.json`；
* 预处理条件。

### `src/main/java/com/yiyihehe/quickcraft/`

QuickCraft 兼容 / 协议实现。

涉及此目录时，同时检查网络协议、客户端兼容和第三方许可证。

### `src/main/resources/`

主要包括：

* `fabric.mod.json`
* `carpet-fga-addition.mixins.json`
* `assets/carpet-fga-addition/lang/`
* 数据资源
* `META-INF/NOTICE-*`
* `META-INF/LICENSE-*`

部分资源由 Gradle 按 Minecraft 版本动态生成，修改前先确认资源来源。

### `versions/<版本>/`

主要保存各 Minecraft 版本的构建属性，不是独立 Java 源码目录。

### `versions/mapping-*.txt`

Preprocessor 跨版本符号映射文件。

仅在确有符号差异时修改。

### `docs/`

当前规则、命令和发布说明。

功能行为变化时应同步相关中英文文档。

### `scripts/`

构建、Smoke Test、Benchmark 和测试 Fixture。

执行前先阅读脚本，涉及世界或玩家数据时只能使用测试环境。

---

## 固定项目标识

除非任务明确要求重命名，否则不要修改：

| 类型                 | 固定值                               |
| ------------------ | --------------------------------- |
| 项目名                | `Carpet FGA Addition`             |
| Mod ID             | `carpet-fga-addition`             |
| Java 主包            | `carpet.fga`                      |
| Maven Group        | `carpet`                          |
| Archives Base Name | `carpet-fga-addition`             |
| Fabric 入口          | `carpet.fga.CarpetFGAAddition`    |
| Carpet Extension   | `carpet.fga.FGAExtension`         |
| 规则类                | `carpet.fga.FGASettings`          |
| Carpet 分类          | `FGA`                             |
| Resource Namespace | `carpet-fga-addition`             |
| Mixin Package      | `carpet.fga.mixin`                |
| 世界配置目录             | `world/config/carpetfgaaddition/` |
| GitHub             | `halfkite/Carpet-FGA-Addition`    |

相关标识修改时必须同步检查 `gradle.properties`、`fabric.mod.json`、Java 常量、资源 namespace 和 Mixin 配置。

---

## 多版本 Preprocessor

本项目使用 Fallen-Breath Preprocessor。

以下不是普通注释，严禁被格式化或清理：

```java
//#if MC >= 1.21
...
//#else
//$$ ...
//#endif
```

修改跨版本代码前必须检查：

* `settings.json`
* `build.gradle` preprocess graph
* 当前 `//#if` 条件
* 对应 `versions/<版本>/gradle.properties`
* 必要的 `versions/mapping-*.txt`
* `common.gradle`

不要：

* 在 `versions/<版本>/` 复制完整 Java 类来绕过预处理；
* 因一个版本验证成功而扩大 `//#if` 范围；
* 为了减少分支强行合并存在 API 差异的版本。

Java 和 Loom 版本也会随 Minecraft 版本变化，不要为了本机环境修改项目兼容范围或提交个人 JDK 路径。

### 适配版本记录

每次新增功能、修改已有功能，或把功能同步到其他 Minecraft 版本时，必须同步更新 `docs/version_compatibility.md`。

记录至少包含：功能或规则名称、源码预处理条件、实际适配版本、已完成编译/构建版本、尚未适配版本，以及客户端/服务端要求（如有）。

版本必须写出明确版本号；只有确实覆盖全部对应版本时才可以使用 `1.21+` 这类范围写法。只在单个版本实现的功能必须明确标记为单版本，不能用“已同步”概括未构建或未测试的版本。

构建、测试或代码门控发生变化时，先更新该记录再在回复中报告适配状态；源码、规则文档和版本记录不一致时必须报告并修正。

---

## Mixin

`carpet-fga-addition.mixins.json` 为跨版本预处理资源。

默认保持现有：

* `required`
* injector `require`
* client / common 分区

不要通过：

* `required=false`
* `require=0`
* 删除失败 Mixin
* 静默捕获异常

来掩盖兼容问题。

Mixin 失败应优先检查：

* Minecraft 签名变化；
* Carpet / Fabric API 差异；
* descriptor；
* 注入点；
* 预处理条件。

新增或移除 Mixin 时同步检查 Java 类、Mixin JSON、规则和文档。

---

## 规则 / 命令 / Logger

### 规则

至少检查：

* `FGASettings.java`
* Validator / Condition
* 对应 Manager / Mixin
* `FGAExtension.java`
* Rule Observer
* `carpet-fga-addition.mixins.json`
* `en_us.json`
* `zh_cn.json`
* `docs/rules_ch_cn.md`
* `docs/rules_en_us.md`

FGA 规则注册到 Carpet 主 SettingsManager，通过 `/carpet` 管理。

不要为普通规则额外创建独立 SettingsManager。

### 命令

至少检查：

* `FGAExtension.registerCommands()`
* 命令实现
* 权限和命令可见性
* Tab 补全
* Rule Observer / 命令树刷新
* 配置读写
* `docs/commands.md`
* `docs/commands_en.md`

权限变化会改变命令树时，必须确保在线玩家正确刷新命令。

### Logger

Logger 指 Carpet `/log` 体系，不是服务端 Log4j / SLF4J 日志。

至少检查：

* `registerLoggers()`
* Logger 注册
* 订阅状态
* Tick / 事件触发
* 清理生命周期
* 文档

高频 Logger 必须先判断是否存在订阅者。

---

## 翻译和玩家文本

FGA 翻译主要由：

```text
assets/carpet-fga-addition/lang/en_us.json
assets/carpet-fga-addition/lang/zh_cn.json
```

和 `FGATranslations` 提供。

新增或修改规则时同步检查翻译。

不要把大量玩家可见文本直接散落硬编码在 Mixin 中。

玩家文本必须符合功能实际安装模型：

* 纯服务端功能不能依赖客户端安装 FGA 才能正常显示；
* 客户端增强功能按现有协议处理；
* 不得意外破坏未安装 FGA 客户端的兼容路径。

---

## 客户端 / 服务端与网络

FGA 主要面向服务器，但并非全部功能都是纯服务端。

任何相关修改都要明确：

* 服务端是否必须安装；
* 客户端是否必须安装；
* 未安装 FGA 客户端是否可用；
* 是否存在自定义 Payload；
* 是否修改 vanilla 数据包；
* 是否存在客户端 Mixin。

涉及网络时至少检查：

* Payload ID 和方向；
* Codec；
* Receiver；
* 版本条件；
* 线程切换；
* 未安装 / 旧客户端兼容；
* 服务端输入校验。

服务端不得信任客户端提供的坐标、实体、ItemStack、数量、权限或 Capability 声明，必须重新校验。

不得让客户端专用类进入 Dedicated Server 必经的类加载路径。

---

## 配置和数据安全

世界配置默认位于：

```text
world/config/carpetfgaaddition/
```

修改配置系统时必须考虑：

* 旧配置迁移；
* 损坏文件；
* 安全默认值；
* 原子写入；
* 写入失败反馈；
* 向后兼容。

不要静默删除或覆盖损坏配置。

涉及离线玩家 `playerdata` 时属于高风险修改，必须考虑：

* 玩家在线 / 离线竞争；
* NBT 兼容；
* 原子写入；
* 崩溃恢复；
* 数据备份；
* 背包与装备栏边界。

---

## 世界 / 区块修改

地形重生成、区块清除、虚空生成等功能会永久修改存档。

修改时必须检查：

* 权限；
* Dimension 和范围；
* 预览 / 二次确认；
* 重启队列；
* Region 备份；
* 方块实体 / 实体 / POI；
* Scheduled Tick；
* Heightmap / Lighting；
* 边缘流体；
* 失败恢复 / Retry。

不得降低已有安全保护。

自动测试只能使用测试存档。

---

## 性能

以下属于高频路径：

* `onTick`
* Entity Tick
* Item Entity / Hopper
* 假人分类
* 展示框
* 村民 AI
* 玩家加载距离
* Logger
* 大范围扫描

原则：

* 规则关闭时尽早返回；
* 无订阅者时不构建 Logger 内容；
* 不每 Tick 扫描整个世界；
* 优先复用缓存和索引；
* 不在 Tick 线程做阻塞磁盘 I/O；
* 不无界创建异步任务；
* 高频路径不大量输出 INFO。

性能优化必须验证行为等价。

---

## 第三方来源与许可证

项目主体为 MIT，但仓库包含第三方来源实现和许可证文件：

```text
src/main/resources/META-INF/NOTICE-*.txt
src/main/resources/META-INF/LICENSE-*.txt
```

涉及第三方代码的新增、修改、移植、重写或删除时必须检查对应 NOTICE 和许可证。

不要擅自：

* 删除 NOTICE；
* 删除 LGPL 许可证；
* 将派生实现弱化为“灵感参考”；
* 将 LGPL 派生代码标成 MIT。

无法确认许可证关系时，保留现有声明并列入“待人工确认项”。

---

## 文档和发布

### 规则介绍格式

规则介绍正文不使用句号，包括中文句号 `。` 和英文句点 `.`

当规则在排除 `false`、`ops`、`0`、`1`、`2`、`3`、`4` 后仍有两个以上非自定义选项时，每个选项必须单独换行，并说明该选项的用途

自定义列表、字符串或数值范围属于自定义输入时，说明输入格式和整体行为，不把每个自定义值当作固定选项逐项列出

选项说明必须与游戏内规则描述、`Options` 列表和实际实现保持同步

规则变化检查：

```text
docs/rules_ch_cn.md
docs/rules_en_us.md
```

命令变化检查：

```text
docs/commands.md
docs/commands_en.md
```

发布相关变化重新检查：

```text
gradle.properties
build.gradle
settings.json
.github/workflows/
docs/releasing.md
```

不要把 AGENTS 中的信息当作当前发布矩阵来源。

`docs/releases/` 是历史记录，除非明确要求，不随当前版本变化批量修改。

不要提交任何 Token、Secret 或个人环境配置。

---

## 构建与验证

默认使用 Gradle Wrapper。

仅 Markdown：

```powershell
git diff --check
```

修改 Java、Mixin、资源或构建配置：

```powershell
git diff --check
.\gradlew.bat :<版本>:compileJava
```

涉及完整资源处理或打包时：

```powershell
.\gradlew.bat :<版本>:build
```

跨版本共享代码必须验证所有实际受影响版本。

可以使用：

```powershell
.\gradlew.bat buildAllVersions
```

但它只代表当前构建脚本选择的版本集合，不等于 `settings.json` 中所有节点均已验证。

最终回复必须准确说明实际验证了哪些版本。

已有对应 Smoke / Benchmark 时优先复用，但执行前必须阅读脚本并确认不会破坏真实存档。

没有实际运行就不要写“Smoke Test 通过”。

---

## Bug 日志

Bug 排查优先检查：

```text
.minecraft/versions/<实例>/logs/debug.log
.minecraft/versions/<实例>/logs/latest.log
.minecraft/versions/<实例>/crash-reports/
```

重点看 `debug.log`。

新增日志时：

* 高频路径不刷屏；
* 正常 Tick 不大量 INFO；
* 配置 / 写入 / 兼容失败可 WARN；
* 调试信息优先 DEBUG；
* 不记录 Token、隐私或无关敏感路径。

---

## 默认不要修改

除非任务明确要求，默认不要修改：

* `README.md`
* `MODRINTH_DESCRIPTION.md`
* `.gitignore`
* `LICENSE`
* `.github/workflows/`
* `docs/releases/`
* `versions/mapping-*.txt`
* `scripts/fixtures/`
* `AGENTS.md`
* 与任务无关的 Gradle 配置、Smoke 或 Benchmark

NOTICE / LICENSE 默认不修改，但涉及对应第三方实现时必须同步检查。

---

## 完成后的回复要求

回复至少说明：

1. 修改了哪些文件以及原因；
2. 涉及哪些 Minecraft 版本 / 预处理条件；
3. 执行了哪些验证命令；
4. 哪些版本实际完成编译 / 构建；
5. 哪些游戏内或客户端行为未验证；
6. 是否改变客户端要求、配置格式、存档数据、网络协议、规则默认值或权限；
7. 待人工确认项；没有则写“无”；
8. 是否发现 docs、源码、README、构建或许可证声明之间的不一致。
