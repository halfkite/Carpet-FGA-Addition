# 移除命令确认的点击兼容验收

## 实现与代码验证

本轮仅修复 Minecraft 26.3 的 `removeDialogWarning`，规则默认仍为 false；服务端已注册的 `/` 聊天按钮命令继续免确认，其他点击保留原事件，让客户端聊天插件和 Fabric 客户端命令继续处理；对话框的已注册服务端命令允许省略 `/`

涉及 `DialogWarning.java`、`DialogWarningClickEventMixin.java` 和 `DialogWarningActionMixin.java`，不修改服务器自定义回包执行权限；查询服务端根命令节点，不扫描世界，不持有命令树缓存

4项 JUnit 验证未知聊天/客户端指令、已注册的带斜线和对话框无斜线命令、完整根节点匹配与动态注册；Minecraft codec/Accessor 的实际注入通过独立探针检查，测试类不会进入生产 JAR

```powershell
.\gradlew.bat :26.3:build --no-daemon --configure-on-demand --max-workers=1 --offline
.\scripts\powershell\dialog-warning-routing-smoke-26.3.ps1 -Java25Home <Java25目录> -FgaJar <归档JAR>
git diff --check
```

冒烟在 `build/dialog-warning-routing-smoke-26.3-<时间>/` 创建独立测试服务器，不使用真实世界或玩家数据；使用已有 published-fga-jar-smoke init 脚本移除当前源码主输出，只加载指定归档 JAR；探针覆盖点击、静态对话框、动态模板、输入元数据、上下文清理和关闭规则后恢复，报告在 `scripts/logs/` 中

2026-10-03 结果：26.3完整构建成功，72项单测全部通过，其中4项为本轮新增；实际归档包42项编码冒烟通过，报告 `scripts/logs/dialog-warning-routing-smoke-26.3-20261003-113633-615/summary.txt`；`git diff --check` 和归档校验通过

归档包：`mod-builds/20261003-113623/carpet-fga-addition-1.6.0+v2610031134-mc26.3.jar`，SHA-256 `e3ee31b2af9107cfee0b9d29fcca31a71d5920cc3d4b252683cf9c3ffb875fbc`；没有自动替换正在运行的客户端或服务器 JAR

## 客户端验收

1. 在测试环境替换服务端 FGA 为新归档包；单人游戏需替换该实例中的 FGA 并重启客户端；保持原有 Org 和 MCDR 点击聊天兼容模组
2. `/carpet removeDialogWarning true`，重新生成消息后点击 `!!spbridge config backup on` 的开启按钮；应走原有聊天插件路径并正常反馈，不出现 Minecraft 未知命令；使用测试备份配置，避免修改生产备份策略
3. 重新产生 Org 查找结果，点击其高亮按钮；默认命令名为 `/highlight`，应在本地显示目标高亮，不出现服务端未知命令；若自定义高亮命令名，重复测试
4. 对无害服务端命令 `/seed` 生成测试按钮，例如 `/tellraw @s {"text":"执行 seed","click_event":{"action":"run_command","command":"/seed"}}`；应直接执行且不出现命令确认窗口；以 `/carpet` 规则按钮再验证一次
5. 检查原有命令建议/复制按钮仍正常；检查已注册服务端命令的静态对话框与动态文本/布尔输入对话框仍可执行，聊天及 Org 模板保留客户端原有替换
6. `/carpet removeDialogWarning false`，重新生成上述消息后再次测试，行为应与原来的客户端/原版处理一致

已发送到聊天历史的自定义点击事件不会被反向改写，测试必须重新生成消息

## 待人工确认与边界

真实客户端点击、Org 图形高亮和完整 MCDR 整合包未由自动探针验证；保留原事件也保留原客户端的确认策略，不强行绕过客户端专用流程

服务端无法判断一个已注册根命令是否同时被客户端同名命令覆盖；若 Org 自定义高亮命令名与服务端已有命令冲突，应使用独立的客户端命令名

其余版本尚未移植本修复；编译和序列化检查不等于游戏内验收
