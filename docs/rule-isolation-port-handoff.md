# 全版本规则隔离同步：测试交接

分支：`codex/rule-isolation-all-versions`。基于 `97edae3`，包含已 Squash 合并的 PR #15–#19。

本次把原仅在 1.21.1、26.2 启用的 Hopper、scoped 配置、ItemEntity、玩家背包回退修复同步到其余七个构建节点：1.21.3、1.21.4、1.21.5、1.21.8、1.21.10、1.21.11、26.1.2。客户端安装要求门控此前已覆盖九版本，本次保持不变。

## 实现与构建

- 修复预处理范围为 `MC >= 1.21.1 && MC <= 26.2`，对应当前 settings.json 九个节点；范围外旧代码保留。
- Hopper 内部 addItem 只调用一次；关闭主规则时传递原调用，开启时最多尝试一个 batch。
- scoped effective limit 统一遵守主规则和配置加载状态；原保存值不删除。
- ItemEntity 保留原容量表达式、合并数量参数与否决结果；默认距离传递原 AABB。存档 API 分支保持原样。
- 当前九节点均不注册旧 Inventory 方法覆盖，继承 Container 返回值钩子。
- 已检查九节点的 vanilla 字节码签名、相关内部调用及 Container 容量实现。静态检查不能替代运行时 Mixin 验证。
- 使用各版本支持的 JDK（1.21.x：21；26.x：25）执行 `gradlew.bat :<版本>:assemble --no-daemon --configure-on-demand --max-workers=2`，不会运行 test/check/Smoke 或启动游戏。
- 本轮按用户要求不运行测试。历史两版本测试结果不能作为其余七版本的通过证据。

## 下一模型的测试任务

1. 从此分支继续，先检查工作树；原 `D:/ai/carpet-fga` 含无关未提交改动，不要覆盖。
2. 阅读 scripts/tests 下 hopper/scoped/item/inventory-compat 的说明、Gradle init 脚本和 run-rule-compat.py。Runner 当前只允许 1.21.1、26.2，需逐版检查并适配 fixture 后扩大范围。
3. 每个节点执行 Hopper 单次搬运、规则关闭、synthetic wrapper 优先级；ItemEntity veto、AABB、容量参数；scoped 新配置、开启后关闭、真实进程重启；Inventory 普通和 >99 物品、第三方容器上限与特殊 Slot。
4. 复用假人堆叠 Smoke，另测真实 vanilla/FGA 客户端登录、重连、服务器重启以及规则开关；假人豁免不能代替真实客户端握手。
5. 指定 Org Addition 版本的兼容测试和“箱子 → 漏斗矿车”分别验证；后者不能认定为 ItemEntity 路径。超量物品保存与任意第三方 Inventory override 仍待集成验证。
6. 测试通过后创建 PR、审查并 Squash merge；本轮不创建 PR，以免自动触发 CI 游戏验证。

配置格式、存档格式、网络协议、默认值、权限均不改变。未修改依赖、映射、发布配置或许可证。原有预处理 JSON 诊断及编译警告需如实记录，不通过放松 Mixin require 掩盖。


## 本轮构建结果

九个节点均已 assemble 成功；本轮测试全部未运行。构建日志位于工作树 `.tmp/assemble-<版本>.log`，各产物目录内含 SHA-256 manifest。

| Minecraft | 构建 | 归档目录 |
|---|---|---|
| 1.21.1 | assemble 成功；未测试 | `mod-builds/20260908-130253` |
| 1.21.3 | assemble 成功；未测试 | `mod-builds/20260908-130449` |
| 1.21.4 | assemble 成功；未测试 | `mod-builds/20260908-130656` |
| 1.21.5 | assemble 成功；未测试 | `mod-builds/20260908-130839` |
| 1.21.8 | assemble 成功；未测试 | `mod-builds/20260908-131116` |
| 1.21.10 | assemble 成功；未测试 | `mod-builds/20260908-131401` |
| 1.21.11 | assemble 成功；未测试 | `mod-builds/20260908-131558` |
| 26.1.2 | assemble 成功；未测试 | `mod-builds/20260908-131645` |
| 26.2 | assemble 成功；未测试 | `mod-builds/20260908-131724` |
