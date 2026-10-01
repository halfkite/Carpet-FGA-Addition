# Minecraft 版本适配记录

## 模组版本号调整为 1.6.0（2026-10-01）

- 仅调整根 `gradle.properties` 的 `mod_version`，所有构建节点继承 1.6.0；保留历史产物版本号及此前验证记录
- 本次基线仍为 Minecraft 26.3，不改变任何功能源码、预处理条件、客户端要求、协议、配置、存档格式、规则默认值或权限；此前单版本功能不因版本号变化而同步到其他版本
- 验证：`git diff --check` 通过；`:26.3:build --no-daemon --configure-on-demand --max-workers=1` 主源码编译及 assemble 完成，但53项自动测试有1项失败，完整 build 未通过；失败项 `filenameIndexFindsSparseOverflowWithoutPrimaryOrIntermediateFiles` 仍期待升序，而此前实现已改为首位优先、后位倒序，属于既有源码/测试不一致，本次不修改业务或测试
- 打包：另执行 `:26.3:assemble --no-daemon --configure-on-demand --max-workers=1` 成功，归档 `mod-builds/20261001-144823/carpet-fga-addition-1.6.0+v2610011448-mc26.3.jar`；读取 JAR 内 `fabric.mod.json` 确认版本为 `1.6.0+v2610011448-mc26.3`，本地开发时间戳沿用现有构建约定，正式发布版本另由 Tag 传入
- 边界：此次没有启动客户端或服务端，其他版本本次不打包、不进行游戏测试；待人工确认项为既有升序测试与倒序实现的验收同步，及当前新增库存功能的游戏内回归；不推送、创建 Tag 或发布

## 客户端打印机补货及有界取货数量（2026-10-01，26.3）

- 仅 `MC == 26.3` 新增服务端可选 query_amount/take_amount 频道，0为半组、1–2304为指定数量；普通与静默共用身份/授权/限速/快照/防重放/库存锁。原 v1 频道及 DTO 构造器和 Java 入口描述符保持兼容。
- halfmasa 将轻松放置与打印机补货移入扩展功能栏，各自折叠允许假人取货、数量与静默子选项；旧 Ported 配置先迁移，再读 Extensions。打印机按现有 Hana 协调器方法或旧同系 InventoryUtils 方法描述符选择互斥接入，不依赖版本字符串；没有兼容方法时提示，未知改版需提供26.3 JAR再补适配。
- 本次构建使用 `:26.3:build -x test --console=plain --max-workers=1`。未运行自动测试或启动游戏；具体数量、连续取物、双功能同时触发及改版游戏内行为仍待人工确认，验收步骤见中英文 API 文档及客户端 `docs/litematica-auto-refill.md`。
- 最终构建两端均成功，`git diff --check` 通过。服务端归档 `mod-builds/20261001-141117/carpet-fga-addition-1.5.16+v2610011409-mc26.3.jar`；客户端归档 `D:/ai/half_masa/mod-builds/20261001-141117/halfmasa-fabric-26.3-1.1.8+20261001.140938.jar`。此前成功的中间构建均独立归档，客户端 `20261001-140348`、服务端 `20261001-135918`。

## 首位低库存从末位补货及空盒回收（2026-10-01，26.3）

- 源码门控：`MC == 26.3`；本次仅修改 Minecraft 26.3 服务端，halfmasa 客户端协议保持不变。其他版本未移植。
- 材料查询首位优先，后位按实际已有编号倒序，首位缺失或不足时直接查末位。自动维护已登记普通材料首位，散货不足1152时从后位提取材料补入9–35槽；普通64堆叠材料散货区容量1728。
- 取空盒返回名称精确为 `潜影盒` 的库存假人；保留组件及其他盒内材料，接收者不可用时保留并定期重试。维护不召唤假人，离线读取及压缩写入由有界IO线程处理，主线程复核并提交；共享锁防止分类/取物/手动召唤竞争，失败回滚，回滚不确定时隔离相关库存。
- 本次未运行自动测试或启动游戏。人工验收及限额详见 `docs/playersort_inventory_api.md`；待确认在线/离线、1151/1152边界、后位倒序与缺号、空盒回收及接收者背包满后重试、连续取货及总量守恒。异常断电跨档案原子性不承诺。
- 构建：`:26.3:build -x test --console=plain --max-workers=1` 成功，预处理依赖使用仓库内JDK21、26.3使用JDK25。每次成功构建独立归档；最终产物为 `mod-builds/20261001-125047/carpet-fga-addition-1.5.16+v2610011249-mc26.3.jar`，此前中间构建为 `mod-builds/20261001-124430`。本次无需更新客户端。

## 连续补货上线标记清理与静默取物（2026-10-01，26.3）

- 依据：客户端 11:35:07 首次云杉木栅栏取回32，假人正常下线；11:35:12 第二次请求后服务端没有上线记录，11:35:22 返回 SPAWN_TIMEOUT，随后 TARGET_BUSY。API 未清理分类管理器 PENDING_SORTER_SPAWNS 的1800 tick标记，导致重复召唤被当成已有请求；完成上线及关闭 API 假人时现清除此标记。
- 源码门控：`MC == 26.3`；实际适配仅 Minecraft 26.3，其他版本未移植。
- 新增可选 C2S `playersort/silent_take`；客户端 halfmasa 增加默认关闭的 `litematicaRefillSilent` 子选项，保持 stock/inventoryTake 权限、半组数量、请求编号防重放和背包同步。
- 离线静默库存由有界后台线程读取并生成临时文件；主线程复核身份、来源未上线、文件版本与背包容量后执行原子替换及在线背包交付，失败回滚并保留恢复反馈。不改玩家存档格式；原文件存为 dat_old；锁定期间阻止分类离线写入和 Carpet 假人召唤。已经在线的假人通过原在线事务取货，不关闭。
- 26.3 客户端及服务端 build（-x test）通过；本次未运行自动测试或启动游戏，最终归档及人工验收见本次交付。真实连续三次普通/静默取物、回退、并发和正常停服后库存守恒待确认；断电时两个档案的原子提交仍不承诺。
- 最终归档：服务端 `mod-builds/20261001-115423/carpet-fga-addition-1.5.16+v2610011151-mc26.3.jar`；客户端 `D:/ai/half_masa/mod-builds/20261001-115535/halfmasa-fabric-26.3-1.1.8+20261001.115429.jar`；两仓库 `git diff --check` 通过。

## 无前缀中文库存直接取货 API（2026-10-01，26.3）

- 源码门控：`MC == 26.3`；本次仅适配 Minecraft 26.3，其他版本未移植。
- 新增可选 C2S `playersort/direct_take`，只传版本、共享请求编号和物品 ID；服务端推导无前后缀中文假人名，校验完整快照后沿用既有半组转移事务。
- 直接请求复用取物权限、限速、请求结果缓存、离线身份校验和有界读取；客户端需支持新增频道，未安装 FGA 的客户端也可自行实现协议，旧客户端原查询流程继续可用。
- 客户端 halfmasa 优先直接取货，找不到有效且足量的来源后再查询分类库存；取货与投影放置冷却独立。
- 构建状态：`:26.3:build -x test --console=plain --max-workers=1` 成功（显式指定仓库内 JDK 21 供预处理节点使用）；客户端 halfmasa 26.3 同命令构建成功，`git diff --check` 通过。本次未运行自动测试或启动游戏，连续补货、回退、重复编号及物品守恒待游戏内验收。

## 材料 API 补查未登记与稀疏编号库存（26.3）

- 日志/档案依据：中文假人玻璃有1728个 minecraft:glass、身份标记匹配，但缓存没有 glass 映射；客户端请求普通玻璃返回库存不足。黑色染色玻璃与普通玻璃是不同物品，仍禁止互相替代；相关实例没有 debug.log
- 范围：修改仅 MC == 26.3，并使用原始源码中的 //$$ 隔离；1.21.1、1.21.3、1.21.4、1.21.5、1.21.8、1.21.10、1.21.11、26.1.2、26.2 不启用此修复
- 行为：复用分类实际命名配置，补查中文/英文/自定义名称、前后缀、盒装和杂盒库存；异步索引已有 UUID 文件名，检查实际存在的 _1–_999，不因首位或中间编号缺失漏查后位；不读取所有玩家背包
- 登记：仅在身份及完整库存快照通过后补入映射；不覆盖既有路线，正常停服按原缓存格式保存；有旧路线冲突时材料查询仍可使用当前生成名称，但不强行改写旧分类路线
- 校验：保留 UUID、白名单、真人/shadow 排除、占用/夺舍、权限、快照和重复请求保护；取物阶段复核材料查询对应的物品及来源候选，命名设置改变后不绕过校验
- 限额：每个材料计划最多64个名称族和4096个实际候选，目录最多65536个 .dat 文件名；单 IO 工作者、有界队列、每请求最多4份库存，查找计划30秒；nextCursor 为服务端计划内索引，客户端只需回传，不可自行计算
- 反馈：NOT_FOUND 表示没有候选库存；NOT_ENOUGH 表示已找到身份有效的库存但不足半组；不合格来源保留具体保护/身份错误；INVENTORY_INDEX_TOO_LARGE 表示目录超出索引上限
- 构建：`:26.3:build --no-daemon --configure-on-demand --max-workers=1` 成功，26.3 全部53项自动测试通过（本次新增3项），0失败/错误；其余九个节点仅随预处理依赖图编译主源码，不启用此修复，也不代表完成其游戏测试
- 产物：`mod-builds/20261001-091638/carpet-fga-addition-1.5.16+v2610010911-mc26.3.jar`，SHA-256 `29703a2a5ff347f4e1bc27caedc85b2fce1b2fbd5a4a027815a34d1153b90db2`
- 冒烟：归档包的隔离服务端完整回归通过，报告 `scripts/logs/fake-player-item-sort-smoke-26.3-20261001-091646/summary.txt`；请求者背包和随身潜影盒无玻璃，未登记中文玻璃档案1728→1696、请求者获得32；仅存在的自定义前后缀盒装 _17 库存33→1、请求者获得32，盒子名称保留；缺失返回 NOT_FOUND、不足半组返回 NOT_ENOUGH；来源登记及物品守恒通过，停服缓存文件确认保存盒装路线
- 回归边界：原分类、补给、设置、网页登录，以及 API 在线/离线半组补货、重复编号、背包满、快照改变、权限保护均继续通过，正常停机；本次未运行性能基准和正版档案查询专项，报告中对应 False 表示未运行；探针直接调用服务端 API，没有真实投影客户端收发，不记为客户端轻松放置验收通过
- 兼容：只需更新服务端；Payload 字段及协议版本不变，仅增加错误状态和材料 cursor 内部语义；客户端应只使用返回的 nextCursor，并处理 NOT_FOUND/索引超限；不修改规则默认值、权限、配置格式或玩家存档格式
- 待人工确认：真实投影客户端轻松放置及新错误提示；不会自动替换生产服 jar 或操作真实库存；其他版本等待确认
- 已知不一致：此前分类副手存盘格式、基础节点门控和 Mixin 警告仍见后续记录，未扩大此次修改范围

## 库存导出成功反馈发送失败修复（26.3）

- 日志依据：26.3 服务端 latest.log 的 stock_exported 系统消息编码失败，参数含 Java Path；同一时间客户端显示 multiplayer.message_not_delivered，导出的 TXT 文件实际存在；相关实例未找到 debug.log
- 修改：仅 MC == 26.3 的 stockExport 将输出路径转换为字符串，保留翻译键、服务端语言 fallback 和绿色反馈；其他节点保留原行为，尚未同步 1.21.1、1.21.3、1.21.4、1.21.5、1.21.8、1.21.10、1.21.11、26.1.2、26.2
- 测试：新增 StockExportFeedbackTest，通过原版 ClientboundSystemChatPacket Codec 复现 Path 参数失败，并验证中文、英文、繁体中文反馈编码/解码，包含中文目录、空格、百分号和相对路径；隔离冒烟增加真实导出命令和 TXT 文件检查
- 兼容：只需更新服务端，客户端安装要求、协议格式、权限、规则默认值、配置格式和库存存档均不变；不改变 API 身份校验
- 构建：`:26.3:build --no-daemon --configure-on-demand --max-workers=1` 成功，26.3 全部 50 项自动测试通过（本次新增 2 项），0 失败/错误/跳过；原版系统聊天包的旧参数失败复现和新反馈编码/解码通过。其余九个节点仅随依赖图编译主源码，不代表已启用此修复或完成游戏测试
- 产物：`mod-builds/20260930-215345/carpet-fga-addition-1.5.16+v2609302151-mc26.3.jar`，SHA-256 `f5c7b21ddd257c4ccf5f8c4c89cbb84750dbfebf014c26aa4b83a94c89ae2b29`
- 冒烟：归档包的隔离服务端完整回归通过，报告 `scripts/logs/fake-player-item-sort-smoke-26.3-20260930-215415/summary.txt`；实际执行 `/fga playersort stock list all`，成功提示写入日志并生成一个非空 TXT 文件；同次回归中分类、半组补货 API、权限/设置和网页登录均通过，正常停机。原版消息 Codec 测试覆盖发包编码；隔离服没有真实客户端，不将冒烟通过记作真人聊天栏已验证
- 待人工确认：更换服务端 jar 并重启后，以 OP 玩家执行 `/fga playersort stock list all`，应显示导出成功路径、TXT 文件正常产生、服务端不再出现该反馈的 EncoderException。分别使用客户端安装/未安装 FGA 的情况检查语言；客户端实际显示尚未验证。其他版本等待本版本确认后同步
- 不一致：本次日志中的参数类型错误已对应修复；下节记录的旧分类副手写入及既有门控问题不属于此次反馈修复，仍保留记录

## 假人库存查询与轻松放置补货 API v1（26.3）

- 范围：新增 Payload、API、假人身份保存 Mixin 和测试均为 `MC == 26.3`；原始 1.21.1 源码中的新增行使用 `//$$`，不会启用新协议；尚未适配 1.21.1、1.21.3、1.21.4、1.21.5、1.21.8、1.21.10、1.21.11、26.1.2、26.2
- 行为：仅客户端主动查询回包；可分页查看分类路线/库存，或按物品 ID 查找补货来源；取物数量由服务端按实际堆叠上限计算为半组，64→32、16→8、不堆叠物品→1；可以从潜影盒内提取散货，空盒留在原槽位，同组件物品才合并
- 权限：查询沿用 stock；新增 inventoryTake 权限默认 OP，可通过 `/fga playersort permission inventoryTake <玩家名|0-4|ops> true|false` 单独授权；普通客户端的轻松放置补货需要同时授予 stock 和 inventoryTake；不新增 Carpet 规则或第 12 项设置
- 安全：初版只操作缓存分类路线及 _1–_999 后位；当前26.3材料查询的命名候选补查见顶部记录，指定名称查询仍仅允许登记路线；均校验确定性离线 UUID、白名单、在线实体为假人、非夺舍/分类中的来源，以及离线假人身份标记；不接收客户端 ItemStack、NBT、数量或接收者；30 秒快照、请求编号去重、全量快照复核、背包容量预检，失败不扣货
- 端侧和网络：服务端必须安装此构建，客户端需实现 API v1 的三个游戏 CustomPayload 频道；不要求安装 FGA 客户端，不主动向未实现 API 的客户端发包，不修改原版数据包；网页 HTTP 接口仍只读，网页关闭不影响主动游戏 API
- 性能：每玩家查询/取物分别最多每秒一次，全局查询最多 10 次/秒、取物最多 4 次/秒；最多 64 会话、8 在途取物，一个有界 IO 线程读取离线 NBT；每次材料查找最多 4 个档案，可继续分页；存盘通过原版假人生命周期，不在 API Tick 内直接读写 playerdata
- 存档兼容：复用 fgaOfflineSorterName 标记，并在原版保存 Carpet 假人时保留；不标记真人，不自动改写旧档案。没有标记的旧假人档案须先备份并检查旧副手槽位、载具和身份，确认可安全上线后才由管理员上线并正常下线一次，再使用 API。两端背包转移沿用原版存盘/备份语义，不承诺断电时两个 playerdata 文件的原子提交；生产测试前应备份存档
- 26.3 副手：API 按原版 equipment 读取副手，盔甲不计库存；旧 Inventory 副手槽位返回 LEGACY_OFFHAND，在上线前拒绝操作，须先备份并人工检查迁移，不能盲目用上线/下线修复
- 临时上线保护：API 自行召唤的库存假人使用旁观模式，避免环境伤害及拾取地面物品；拒绝自动上线含 RootVehicle 的离线档案，不搬移载具；原本在线来源的模式及位置不改变
- 真人 shadow：排除 Carpet isAShadow 替身，不标记其存盘身份、不将其视作分类库存、不由 API 清理逻辑下线
- 接口文档：`docs/playersort_inventory_api.md`、`docs/playersort_inventory_api_en.md`
- 构建：`:26.3:build --no-daemon --configure-on-demand --max-workers=1` 成功，使用本机已有 JDK 21 工具链；26.3 全部 48 项自动测试通过，其中 API 单元测试 12 项。实际打包仅 26.3；其余九个节点仅随预处理依赖图编译主源码，未启用该 API、未进行此功能游戏测试
- 验证：最终归档包的隔离服务端完整回归通过，报告 `scripts/logs/fake-player-item-sort-smoke-26.3-20260930-195254/summary.txt`。API 在线/离线半组补货（32/8）、潜影盒散货和物品守恒、重复编号/参数更改、背包满、物品变更快照、权限/关闭、路线列表及库存分页通过；离线取物保留副手 31 个泥土，shadow 标志来源被拒绝。副手解码、旧副手槽位和 RootVehicle 拒绝另有单元测试；shadow 用例为探针设置假人标志，不等同于真人客户端替身测试。原分类、补给、设置、网页回归继续通过，服务端正常停机；本次未运行性能基准及正版档案查询专项，报告中对应 False 表示未运行
- 最终产物：`mod-builds/20260930-195254/carpet-fga-addition-1.5.16+v2609301946-mc26.3.jar`，SHA-256 `c03f791e0c38641f847a30ff2a0d9aded66df2627985e013ff6d1109680dac4a`
- 日志边界：启动时存在本机 Windows Perflib/OSHI 计数器错误，测试来源名查询有 Mojang 404；未将这些日志记为 API 故障，也未据冒烟通过宣称日志完全无异常。其他节点既有 Mixin 警告见下节
- 已发现的既有不一致：分类 OfflineInventory 的读写仍使用旧 Inventory 副手槽位，而 26.3 原版在 equipment 保存副手；本次 API 不复用该读写路径，只安全读取并经原版在线背包转移。未授权扩大为整个分类存盘格式迁移，旧档案含副手时必须先备份检查
- 待人工确认：实际投影客户端的网络对接与轻松放置补货体验（服务端探针直接调用 API，Codec 有单元测试，未运行真实客户端收发）；断电/强杀恢复及旧档案人工迁移未验证；其他版本等待单版本确认后移植

## 假人分类设置收拢到 set（26.3）

- 范围：`MC == 26.3`；11 项设置统一放在 `/fga playersort set` 下，保留 `/fga playersort language` 语言快捷入口；其他版本的旧 setting 和外层设置入口保持原行为
- 门控：`usesGroupedSettings()` 在原始源码及其他预处理节点返回 false，仅 26.3 预处理结果返回 true；1.21.1 根节点直接编译原始源码，所以不能仅用正常 Java 外层的 `//#if MC == 26.3` 注释隔离本次命令布局。设置按钮和帮助路径同样按该门控切换
- 指令：set 下为 language、mode、summonNotices、prefix、quickShulker、autoCraft、whitelistMode、cleanOpenedTarget、speed、cpu、dashboard；网页密码/端口、旧 format/workers 设置同样收拢；名单管理、名称映射、库存查询与权限管理仍在外层；set 不带参数显示设置面板
- 同步：设置向导的点击补全、帮助及中英文文档改用新路径；26.3 不再注册外层 setting 或上述模式设置，language 同时支持两个路径；旧 `/fakePlayerItemSort` 别名采用同一命令树
- 兼容：不更改配置字段、规则默认值、物品数据、客户端要求或网络协议；沿用原有逐功能权限键，高级内部设置别名 targetLanguage、shulkerRestock、cpuThreads、inventoryRebuild 仍可在 set 下输入
- 构建：`:26.3:build :1.21.1:test --tests carpet.fga.FakePlayerItemSortCommandLocalizationTest --no-daemon --configure-on-demand --max-workers=1` 成功；26.3 全部 36 项自动测试通过，1.21.1 命令回归 6 项通过。实际打包版本仅 26.3；1.21.3、1.21.4、1.21.5、1.21.8、1.21.10、1.21.11、26.1.2、26.2 的主源码也随依赖图编译成功，未对这些版本启用新布局或进行游戏测试
- 验证：补齐基础节点门控后，使用 `scripts/powershell/fake-player-item-sort-smoke-26.3.ps1 -BaselineJar <最终产物>` 重跑隔离服务端全套冒烟通过，报告 `scripts/logs/fake-player-item-sort-smoke-26.3-20260930-164526/summary.txt`；外层 Tab 不显示设置项、set 包含 11 项、两个 language 路径、全部设置执行、旧 settings 授权/撤销，以及分类/网页登录回归均通过。quickopen/summon、杂盒/满盒/拆盒、散货分批、补给生命周期、盒装补给材料、失败重试节流和网页四端点认证/关闭均通过，正常停机；本次未重复性能基准或正版档案查询专项测试
- 最终产物：`mod-builds/20260930-164512/carpet-fga-addition-1.5.16+v2609301640-mc26.3.jar`，SHA-256 `79697e1ab937a7a11f65299814c7194020f38f16e041a28f64b5503fd57b9841`
- 编译警告：1.21.10/1.21.11 的既有末地传送门 Mixin descriptor 有目标未找到警告；与本次命令布局无关，未修改或掩盖，不能把这些节点的编译通过当作 Mixin 游戏验证通过
- 已发现的既有不一致：1.21.1 原始源码仍含先前用正常 Java 写在 `MC == 26.3` 注释块内的设置向导、权限和网页登录分支，与前序记录的“仅 26.3”范围不一致。本次仅隔离 set 布局与路径变化，不更改那些既有功能；其实际门控需另行核对，不能据此次构建声称它们已经完成其他版本适配或游戏验证
- 待人工确认：游戏内点击填入聊天栏的视觉交互

## 假人分类网页三态设置与登录（26.3）

- 范围：新增网页登录与指令为 `MC == 26.3`，分类向导新增第 11 项 `[false][true][需要登录]`，内部选项 `false|true|login`
- 行为：默认 false，关闭 HTTP 服务并停止自动网页快照更新；true 无需登录；login 使用用户名 fga 与单独设置的密码保护页面、库存 API 和文本导出，未设置密码时拒绝进入 login
- 配置：保留旧布尔 dashboard 字段，新增 dashboardLogin、dashboardPasswordHash；旧配置仍可读取，新密码只保存随机盐 PBKDF2 哈希，写入失败回滚；关闭网页不影响游戏内主动库存查询
- 端侧：无需客户端 FGA，仍仅监听 127.0.0.1；登录模式下原 API 调用者也需发送认证信息，未更改 Minecraft 网络协议、存档物品、规则默认值或命令权限
- 验证：`:26.3:build --no-daemon --configure-on-demand --max-workers=1` 成功，36 项自动测试通过；`scripts/powershell/fake-player-item-sort-smoke-26.3.ps1` 隔离服务端全套冒烟通过，报告 `scripts/logs/fake-player-item-sort-smoke-26.3-20260930-084105/summary.txt`。页面、旧/新库存 API、stock.txt 四个端点均验证匿名和错误密码返回 401、正确密码返回 200；配置重载后认证有效；切回 true 后匿名可访问；false 后监听停止；原分类/补给/物品守恒用例继续通过
- 实际适配和构建版本：26.3；其余节点仅随预处理依赖编译主源码，未增加登录功能或进行该功能游戏验证
- 最终产物：`mod-builds/20260930-084713/carpet-fga-addition-1.5.16+v2609300845-mc26.3.jar`，SHA-256 `5ab2c2465046d4280a480a8ea68565dc1b18954123a4facddbc7c8224ad9859d`
- 待人工确认：浏览器自带登录框交互

## 补给站按盒装材料取料（全部含 `FakePlayerItemSortManager` 的版本）

- 现象：玩家的 `潜影盒补货` 背包里全是装满原木、潜影盒壳的潜影盒，分类却一直取不到空盒；日志表现为每次搬运尝试都新召唤一个编号后位假人而不分类
- 根因：取料/计数只看背包顶层槽位。`countDepotMaterial` / `consumeDepotMaterials` / `transferMaterialsToDepot` 都只匹配顶层 `isLog(stack)` / `stack.is(Items.SHULKER_SHELL)`，盒内的原木与壳完全看不见；`isDepotAllowed` 还把这类材料盒判为"异物"，会尝试把它们清出去。另外 `restockDepot` 需要一个空槽放合成产物，背包 36 格全被材料盒占满时 `wanted=0` 直接返回
- 修改：新增 `isDepotMaterialBox`（盒内只装原木/壳的潜影盒视为补给站材料，不再算异物）、`countShulkerMaterial`、`takeShulkerMaterial`；`countDepotMaterial` 与 `consumeDepotMaterials` 改为同时统计并消耗盒内材料，被取空的盒子留在原处直接成为可用的空潜影盒；新增 `craftDepotBox`，在 `takeEmptyShulkerFromDepot` 找不到现成空盒时按需消耗 2 原木 + 2 潜影盒壳直接产出空盒（不需要空槽），并同样受 `shulkerRestock` 开关约束
- 行为：材料盒装、无散料、无空槽的真实布局下也能持续产出空盒分类；`shulkerRestock=false` 时不合成，与原有语义一致
- 数据兼容：不新增配置字段、网络协议或客户端要求，不修改规则/权限默认值；材料盒留在补给站内，玩家原有存放方式不变
- 源码预处理条件：无版本条件，随 `FakePlayerItemSortManager` 一起编译到所有版本
- 编译：`:26.3:build` 成功
- 验证：新增 `fgaSortProbe depot-boxed`——把补给假人 36 个主槽全部填成"18 盒原木 + 18 盒潜影盒壳"（无散料、无空槽），连续取两次空盒，断言拿到 2 个潜影盒且恰好消耗 4 原木 + 4 潜影盒壳
  - 修复后：`FGA_SORT_PROBE_PASS: depot-boxed crafted=2 logsUsed=4 shellsUsed=4`
  - 修复前（同一探针 + 上一版 jar `mod-builds/20260929-222036`）：`FGA_SORT_PROBE_FAIL: boxed depot material produced no box: 0 minecraft:air`
  - 全套冒烟 14 项通过，报告 `scripts/logs/fake-player-item-sort-smoke-26.3-20260929-232748/summary.txt`
- 顺带修复：异步召唤期间不再给补给假人打"上线即关闭"标记（`finishDepotUse` 的离线分支改为在 `PENDING_SORTER_SPAWNS` 命中时跳过），避免召唤还在途中就被判定为待关闭；`closeDepotFake` 会清掉该名字的召唤重试标记，使下一轮能立即重新召唤
- 产物：`mod-builds/20260929-233411/carpet-fga-addition-1.5.16+v2609292333-mc26.3.jar`，SHA-256 `29d8a20ace88926333c09ed9e6658a782ce60a78b5d8b708cbc907a4eaacb57b`
- 待人工确认：真实存档里跑一轮杂盒，确认补给站在"背包全是材料盒"时能持续给出空盒；以及盒内材料被取空后的空盒是否符合你的存放预期

## 空潜影盒补给假人的生命周期改为按轮（全部含 `FakePlayerItemSortManager` 的版本）

- 现象：分类杂盒时补给假人 `潜影盒补货` 上线后同一秒被关闭（`lost connection: Killed`），没有合成任何空盒；随后分类器每次搬运尝试都新召唤一个编号后位假人（日志中 `村民刷怪蛋_1` … `_13` 每约 4~5 秒一个），全程零分类
- 根因：`restockDepot` 的 `finally { if (spawnedForRestock) finishDepotUse(...) }` 在**每次取盒/补货尝试后**就判定补给假人去留，缺材料（2 原木 + 2 潜影盒壳）时立刻把它关掉；而材料要等本轮分类把原木、潜影盒壳分到各自目标后才可能出现，于是补给永远来不及合成，后位又因取不到空盒而不断新召唤
- 修改：补给假人生命周期挂到"本轮分类"。`finishDepotUse` 在仍有分类任务时不下线（新增 `sortingRoundActive()`：JOBS 中存在非 `depotCleanup` 任务），只保留"背包有异物则先排队清理"；本轮结束（无分类任务）或规则关闭时才下线。tick 循环里补给假人自己的清理任务结束不再直接关闭，统一交给 `finishDepotUse` 判定；新增 `isAutoSpawnedDepot` 保证玩家手动召唤的同名假人不会被自动下线
- 行为：一次缺材料的补货失败不再关闭补给假人，它留在线上，等原木、潜影盒壳在后续分类中到位后再合成；补货失败重试间隔仍为 30 秒，通知仍有 10 秒节流
- 数据兼容：不新增配置字段、网络协议或客户端要求，不修改规则/权限默认值；补给假人在线期间其背包走在线读写，物品仍持久化到 playerdata
- 源码预处理条件：无版本条件，随 `FakePlayerItemSortManager` 一起编译到所有版本
- 编译：`:26.3:compileJava`、`:1.21.1:compileJava` 成功
- 验证：`scripts/powershell/fake-player-item-sort-smoke-26.3.ps1` 新增 `fgaSortProbe depot-round` 用例——分类任务进行中调用一次补货，断言补给假人保持在线（`FGA_SORT_PROBE_PASS: depot-round online-while-sorting`），随后停止本轮，断言它下线（`FGA_SORT_PROBE_PASS: depot-round closed-after-round`）；原 `depot-restock` / `depot-check` 用例继续通过。隔离服实测：22:18:51 补给假人上线，22:18:53 仍在线的断言通过（修复前是上线同一秒被关闭），停止本轮后才 `lost connection: Killed`。全套冒烟 13 项通过，报告 `scripts/logs/fake-player-item-sort-smoke-26.3-20260929-221537/summary.txt`
- 产物：`mod-builds/20260929-222036/carpet-fga-addition-1.5.16+v2609292219-mc26.3.jar`，SHA-256 `87a33bcec073a76d018825be6db539edd159736d0e586472710111ddec1e8d51`
- 仍未修（用户要求暂缓）：一次搬运尝试取不到空潜影盒材料时，后位循环仍会为下一个编号召唤新的目标假人；本轮修复让补给假人能留在线上并合成，但若原木与潜影盒壳始终不到位，仍会出现"一直召唤新假人、不分类"的现象，需要在后位循环里补防呆
- 待人工确认：真实服务器重跑一次杂盒拆解，确认补给假人本轮内不再反复上下线、缺材料时能在原木与潜影盒壳到位后自动合成空盒；连续分类模式下"只要任务在跑就保持在线"的边界是否符合预期

## 分类目标假人的同步档案查询卡顿修复（全部含 `FakePlayerItemSortManager` 的版本）

- 现象：正版验证（`online-mode=true`）服务器上，summon 模式每创建一个新的分类目标假人都会让主线程同步请求 Mojang 档案服务，单次卡 3–12 秒；`logs/latest.log` 表现为 `Could not lookup user whitelist entry for <目标名>` 紧跟 `Can't keep up! Is the server overloaded? Running 3035ms or 60 ticks behind`，速度设 40/10 都一样
- 根因：`FakePlayerItemSortManager.createFake` 直接调用 carpet 的 `EntityPlayerMPFake.createFake`，而它的第一步 `OldUsersConverter.convertMobOwnerIfNecessary` 在档案缓存未命中、名字不超过 16 字符且 `usesAuthentication()` 为 true 时会在服务端线程同步查询档案服务。分类目标名来自物品本地化名（`targetLanguage=chinese`、`prefix=""` 时为 `红石火把`、`红石火把_1`、`潜影盒` 等），Mojang 只接受 `[A-Za-z0-9_]{3,16}`，必然返回 400 `Invalid profile name`；失败结果没有负缓存，每个新物品种类、每个编号后位都会再发一次请求
- 修改：新增 `FakePlayerItemSortManager.seedSorterTargetProfile`，在调用 carpet `createFake` 之前把目标名以确定性离线档案写入服务端档案缓存（`MC >= 1.21.10` 用 `services().nameToIdCache().add(NameAndId.createOffline(name))`，其余版本用 `getProfileCache().add(new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name))`），使那次查询直接命中缓存。实现只写缓存、不调用任何可能阻塞的缓存 `get`
- 安全边界：`CarpetSettings.allowSpawningOfflinePlayers` 为 false 时不写入；每个名字每服务器会话只写一次；usercache 中 UUID 与离线 UUID 不同的名字（真实账号）保持原版解析，不会被钉成离线身份
- 数据兼容：分类目标改为稳定的离线 UUID，与 quickopen 模式 `OfflineInventory` 的解析一致，并会随 `usercache.json` 持久化；不新增配置字段、网络协议、客户端要求，不修改规则/权限默认值
- 源码预处理条件：方法体为 `//#if MC >= 1.21.10`（新档案缓存 API）/ `//#else`（`GameProfileCache`）；调用点不带版本条件，覆盖所有编译 `FakePlayerItemSortManager` 的版本
- 编译：`:26.3:compileJava`、`:1.21.1:compileJava` 成功；`:1.21.8`、`:1.21.10`、`:26.2` 节点随预处理依赖图一并编译成功。逐版本检查预处理产物：1.21.1/1.21.8 命中 `getProfileCache().add(new GameProfile(...))`，1.21.10/26.2/26.3 命中 `services().nameToIdCache().add(NameAndId.createOffline(name))`。`:26.3:test` 35 项通过、0 失败
- 服务端验证（`online-mode=true`）：新增 `scripts/powershell/fake-player-item-sort-smoke-26.3.ps1 -ProfileCheck -OnlineMode`，在隔离 26.3 服务端直接调用 `OldUsersConverter.convertMobOwnerIfNecessary`。未预置时日志出现 `Could not lookup user whitelist entry for fgaProbeProfile`，服务端线程阻塞 `blockedMs=1309.638`；调用预置方法后同一查询 `lookupMs=0.025` 并返回离线 UUID `22950646-3f7f-3afe-af93-f9ebdbdb0484`，不再产生查询告警
- 回归：`scripts/powershell/fake-player-item-sort-smoke-26.3.ps1` 全套 12 项通过（help、settings、quickopen、summon、混合盒、满盒、拆盒、散货分批、补货假人清理、Dashboard 页面/API/库存文本、Dashboard 停止、正常停机），报告 `scripts/logs/fake-player-item-sort-smoke-26.3-20260929-214505/summary.txt`
- 顺带修正验证脚本缺陷：该脚本被工具写回时丢失 UTF-8 BOM，而本机 Windows PowerShell 5.1 默认按 GBK 解析脚本与 `text/plain` 响应体，导致 Dashboard 库存文本断言把正确响应判为失败（实测响应字节 `231,137,169,229,147,129,9,...` 正是正确的 `物品\t数量\t`）。现改为显式 UTF-8 解码响应体并用码点构造期望表头，不再依赖脚本自身编码
- 产物：`mod-builds/20260929-215227/carpet-fga-addition-1.5.16+v2609292151-mc26.3.jar`，SHA-256 `edd51090817777f1831744d1c3b4a21b3bc1dab90c853444712dea4e8211f821`；其余版本未打包
- 已知不一致（本分支未修，待人工确认）：`fakePlayerProfilePreload` 文档标注 `1.21+` 生效，但 `EntityPlayerMPFakeMixin` 只在 `carpet-fga-addition.mixins.json` 的 `MC == 1.21.1` 分支（以及 1.20.1 的注释分支）注册；26.3 与 1.21.3–26.2 上直接调用 `EntityPlayerMPFake.createFake` 不会被预加载拦截，`PlayerCommandMixin` 的指令预加载仍然有效。分类器已不再依赖该规则
- 待人工确认：真实正版验证服务器上重跑一次大量杂盒拆解，确认不再出现 `Could not lookup user whitelist entry` 与秒级 tick 欠账；以及旧存档中此前以在线 UUID 保存的分类库存是否需要迁移

## 假人分类拆盒主线程峰值优化（26.3）

- 范围：`supportsPlayersortRefactor()` / `MC == 26.3`；其他版本保持原行为
- 修改：在途计划持有 Job 至主线程提交完成，丢弃停止/重启后的旧计划；失败搬运也按 speed 间隔退避；组件直接比较代替主线程构造整盒字符串；拆盒合并同类槽位、单次上限 64 个物品；移除重复首位库存整理；空盒假人等源库存中的待拆盒处理完后再下线；新建空档案目标无法接收物品时结束本次后位尝试，避免缺盒材料时继续扫描其余不存在的后位档案
- 数据兼容：26.3 改用 `LevelResource.PLAYER_DATA_DIR` 对应的 `world/players/data`；遇到旧错误目录 `world/playerdata` 的同 UUID 档案时拒绝写入，保留双方数据等待人工恢复。不增加客户端要求、网络协议或配置字段，不修改规则/权限默认值
- 构建：`:26.3:build --no-daemon --configure-on-demand --max-workers=1` 成功，35 项单元测试通过；本次行为仅适配 26.3，未移植其他版本
- 完整服务端冒烟：`scripts/powershell/fake-player-item-sort-smoke-26.3.ps1` 通过，报告 `scripts/logs/fake-player-item-sort-smoke-26.3-20260930-075253/summary.txt`；覆盖命令设置、quickopen/summon、混合盒/满盒/拆盒物品守恒、散货分批、补货假人清理/按轮生命周期/盒装材料，以及 Dashboard 页面/API/停止和正常停服。缺材料用例源库存 64、目标 1728 保持不变，三次失败重试间隔均不小于 40 tick
- 性能专项：`-PerformanceOnly` 报告 `scripts/logs/fake-player-item-sort-smoke-26.3-20260929-192317/summary.txt` 通过；8 个 27 格杂盒的 864 个内容物和 8 个盒子守恒，覆盖速度 40/10、召唤/档案模式；密集同类盒 1728 个物品每 tick 搬运不超过 64；quickopen 写入后实际召唤目标验证 448 个绿宝石仍在。空盒收集假人上线次数由对照版的每场景 8 次降为 1 次
- 缺材料成本对照：旧版 `v2609291813`（`-PerformanceOnly -BaselineJar`，报告 `scripts/logs/fake-player-item-sort-smoke-26.3-20260930-075608/summary.txt`）三次尝试平均 38.106 ms、最大 45.617 ms；当前版同一用例平均 17.869 ms、最大 25.491 ms，均转移 0 个物品。这是小样本专项测量，不是整服 MSPT；普通杂盒场景尚未证明平均 MSPT 明显下降，首次加载仍可能有尖峰
- 产物：`mod-builds/20260930-075247/carpet-fga-addition-1.5.16+v2609300751-mc26.3.jar`，SHA-256 `f996bda68314bdb3df517dcecbe440c109ced1a2b846b4f9c6f52ed73845c096`
- 待人工确认：真实存档大批杂盒的 MSPT/吞吐量、旧错误目录档案的人工核对与恢复；未进行客户端界面冒烟。本次不自动迁移或覆盖生产档案
- 已发现旧验证缺陷：原冒烟只从旧 playerdata 目录核验，无法证明 26.3 原版登录后数据一致；本次改用原版目录核验。旧记录中的通过结果仅代表当时脚本检查范围

## 假人物品分类全面重构（26.3 单版本基线）

- 功能：保留旧入口与原有 Dashboard/API、路由缓存、分类模式、白名单、持续分类、自动补货、目标清理、名称配置、库存重构和线程调优；26.3 首次设置向导现共 11 项（含网页三态设置），新增分类假人上下线公屏提示开关，关闭时控制台仍记录、玩家公屏不广播；自动补货假人完成补货/清理后下线，即使档案预加载延迟也会在上线后清理。速度设置接受 1–120 tick，除控制潜影盒拆分间隔外，散货也按速度分批移动；核心选项为 `0|1|2|custom`，自定义线程数上限 256 并受可用核心数限制
- 源码预处理条件：向导、独立命令、通知 Mixin、权限与 stock 命令为 `MC == 26.3`；`FakePlayerItemSortManager.supportsPlayersortRefactor()` 仅在 `MC == 26.3` 返回 true，其他源码输出走旧路由实现；26.3 配置写入 `schemaVersion: 3`、现有 11 个向导完成字段和逐命令权限
- 实际适配版本：`26.3`。本轮没有将新行为移植到其他 Minecraft 版本
- 编译/自动测试：`:26.3:test` 与 `:26.3:build` 成功；自动测试报告 35 项通过。Gradle 的 26.3 预处理依赖图还编译了当前 10 个版本节点的主源码，但其他版本没有运行本次测试或游戏内验证；1.21.10/1.21.11 编译有现存 End Gateway/Portal Mixin 目标方法警告，未导致失败
- 服务端冒烟：最新 `scripts/powershell/fake-player-item-sort-smoke-26.3.ps1` 在隔离目录验证 `/fga playersort help`、`speed 6`、`cpu custom 3`、通知开关 true/false、quickopen/summon 各转移 16 个物品、混合盒（石头 32、泥土 32）、满盒（石头 1728）、拆盒（钻石 32、泥土 32）守恒；散货 64 个绿宝石按 speed 16 每 16 tick 最多 4 个完整转移；补货假人上线后下线且清理跟踪状态归零。上述项目通过，测试服正常停机。但同次脚本后续 Dashboard 回环 HTTP 检查超时，故本轮不能报告 Dashboard 页面/API 检查通过；报告 `scripts/logs/fake-player-item-sort-smoke-26.3-20260929-183038/summary.txt`
- 产物：`mod-builds/20260929-181436/`，包含 `carpet-fga-addition-1.5.16+v2609291813-mc26.3.jar`；SHA-256 与构建命令见同目录 `build-manifest.json`
- 客户端/服务端要求：只需服务端安装 FGA；新增服务端 Mixin，不增加客户端代码、自定义网络协议或客户端安装要求。配置 schema 3 在既有 schema 2 数据上补入默认关闭的 `summonNotices`；配置继续位于世界 `config/carpetfgaaddition/`。`stock list all` 会在世界配置目录写入库存文本导出
- 待人工冒烟：真实客户端确认通知开关关闭时玩家公屏无上下线提示、服务端控制台仍有记录；开启后公屏提示恢复；全新存档设置向导点击/颜色、无 FGA 客户端兼容、旧 schema 配置迁移、权限等级与玩家覆盖、两种分类模式下的其他潜影盒组合、白名单、补货材料场景、库存重构确认/取消。Dashboard/API 本轮检查超时，也待单独确认。详细步骤见 `FeatureSmokeTestPlanTest` 中 `fakePlayerItemSort26_3...` 流程

## 硫方怪小型成长为中型的时间（26.2、26.3）

- 规则：`sulfurCubeGrowthTime`，默认 `-1`；`-1` 保持原版 20 分钟（24000 游戏刻），正整数设置成长时间秒数，允许范围为 `1–107374182`。只在新小型硫方怪初始化幼年年龄时应用；改规则不会重置已存在幼体的存档年龄计时，喂食加速仍由原版处理
- 源码预处理条件：`MC >= 26.2 && MC <= 26.3`；规则、计时换算类、Mixin 和测试类使用 `//$$` 分支，保证原始根节点 1.21.1 不会编译或注册此功能
- 实际适配版本：`26.2`、`26.3`；更早构建节点不启用该规则或 Mixin
- 自动验证：`:26.2:build` 与 `:26.3:build` 均成功；每个版本 33 项自动测试全部通过。Preprocessor 依赖链也完成了当前 10 个节点的 `compileJava`；1.21.10/1.21.11 编译期间出现现有 End Gateway/Portal Mixin 目标方法警告，未导致失败
- 资源检查：26.2 和 26.3 的生成 Mixin 配置均注册 `SulfurCubeGrowthMixin`；1.21.x 与 26.1.2 不注册
- 产物：`mod-builds/20260929-110737/`，包含 `carpet-fga-addition-1.5.16+v2609291104-mc26.2.jar` 与 `carpet-fga-addition-1.5.16+v2609291104-mc26.3.jar`；SHA-256 与构建命令见同目录 `build-manifest.json`
- 客户端/服务端要求：服务端规则；不新增客户端依赖、网络协议、配置格式或存档字段，沿用原版 `Age` 保存年龄
- 游戏内验证：尚未启动客户端或服务端；实际成长时长、分裂个体、喂养加速和重进存档流程待人工冒烟，步骤见 `FeatureSmokeTestPlanTest` 中 `sulfurCubeGrowthTime`

## 假人物品分类命令补全、语言反馈与 26.3 扩展命令

- 行为：`/fakePlayerItemSort setting <key> <value>` 的值建议现在根据已输入的 `key` 过滤；反馈、帮助、确认按钮、分页标题及分类运行通知使用翻译组件。安装 FGA 的客户端由客户端语言解析；无 FGA 客户端使用当前 `CarpetSettings.language` 作为 fallback。帮助里的命令可点击后仅补入聊天栏
- 命令移植：原仅在 `1.21.1` 注册的 `workers`、`dashboard`、`bot_sort restart` 和扩展设置键现也在 `26.3` 注册；其他节点仍待后续同步。`cleanOpenedTarget` 只建议 `false|true`；分类设置的完整可选值以 `docs/commands.md` 为准
- 源码范围：`FakePlayerItemSortCommand` 命令门控为 `MC == 1.21.1 || MC == 26.3`；按键过滤、文本 fallback 及 Manager 运行通知位于共用源码。`FGAText` 从 Carpet 当前语言动态生成无 FGA 客户端 fallback
- 实际适配版本：命令入口及扩展键为 `1.21.1`、`26.3`；分类器核心反馈与按键值补全覆盖本构建矩阵：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 构建/自动测试：`:26.3:build` 成功；该任务的预处理依赖图编译了上述 10 个节点的主源码，测试仅在 `26.3` 执行。`FakePlayerItemSortCommandLocalizationTest` 与其余套件合计 30 项测试通过。测试覆盖每个设置键对应的值、扩展键可见性、三种语言 fallback 和翻译键占位符
- 产物：`mod-builds/20260929-093021/carpet-fga-addition-1.5.16+v2609290925-mc26.3.jar`
- 客户端/服务端要求：不新增客户端依赖、网络协议、配置格式或存档字段；命令仍受原 `commandPlayer` 与 OP 权限约束，仅将 26.3 的命令树扩展到与 1.21.1 一致
- 游戏内验证：未启动客户端/服务端；真实 Tab 建议、FGA 英中客户端及无 FGA 客户端的聊天显示仍待手动冒烟。步骤见 `FeatureSmokeTestPlanTest` 中 `fakePlayerItemSort26_3CommandPortAndLocalizedFeedback`

## 经验扁平化快速加减与 ORG 转移兼容（全构建矩阵）

- 规则：`experienceLevelCost`；默认值 `false`、选项 `false` / `29-30` / `0-1` 均不变。启用固定消耗模式后，经验总量按整数点数计算；`29-30` 在30级以下保留原版曲线、30级及以上每级107点，`0-1` 则每级7点。30级以上的经验等级换算为有界常数时间；30级以下最多处理30个等级，不再对高等级执行逐级换算
- ORG 经验转移为可选适配：1.21.x 构建分支识别 `org.carpetorgaddition.wheel.ExperienceTransfer`，允许 ORG `1.41.5` / `1.41.6`；26.1.2 对应 `boat.carpetorgaddition.wheel.ExperienceTransfer` / ORG `1.44.0`；26.2 对应 `boat.carpetorgaddition.wheel.misc.ExperienceTransfer` / ORG `1.45.1`；26.3 对应 `boat.carpetorgaddition.command.XpTransferCommand$ExperienceTransfer` / ORG `1.46.0`。其他 ORG 版本不会启用此适配，并会记录警告
- ORG 转移使用扁平化后的整数经验总量并沿用玩家原生加减经验入口；保留 ORG 命令权限及不足经验反馈；先校验接收方容量，容量不足时双方均不改变；自转移不改变余额。整数计算修正进度浮点表示误差，等级限制在 `int` 范围，转移不读写离线玩家数据
- 源码预处理范围：`MC >= 1.21.1 && MC <= 26.3`。实际构建节点：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成构建与自动测试：上述10个节点分别通过 `:<版本>:build`；每个最终 JAR 均检查了 Fabric manifest、ORG Mixin 配置、生成的目标类和 ORG 版本白名单。最终产物分别归档在 `mod-builds/20260928-231807`、`20260928-231858`、`20260928-232008`、`20260928-232052`、`20260928-232153`、`20260928-232254`、`20260928-232348`、`20260928-232810`、`20260928-232922`、`20260928-233212`
- 已通过隔离服务端 ORG Mixin 冒烟：1.21.11 + ORG `1.41.5`、26.1.2 + `1.44.0`、26.2 + `1.45.1`、26.3 + `1.46.0`。26.3 另通过完整经验加减与 ORG 转移探针（含规则关闭/开启、30级边界、大额正负经验、余数守恒、不足经验与接收方溢出）；无 ORG 对照也通过。报告：`scripts/logs/org-flat-experience-smoke-1_21_11-20260928-232357-904/summary.txt`、`scripts/logs/org-flat-experience-smoke-26_1_2-20260928-230245-017/summary.txt`、`scripts/logs/org-flat-experience-smoke-26_2-20260928-230736-013/summary.txt`、`scripts/logs/org-flat-experience-smoke-26_3-20260928-231106-785/summary.txt`、`scripts/logs/flat-experience-smoke-26.3-20260928-231155-103/summary.txt`、`scripts/logs/flat-experience-smoke-26.3-20260928-231330-568/summary.txt`
- 1.21.1 + ORG `1.41.6` 的隔离服务端冒烟未能运行到 FGA 探针：服务端在 ORG 自身 `LivingEntityMixin` 的 MixinExtras Expression 注入点应用阶段失败（加载到 MixinExtras `0.3.5`）；因此该组合的运行时适配待确认，不据此推断 FGA 转移探针通过，也未为掩盖 ORG 依赖冲突而改动依赖
- 测试入口：`FlatExperienceMathTest` 覆盖两种固定消耗曲线、全部进度余数、30级双向跨越及大额正负经验；`scripts/powershell/flat-experience-smoke-26.3.ps1` 覆盖服务端经验行为；`scripts/powershell/org-flat-experience-mixin-smoke.ps1` 用于各版本的可选 ORG Mixin 隔离验证。26.3 完整 ORG 命令行为已验证；其他节点目前验证到代表性版本的 Mixin 应用和辅助逻辑，未逐节点执行完整 ORG 命令矩阵
- 客户端/服务端要求：只需服务端安装 FGA；不增加客户端依赖、网络协议、配置格式、权限或存档数据，也不改变规则默认值和版本号。真实图形客户端经验条显示尚未验证

## 26.3 长名称假人玩家列表编码与解码

- 问题：`fakePlayerNameLength` 设置为大于 16 后，长名称假人加入、实时同步或客户端重进后的玩家列表初始化可能触发 UTF 玩家名 16 字符限制；安装 FGA 与 Flashback 的客户端还会在录制线程重新编码收到的玩家列表包时崩溃，因为解码后的包没有服务端构造时的许可标记
- 修复：只在长名称玩家列表数据包的写入/解码作用域内扩展 UTF 长度，并在 FGA 握手补发初始化列表时保留该包级许可；重新编码时也检查包中实际档案名称，兼容录制线程保存解码后的包；不扩大其他包（包括第三方记分板队伍前缀）的长度限制。未安装 FGA 的客户端仍使用兼容别名
- 源码分支边界：`MC >= 26.3` 使用独立的 `ModernLongNameClientHandshakeMixin`、`ModernLongNameServerHandshakeMixin`、`ModernLongNamePlayerInfoEncodeMixin`、`ModernLongNamePlayerInfoDecodeMixin`、`ModernLongNameUtf8CodecMixin`；`MC < 26.3` 保留 `ClientPacketListenerMixin`、`ServerGamePacketListenerImplMixin`、`FriendlyByteBufMixin`、`FriendlyByteBufWriteMixin` 等旧版路径，注册互斥；共享玩家别名、名称装饰和 Payload 结构仍沿用原实现。根节点 1.21.1 的新分支源码和注册项使用 `//$$` 保持关闭
- 实际适配版本：当前现代分支只有 26.3 构建节点；旧分支为 `1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`，没有向这些版本移植现代编解码；`26.3+` 是用户要求的代码分支边界，不代表尚不存在于矩阵中的后续版本已经适配或测试
- 客户端/服务端要求：服务端安装 FGA；26.3 的完整长名称要求客户端也包含对应编解码修复，旧 1.5.15 客户端不兼容完整长名；未安装 FGA 的客户端兼容别名路径尚未在本轮实测；不改变规则默认值、配置、存档或网络协议
- 源码测试流程：`src/test/java/carpet/fga/FeatureSmokeTestPlanTest.java` 中的 `fakePlayerLongNamePlayerInfoEncoding`；先让 FGA 客户端加入隔离 26.3 服务端，再将 `fakePlayerNameLength` 设为 32 并召唤 17 字符假人；验证实时 TAB 完整名称，假人保持在线时断开并重进后再次验证；执行 `scripts/powershell/long-name-player-info-replay-smoke-26.3.ps1`，在实际客户端中验证“解码收到的包→独立线程重新编码”，保留名称、UUID、皮肤属性和数据包字节，同时确认 129 字符名称及普通 16 字符字符串的负向校验；加上 `-FlashbackJar <已安装的 Flashback JAR>` 后，还会调用该模组实际 `AsyncReplaySaver` 队列保存长名玩家列表包；另测多人服务器中的录制与回放界面、无 FGA 客户端别名与第三方 Team 前缀
- 拆分前代码验证：`:26.3:build`（包含 `:26.3:test`）通过；构建 `carpet-fga-addition-1.5.16+v2609280841-mc26.3.jar` 归档于 `mod-builds/20260928-084406/`；依赖节点编译不等于已移植现代编解码
- 本轮分支拆分验证：`:26.3:build --offline --no-daemon --configure-on-demand --max-workers=1` 通过，包含 26.3 的 23 个自动测试（零失败、零跳过）；新增 `LongNameVersionBoundaryTest` 验证生成配置中的新旧编解码和连接 Mixin 互斥。前置任务完成当前 10 个矩阵节点的主源码编译；另逐节点检查生成的 Mixin JSON，9 个旧节点只注册旧路径，26.3 只注册现代路径；只有 26.3 执行本轮测试和打包，不能视为其他节点运行时通过。最终 JAR 归档于 `mod-builds/20260928-093623/carpet-fga-addition-1.5.16+v2609280933-mc26.3.jar`，确认包含 5 个现代 Mixin，不注册旧路径、不包含已撤回的旧类名或测试探针
- 本轮分支拆分服务端冒烟：用 `scripts/gradle/published-fga-jar-smoke.init.gradle` 排除工作区主类，只加载上述归档 JAR；唯一一台回环隔离 26.3 测试服在 2026-09-28 09:40:56 启动到 `Done`，设置 `fakePlayerNameLength 32` 后，17 字符假人 `FGA_LongFake_0001` 于 09:41:41 正常上线，09:42:00 查询在线列表确认后移除并正常停服，运行任务退出码为 0；日志 `build/long-name-branch-smoke-26.3-20260928-093623/server/logs/latest.log`。该轮只验证服务端启动、现代 Mixin 相关类加载与长名假人创建，没有真实客户端连接，不代表客户端解码、握手补发或 Flashback 回归通过；日志中的 Windows OSHI 性能计数器和默认平坦生成配置诊断与本次 Mixin 无关
- 前轮客户端/服务端冒烟：独立回环 26.3 开发运行环境中，测试客户端 `FGANameSmoke` 先加入；控制台设置 `fakePlayerNameLength 32` 并召唤 `FGA_LongFake_0001`；客户端实时 TAB 与假人在线期间重进后的 TAB 均显示完整名称，连接未断开。该轮没有验证 Flashback 重新编码；客户端和服务端均已正常关闭
- 拆分前客户端录制回归：修复前使用 `-ExpectFailure`，实际客户端在独立编码线程中复现 17 字符名称的 `String too big`，对照报告 `scripts/logs/long-name-replay-smoke-26.3-20260928-083756/summary.txt`；修复后在隔离 26.3 开发客户端、Flashback `0.43.6` 中通过 12、17、24、32 个中文字符及 128 字符名称的编码/解码，名称、UUID、皮肤属性和包字节保持一致，129 字符输入被拒绝，普通字符串长度限制及线程作用域不泄漏；实际 `AsyncReplaySaver` 成功保存长名数据包片段，报告 `scripts/logs/long-name-replay-smoke-26.3-20260928-084834/summary.txt`；测试客户端正常退出，测试探针不进入发布 JAR。此结果属于拆分前实现，不替代本轮最终 JAR 的客户端回归
- 尚未验证：拆分后的最终 JAR 客户端入服、长名在线期间重进和 Flashback 重新编码/录制回放，由用户进行客户端测试；无 FGA 客户端的别名显示、第三方记分板队伍前缀更新，以及其他 Minecraft 版本的运行时行为；本次没有将现代修复移植到 26.3 之前或尚未纳入矩阵的后续版本

### 26.3 FGA 版本混用检查（旧客户端长名断连已复现）

- 检查对象：26.3 服务端 FGA `1.5.16+v2609280841-mc26.3`、客户端 FGA `1.5.15+v2609251958-mc26.3`；两个模组版本都发送 `HandshakePayload(1)`，当前服务端只记录是否安装 FGA，不能由此识别客户端是否支持新的长名 UTF 路径
- 源码结论与处理决定：1.5.15 客户端只有 `FriendlyByteBuf` 长度扩展，没有 26.3 新增的直接 UTF 玩家列表解码修复；当前服务端仍可能向它发送完整长名称。用户已放弃纯服务端兼容旧客户端的方向，保留上一版客户端/服务端编解码修复，不新增能力协商或旧版兜底
- 测试流程：`scripts/powershell/mixed-version-long-name-smoke-26.3.ps1` 与 `src/test/java/carpet/fga/FeatureSmokeTestPlanTest.java` 的 `fakePlayerLongNameMixedFgaVersions`；加载指定发布 JAR，排除工作区主类，先验证短名，再召唤 17 字符假人；覆盖旧客户端、新客户端、无 FGA 客户端。已撤回测试中的强制短名降级入口；客户端/服务端测试探针均不进入发布 JAR，专用探针限定 `MC == 26.3`
- 已完成用户客户端联机复现：隔离 26.3 服务端加载上述 1.5.16 JAR，用户实际客户端日志声明 FGA `1.5.15`；短名假人 `FGAMixedShort` 在线时用户于 2026-09-28 09:18:42 正常进服；09:19:10 控制台召唤 `FGA_LongFake_0001` 后客户端同秒断连。客户端报告 `disconnect-2026-09-28_09.19.10-client.txt` 明确显示 `player_info_update` 解码失败，根因 `The received string length is longer than maximum allowed (17 > 16)`，栈经过 `Utf8String.read` 和 `ByteBufCodecs$14.decode`，不是 Flashback 的录制重编码故障；服务端报告位于 `build/mixed-version-manual-26.3-20260928-091357/server/logs/latest.log`。09:20:08 已仅移除本次长名测试假人，避免用户重进再次断连
- 自动化验证状态：本轮 `:26.3:build` 已重新编译客户端和服务端测试探针，26.3 自动测试通过；混用自动化脚本此前的启动器时机错误与默认白名单拒绝连接均属于测试环境失败，不计为兼容性测试结果，撤回降级入口后的联机脚本尚未重新执行。混用断连结论来自用户手动进服及双方日志，不是尚未通过的自动化联机脚本
- 范围与待人工确认：当前 1.5.16 服务端与 1.5.15 客户端的完整长名路径已确认不兼容；本轮仅拆分实现分支，不改变握手通道 `carpet-fga-addition:handshake`、Payload 格式、规则默认值、配置、存档格式或权限；拆分后的客户端联机回归由用户进行，无 FGA 客户端对照、其他 Minecraft 节点的运行时行为及未来版本仍待验证

## 假人物品分类异步召唤去重与库存守恒

- 功能：`summon` 分类模式遇到异步假人档案预加载时，每个目标名只保留一个待处理召唤；复用已在线的分类假人，不重复登录/踢下线；目标生成未完成时不继续尝试后缀名字，避免无界创建请求。`quickopen` 行为不变
- 源码预处理条件：`MC >= 1.21.1 && MC <= 26.3`；分类功能本身仍沿用其原有版本范围
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：以上 10 个发布节点均通过 `buildAllVersions`，自动测试任务通过；构建产物位于 `build/libs/20260927-191446/`，逐版本 JAR 及 SHA-256 归档于 `mod-builds/20260927-191800/` 至 `mod-builds/20260927-191800-10/`
- 已完成服务端冒烟：隔离 26.3 服务端分别验证 `quickopen` 和异步预加载下的 `summon`；两种模式各 16 个物品均从源背包完整转入目标库存。`quickopen` 未登录目标；`summon` 只登录一个 `bulk_cobblestone`，没有编号后缀假人，然后在批次结束后下线并保存库存。服务端正常停止；脚本 `scripts/powershell/fake-player-item-sort-smoke-26.3.ps1`，报告 `scripts/logs/fake-player-item-sort-smoke-26.3-20260927-192059/summary.txt`
- 客户端/服务端要求：仅服务端 FGA；未改变配置格式、规则默认值、存档格式或网络协议。自动召唤的分类假人会在当前批次完成后下线，库存保存在其 playerdata 中
- 待人工确认：其他 9 个版本尚未分别启动隔离游戏服验证；真实服务器中切换至 `summon` 后的玩家可见消息与重登取物体验仍需人工确认

## `/tp <坐标> <维度>` 后缀维度语法（全构建矩阵）

- 功能：`/tp <x> <y> <z> <维度>` 和 `/teleport <x> <y> <z> <维度>` 将命令执行者传送到指定维度的坐标；沿用原版 `/tp` 坐标分支的反馈、权限和相对坐标语义，并检查目标世界边界
- 源码预处理条件：`MC >= 1.21.1 && MC <= 26.3`
- 客户端/服务端要求：仅服务端安装 FGA；无自定义网络协议、存档数据或配置变更；不改变 `spectatorFreeTeleport` 规则默认值及 `/tp` 根命令权限
- 实际适配版本：当前构建矩阵全部 10 个节点；`/tp` 与 `/teleport` 分别挂接到原版命令树，兼容不同版本的别名结构
- 已完成编译/测试/构建版本：以上 10 个节点均通过本轮 `buildAllVersions`，产物及 SHA-256 清单归档于 `mod-builds/20260928-123844`
- 已完成服务端冒烟：10/10 节点在唯一命名的平坦测试存档中验证 `/tp <相对坐标> minecraft:the_nether`、`/teleport <绝对坐标> minecraft:overworld`、目标维度、相对坐标原点、无 Mixin 注入错误及正常停服；报告分别为 `scripts/logs/tp-dimension-suffix-smoke-all-20260928-121729/progress.log`（1.21.1、1.21.3、1.21.4、1.21.5、1.21.8、1.21.10）和 `scripts/logs/tp-dimension-suffix-smoke-all-20260928-122502/summary.json`（1.21.11、26.1.2、26.2、26.3）
- 待人工确认：真实客户端的命令树展示与维度 Tab 补全

## `spectatorFreeTeleport` 完整传送权限选项（1.5.16）

- 功能：`spectatorFreeTeleport` 从布尔规则扩展为 `false`、`true`、`full`；`false` 保留原版权限，`true` 保留旁观者仅传送自己的旧行为，`full` 允许任意游戏模式的玩家使用完整 `/tp` 与 `/teleport`，包括多目标、传送其他实体和跨维度；`full` 仅对这两个命令绕过 TIS `opPlayerNoCheat` 与 AMS `preventAdministratorCheat` 的权限包装
- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`；命令权限包装、选择器权限和规则变更后的命令树刷新均使用共享实现
- 实际适配版本：当前构建矩阵 `1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/测试/构建版本：以上 10 个矩阵节点均由 `buildAllVersions` 完成 `compileJava`、`test`、`build`；归档于 `build/libs/20260927-161112`
- 已完成服务端冒烟：26.3 隔离测试服启用 TIS `opPlayerNoCheat` 和 AMS `preventAdministratorCheat`；非 OP `false` 模式在生存、创造、冒险、旁观四种模式都没有 `/tp` 权限；`true` 模式非 OP、OP、取消 OP 的旁观假人均只能传送自己并可使用跨维路径；`full` 模式由非 OP 与 OP 分别在四种模式执行自传送、传送其他玩家、多目标选择、显式维度坐标传送、跟随异维玩家及跨维传送其他玩家，均通过；服务端正常关闭。脚本：`scripts/powershell/spectator-free-teleport-smoke-26.3.ps1`；报告：`scripts/logs/spectator-free-teleport-smoke-26.3-20260927-161808/summary.txt`
- 客户端/服务端要求：只需服务端安装 FGA；不添加网络协议或客户端代码；规则默认值仍为 `false`，没有改变存档数据。规则值类型由布尔扩展为字符串，但旧配置文本 `false` / `true` 仍是有效选项
- 待人工确认：尚未用真实图形客户端验证命令树同步和 Tab 补全；尚未用带既存 `spectatorFreeTeleport=true` Carpet 规则配置的重启测试验证旧配置读取；其余 9 个版本通过了构建和自动测试，但未逐个启动游戏内服务端冒烟

## 旁观者跨维度传送

- 功能：扩展 `spectatorFreeTeleport`；旁观者可用 `/tp <在线玩家>` 跟随异维玩家，也可用 `/tp in <维度> <x> <y> <z>` 和 `/teleport in <维度> <x> <y> <z>` 显式选择维度与坐标；显式坐标路径只移动执行者自身，并检查世界边界
- 源码预处理条件：规则与命令为 `MC >= 1.21 && MC <= 26.3`；`1.21.1` 使用旧版 `ServerPlayer.teleportTo` 签名，`1.21.3+` 使用包含相对位置参数的签名；`1.21.11+` 使用更新后的维度标识符 API
- 实际适配版本：当前构建矩阵 `1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：当前 10 节点 `buildAllVersions` 全部成功，包含各版本 `test` 和打包任务；产物归档于 `build/libs/20260927-141837`，另复制至 `mod-builds/20260927-141837`
- 已完成服务端冒烟：`scripts/powershell/spectator-free-teleport-smoke-26.3.ps1` 在隔离 26.3 测试存档通过；启用 `spectatorFreeTeleport`、TIS `opPlayerNoCheat` 和 AMS `preventAdministratorCheat` 后，非 OP、OP、取消 OP 的旁观假人均成功传送至异维在线假人，并成功使用两种显式维度坐标语法；尝试 `/tp <其他玩家> <坐标>` 被拒绝且目标未移动，服务端正常关闭；最终共享 API 实现报告 `scripts/logs/spectator-free-teleport-smoke-26.3-20260927-142126/summary.txt`
- 客户端/服务端要求：仅服务端安装 FGA；不改变规则默认值、配置格式、存档数据、网络协议或权限模型；显式坐标子命令只在规则开启且执行者为可用该规则的旁观玩家时开放
- 尚未完成验证：真实客户端命令树同步、Tab 补全展示及坐标传送反馈视觉效果；其他 9 个矩阵版本尚未运行对应游戏内服务端冒烟

## 增强假人重新上线（全构建矩阵）

- 规则：`enhancedFakePlayerRejoin`，默认 `false`，需要服务端 Carpet TIS，客户端不要求安装 FGA
- 功能：TIS `/player <名字> rejoin` 保留原版玩家存档中恢复的载具与非玩家乘客；增加 `rejoin at <坐标> [facing <朝向>] [in <维度>]`，恢复后移动整个载具树
- 源码预处理条件：`MC >= 1.21.1 && MC <= 26.3`
- 实际适配版本：当前构建矩阵全部 10 个节点；坐标重进逻辑使用共享实现，1.21.x 通过玩家上线后的服务端 tick 完成，26.1.2、26.2、26.3 才注册与现代 Carpet 回调匹配的载具保留 Mixin
- 数据与权限：不新增配置格式或存档结构，不修改默认命令权限；关闭规则时保留 TIS 原命令；沿用原版仅在载具剩余一名玩家乘客时持久化载具的条件
- 已完成编译/测试/构建版本：当前构建矩阵 10 个节点均通过本轮 `buildAllVersions`，逐版本测试、构建和 JAR 归档记录位于 `mod-builds/20260928-123844`
- 已完成服务端冒烟：26.3 隔离测试存档、Carpet TIS `1.82.4` 下通过假人与猪同乘船、离线时载具离场、重启后原位恢复、同一载具 UUID 的跨维度指定坐标重进两次，以及规则关闭后 TIS 无参数命令继续可用；当前源码测试报告 `scripts/logs/fake-player-rejoin-smoke-26.3-20260928-123456/summary.txt`。坐标维度命令全矩阵服务端启动同时验证了所有节点 Mixin 配置无注入错误，报告见上方 `tp-dimension-suffix` 冒烟记录
- 待人工确认：尚未用真实客户端验证朝向与视觉同步；其他 9 个版本尚未安装匹配的 Carpet TIS 执行完整载具重进实测；1.21.1–1.21.11 对应的 Carpet TIS 版本/API 兼容性需在目标服务端人工确认

### 坐标重进重复提示修复（全构建矩阵）

- 问题与修复：坐标重进的待处理状态此前依赖 TIS 异步 `ThreadLocal` 标记在假人创建回调中仍可读取；标记未跨回调保留时，目标重进已结束但状态未清除，后续每次执行都会提示 `A rejoin is already pending for this player`。现在旧 Carpet 版本在玩家确实上线后的首个服务端 tick 完成坐标重进，现代 Carpet 在异步回调中完成并清理；下次请求时还会清理过期、已在线或已不处于创建流程的残留项；普通 TIS 重进仍由现代回调按原标记保留载具
- 源码预处理条件与适配版本：命令与规则 `MC >= 1.21.1 && MC <= 26.3`；仅 `MC >= 26.1 && MC <= 26.3` 注册载具保留 Mixin；旧 Carpet API 未提供创建中状态查询时，待处理标记最多保留 200 tick
- 验证：26.3 当前源码的 `scripts/powershell/fake-player-rejoin-smoke-26.3.ps1 -SkipCompile` 在隔离服务器、Carpet TIS `1.82.4` 下通过普通重进、两次连续跨维坐标重进、相同载具 UUID 与猪乘客恢复、目标坐标检查及规则关闭后的 TIS 原命令；报告 `scripts/logs/fake-player-rejoin-smoke-26.3-20260928-123456/summary.txt`
- 本轮全矩阵 `buildAllVersions` 通过，包含全部 10 个节点的编译、测试与构建；归档于 `mod-builds/20260928-123844`
- 客户端 / 服务端影响：服务端修复，不新增客户端要求、配置格式、存档数据、网络协议、规则默认值或权限变化；尚未进行真实图形客户端测试
- 待人工确认：其他版本安装匹配的 Carpet TIS 后，连续执行至少两次 `/player <名字> rejoin at <坐标> [in <维度>]`，确认不再出现重复待处理提示


## 夺舍后聊天签名校验失败（1.5.16）

- 问题：夺舍后发送聊天消息时客户端记录 `Received message with invalid signature`，并显示“聊天验证错误”；日志同时出现玩家档案公钥校验失败
- 原因与修复：身体状态交换错误地连同 `RemoteChatSession` 一起互换，而签名会话属于各自的网络连接；现在夺舍与恢复身体状态时均保留连接原有聊天会话
- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`，修改位于共享的 `SwapSnapshot`
- 实际适配版本：共享源码覆盖当前矩阵 `1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`；本次按单版本基线先验证 `26.3`
- 已完成编译/构建版本：Gradle 为生成 26.3 预处理产物编译了当前 10 个矩阵节点；`:26.3:test` 与 `:26.3:build` 通过，26.3 构建产物归档于 `mod-builds/20260927-115647`（仅 26.3 执行测试和打包任务）
- 尚未完成单独构建验证版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`
- 客户端/服务端要求：修复在服务端 FGA 的玩家身体状态交换路径；不改变客户端要求、配置格式、存档数据、网络协议、规则默认值或权限
- 测试流程源码：`src/test/java/carpet/fga/FeatureSmokeTestPlanTest.java` 中的 `playerPossessionSignedChatSession`；需要两个已认证客户端的 secure-chat 多人冒烟，分别在夺舍前、夺舍中、恢复后发送消息，并检查客户端及服务端日志
- 待人工确认：本地可用日志对应 26.3；日志中的客户端确有无效签名和公钥验证报错，服务端 `debug.log` 未找到，需真实 secure-chat 多人测试确认发送、签名状态与恢复行为；其他版本尚未单独运行测试或打包

## Unicode 假人背包标题导致的 26.3 客户端断开（2026-09-27）

- 问题：启用 `fgaUnicodeArgumentsSupport` 后，通过 Carpet Org 假人背包指令打开中文名假人背包时，服务端编码 `ClientboundOpenScreenPacket` 失败；`latest.log` 显示 `Player name contained disallowed characters`，异常资料名位于标题组件的玩家头像资料中
- 修复：仅在打开容器包构造时递归检查标题组件；对头像资料中的非 `[A-Za-z0-9_]{1,16}` 名称生成基于原 UUID 的安全别名，同时保留原 UUID、皮肤属性和中文可见标题；普通玩家名不变
- 源码预处理条件：`MC == 26.3`
- 实际适配版本：`26.3`；其他版本不含依赖 26.3 组件 API 的修复类
- 已完成编译/构建版本：`:26.3:test` 和 `:26.3:build` 通过；26.3 构建产物归档于 `mod-builds/20260927-111151`
- 已完成服务端协议冒烟：隔离 26.3 服务端启用 `fgaUnicodeArgumentsSupport`，测试探针创建带中文头像资料和中文标题的 `ClientboundOpenScreenPacket`，使用对应 `STREAM_CODEC` 编码并解码成功；解码后标题仍为中文、头像资料名为合法 ASCII 且 UUID 不变，服务器干净退出；流程为 `scripts/powershell/unicode-player-inventory-screen-smoke-26.3.ps1`，报告为 `scripts/logs/unicode-player-inventory-screen-smoke-26.3-20260927-111333/summary.txt`
- 客户端/服务端要求：服务端包标题在发送前处理；客户端无需安装 FGA；不改变规则默认值、配置格式、存档数据、协议结构或权限
- 尚未完成验证：真实客户端安装 Carpet Org 后，用中文名假人实际打开其背包并确认客户端不再断开；其他版本无需该 26.3 专用补丁，但没有在本次单版本基线流程中重新运行全版本矩阵

## 旁观者自身传送与反作弊规则兼容（2026-09-26）

- 功能：修复 26.3 中 `spectatorFreeTeleport` 被 TIS `opPlayerNoCheat`、AMS `preventAdministratorCheat` 等后续命令权限包装器覆盖，导致 `/tp` 与 `/teleport` 对旁观者不可用的问题；非 OP 旁观者仍只能传送自己
- 源码预处理条件：主体 `MC >= 1.21 && MC <= 26.3`；仅 `26.3` 将 `TeleportCommandMixin` 优先级提高到 `2000`，并将相关注入设为必需匹配；更早节点保留现有优先级与可选注入设置
- 实际适配版本：行为修复及服务端冒烟仅验证 `26.3`；当前矩阵其他版本未进行单独运行测试
- 已完成编译/构建版本：`:26.3:build`、`:26.3:test` 通过并生成 `1.5.15` 的 26.3 jar，归档于 `mod-builds/20260926-232006`；Preprocessor 构建依赖还编译了当前 10 个矩阵节点，但本次只构建并归档 26.3
- 已完成服务端冒烟：隔离服务器安装 FGA、TIS、AMS、Carpet Org Addition，启用 `spectatorFreeTeleport`、`opPlayerNoCheat` 和 `preventAdministratorCheat`；非 OP 与 OP 旁观假人均成功用 `/tp @s`、`/teleport @s` 和 `/tp 玩家名` 传送自身，传送另一玩家被拒绝，服务器正常关闭；2026-09-27 再加入 GCA、REMS、Fabric Permissions API，并验证非 OP → OP → 取消 OP 后仍可传送自己；测试流程源码为 `scripts/powershell/spectator-free-teleport-smoke-26.3.ps1`，最新报告为 `scripts/logs/spectator-free-teleport-smoke-26.3-20260927-091312/summary.txt`
- 客户端/服务端要求：仅服务端安装 FGA；不改变客户端要求、配置格式、存档数据、网络协议或规则默认值；仅修正 26.3 的命令权限 Mixin 应用顺序
- 尚未完成验证：其余 9 个矩阵版本的独立构建/运行、真实客户端在旁观模式下的命令树同步，以及 OP 在不同反作弊规则组合下的行为；客户端日志显示 08:53 取消 OP 后发送了无参数 `tp` 并收到“不完整的命令”，本机服玩家存档在 09:00 与 09:03 均记录创造模式（`playerGameType=1`），尚不能据此确认 08:53 的实际游戏模式；客户端安装 FGA `1.5.15`，本机服加载 `1.5.15+v2609262316-mc26.3`

## 进服提示与服务器公告跨版本适配（2026-09-26）

- 功能：`customJoinNotice` 及 `/fga joinNotice` 欢迎语、RGB 颜色、开服日期；独立 `serverAnnouncements` 规则与 `/fga announcement`，支持题头、自动/指定编号、发布者、分钟时间戳、永久/限时、启用/隐藏、JSON 编辑与重载，以及进服/维度三维坐标范围进入触发
- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述 10 个构建节点的 `buildAllVersions --configure-on-demand --max-workers=2` 均通过，包含每个节点的测试任务；最终构建归档位于 `build/libs/20260926-160354`
- 已完成单元测试：公告时效解析、范围端点归一化、时间/发布者/题头/`/n` 格式和启用/隐藏/到期边界；进服日期和 RGB 欢迎语测试也在所有 10 个构建节点运行
- 已完成服务端命令冒烟：26.3 隔离临时存档验证总规则、公告新建/编号、列表/详情、永久转限时、启用/停用、隐藏/显示、进服/区域触发切换、题头修改、JSON 重载/删除，以及欢迎语日期命令；报告为 `scripts/logs/server-startup-smoke-20260926-160600/summary.json`
- 客户端/服务端要求：仅服务端安装 FGA；无需客户端模组，不新增网络协议；配置分别位于 `world/config/carpetfgaaddition/join-notice.json` 和 `world/config/carpetfgaaddition/announcements.json`
- 尚未完成验证：真实客户端欢迎语颜色和点击填入聊天栏体验、日期 Tab 补全界面、真实玩家进服公告，以及在线玩家离开并再次进入指定坐标范围的游戏内行为；服务端命令冒烟不等同于客户端行为验证

## 附魔金胡萝卜名称组件格式修复（2026-09-20）

- 功能：修复部分版本中 `enchantedGoldenCarrot` 合成结果把 `{"text":"附魔金胡萝卜","italic":false}` 当作普通文本显示的问题
- 源码预处理条件：配方资源保持通用 JSON；`common.gradle` 的 `processResources` 按 `mcVersion >= 1.21.5 && mcVersion <= 26.2` 生成对象文本组件，`1.21.1-1.21.4` 和 `26.3` 生成字符串化文本组件
- 实际适配版本：`1.21.1`、`1.21.2`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.6`、`1.21.7`、`1.21.8`、`1.21.9`、`1.21.10`、`1.21.11`、`26.1`、`26.1.1`、`26.1.2`、`26.2` 和 `26.3`
- 已完成编译/构建版本：10 个构建节点 `1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2` 和 `26.3` 的 `buildAllVersions --configure-on-demand` 均通过；最终归档位于 `build/libs/20260920-102228`
- 具体子版本冒烟测试：`scripts/powershell/server-startup-smoke-subversions.ps1` 在无结构平坦临时世界中完成 16/16，报告目录为 `scripts/logs/server-startup-smoke-subversions-20260920-103911`
- 尚未完成验证：真实客户端工作台界面中附魔金胡萝卜名称的视觉显示和配方书刷新
- 客户端/服务端要求：不改变现有客户端要求、配置格式、存档数据、网络协议、规则默认值或权限模型

## 地形重生成任务上限与惰性重生成（2026-09-19）

- 功能：`regenerateTerrain` 的单任务（包括合并任务）区块上限从 4096 提高到 16384；`create` 改为标记目标区块，等目标区块下次加载时按正常流程重新生成
- 源码预处理条件：地形重生成主体为 `MC == 1.20.1 || MC >= 1.21 && MC <= 26.3`
- 客户端/服务端要求：服务端安装 FGA，客户端不需要安装 FGA；会修改世界地形，清空任务仍会创建 Region 备份
- 本次变更影响：不改变配置格式、网络协议、规则默认值或命令权限；提高上限会扩大单次操作的潜在存档修改范围
- 已完成验证：10 个构建节点的 `buildAllVersions --configure-on-demand` 均通过；本轮构建归档位于 `mod-builds/20260920-081245` 至 `mod-builds/20260920-081247-3`；测试存档中已验证 1 区块清空、Region 备份、16384 区块草稿接受和超过上限拒绝
- 尚未完成验证：真实存档中的完整 16384 区块清空、重生成与区块加载时序、失败恢复和客户端显示
- 待人工确认：使用测试存档验证完整 16384 区块操作、合并任务上限和惰性重生成完成后的区块加载行为

## `/controlPlayer` 默认查询执行者关系（2026-09-19）

- 功能：根命令 `/controlPlayer` 改为查询执行者自己的夺舍关系；查询全部关系使用 `/controlPlayer list`，不再注册或补全 `@`
- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述全部 10 个版本；本次命令变更后的产物分别归档于 `mod-builds/20260919-224423`、`20260919-224453`、`20260919-224627`、`20260919-224701`、`20260919-224735`、`20260919-224807`、`20260919-224845`、`20260919-224906`、`20260919-224935`、`20260919-224959`
- 尚未适配版本：无
- 客户端/服务端要求：服务端安装 FGA，客户端不需要安装 FGA；未改变客户端要求、配置格式、存档数据、网络协议、命令权限或规则默认值
- 尚未完成验证：真实服务器中的命令树、玩家执行 `/controlPlayer` 自查询、`list` 查询全部关系、指定玩家查询、控制台来源和 Tab 补全行为

## 新规则服务端冒烟测试（2026-09-20）

- 测试范围：启动隔离临时世界后通过服务端控制台切换 `namedEnderPearlTeleport`、`boneMealMaxEfficiency`、`fireAspectOnTools`、`soulSpeedNoDurability`、`thornsNoDurability`、`lightSourceStonecuttingRecipes`、`lightBlockBreakable` 和 `mapLoadCommandPermission`
- 构建节点脚本与报告：`scripts/powershell/server-startup-smoke-all.ps1`，报告目录为 `scripts/logs/server-startup-smoke-20260920-075643`；10 个构建节点均启动到 `Done`、8 条规则切换命令被接受并正常停服
- 具体子版本脚本与最终报告：`scripts/powershell/server-startup-smoke-subversions.ps1`，报告目录为 `scripts/logs/server-startup-smoke-subversions-20260920-090628`
- 具体子版本冒烟通过：16/16，`1.21.1`、`1.21.2`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.6`、`1.21.7`、`1.21.8`、`1.21.9`、`1.21.10`、`1.21.11`、`26.1`、`26.1.1`、`26.1.2`、`26.2`、`26.3` 均启动到 `Done`、8 条规则切换命令被接受并正常停服；范围产物的非构建节点通过临时版本属性覆盖并在测试后恢复
- 最终全版本构建：10 个构建节点 `1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3` 的 `buildAllVersions --configure-on-demand` 均通过，构建归档位于 `build/libs/20260920-092350`，最终 JAR 归档位于 `mod-builds/20260920-092522` 至 `mod-builds/20260920-092524-2`
- 修复项：`CraftingMenuEnchantedGoldenCarrotMixin` 按 `1.21.1` 与 `1.21.3+` 使用正确的 `Level`/`ServerLevel` 描述符；26.3 骨粉 Mixin 增加 `BonemealSource` 参数；末地传送门和末地折跃门按 1.21.9 与 1.21.10+ 的 `method_9548` 签名分别注入；26.3 构建改用正式 Carpet `26.3+v260915` 与 Fabric Loader `0.19.5`
- 地形冒烟：`scripts/powershell/terrain-regeneration-smoke-1.21.1.ps1` 已改为验证当前进程内即时执行模型，报告目录为 `scripts/logs/terrain-clear-smoke-20260920-080959`；实际清空和 Region 备份通过，恰好 `16384` 区块草稿被接受，超过上限的 `16512` 区块请求被拒绝
- 尚未完成验证：各规则的实际游戏内效果、客户端交互、切石机配方、工具附魔熔炼、末影珍珠传送、骨粉成长；地形 16384 区块仅验证了草稿上限，未执行完整 16384 区块清空或重生成

本文档记录新增功能和跨版本同步修改的适配范围，避免把源码门控、实际构建和游戏内验证混为一谈

当前构建矩阵来自 `settings.json`

`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`

## 记录格式

每次新增或修改功能时记录：

- 功能或规则
- 源码预处理条件
- 实际适配版本
- 已完成编译/构建版本
- 尚未适配版本
- 客户端/服务端要求
- 尚未完成的游戏内或客户端验证

“实际适配版本”表示源码包含该版本，“已完成编译/构建版本”只表示对应构建已成功；两者都不能代替游戏内验证

26.3 构建使用 CurseMaven 坐标 `curse.maven:carpet-349239:8894313`（对应 Carpet `26.3+v260915`）与 Fabric Loader `0.19.5`；此前的 `26.3-beta-3+v260820` 会因缺少 `DensityFunction$FunctionContext` 阻止服务端继续启动

## 当前变更

### 1.5.15：握手注册与堆叠修复覆盖全部 `versions/` 源码集

- 功能一（issue #24，`carpet-fga-addition:handshake`）：客户端发送握手时 `Failed to encode packet 'serverbound/minecraft:custom_payload'` 并断开。原因是握手只通过 `CustomPacketPayload.codec` 的 payload 列表注册；只要列表在到达 vanilla codec 前缺少该条目（其它模组从副本重建列表，Carpet 为注册自己的 payload 正是这样做的），vanilla 就会回退到 `DiscardedPayload`，其写入器把 `HandshakePayload` 强转成 `DiscardedPayload` 抛 `ClassCastException`
- 功能一修复：在 mod 初始化阶段把握手同时注册进 Fabric 的 `PayloadTypeRegistry`（`MC >= 1.20.5` 用 `playC2S()`，`MC >= 26.1.2` 用 `serverboundPlay()`）。Fabric 的 payload codec 会先询问已注册频道再走 vanilla 回退，因此该频道不再依赖 payload 列表；未改动 `CustomPacketPayloadMixin` 的列表注入，也未替换 `CustomPacketPayload.codec` 的返回对象（Fabric 会把该对象强转成自己的 codec 子类，替换会破坏 Fabric API）
- 功能二：把背包/容器堆叠上限修复从 `MC >= 1.21.1` 放宽到全部 `versions/` 源码集（`MC >= 1.16.5 && MC <= 26.3`；创造模式槽位校验因原版在更早版本没有该物品级判断而限定 `MC >= 1.20.6`，`getQuickCraftPlaceCount` 同理）
- 版本号：`gradle.properties` 的 `mod_version` 由 `1.5.14` 提升为 `1.5.15`
- 本地工具链：以后统一使用解压版 JDK 21 `D:\java\jdk-21_windows-x64_bin\jdk-21.0.12.1`（`scripts/tests/run-rule-compat.py` 已按该路径优先、并回退到其它候选）
- 源码预处理条件：`InventoryStackLimitTransferMixin` 在 `MC < 1.17` 使用 `(Level, ItemStack)` 描述符、`MC >= 1.20.5` 才捕获 `ItemStack` 参数并使用槽位容量、更早版本按 `snapshot().inventoryLimit()` 计算；`AbstractContainerMenuStackLimitMixin` 的 `doClick`/`canItemQuickReplace` 覆盖 `MC >= 1.16.5`；`AbstractContainerScreenStackLimitMixin` 覆盖 `MC >= 1.16.5`（`MC >= 26.0` 用 `extractSlot`）
- 注入目标核对：对 9 个历史源码集的官方映射 JAR 逐一 `javap` 确认（1.16.5 的 `placeItemBackInInventory(Level, ItemStack)`、`doClick` 6-9 处物品级读取；`handleSetCreativeModeSlot` 在 1.16.5 至 1.20.4 无该判断；`getQuickCraftPlaceCount` 在 1.16.5 至 1.19.4 不存在、1.20.1/1.20.4 存在但无该读取；`Slot.getMaxStackSize(ItemStack)` 自 1.16.5 起均存在）；Fabric 的 `PayloadTypeRegistry.playC2S()` 在 Fabric API 4.x/5.x（1.20.5–1.21.x）存在、6.x（26.x）改名 `serverboundPlay()`，与 `MC >= 26.1.2` 分支一致
- 历史源码集编译核对：用最小 `//#if` 预处理器按各版本渲染 4 个堆叠 Mixin，并用该版本的官方映射 JAR 直接 `javac` —— `1.16.5`、`1.17.1`、`1.18.2`、`1.19.2`、`1.19.4`、`1.20.1`、`1.20.4` 各 3 个文件通过（创造模式槽位 Mixin 被 `MC >= 1.20.6` 正确预处理掉），`1.20.6`、`1.21` 各 4 个文件通过，失败版本 0
- 尚未完成验证：9 个历史源码集不在 `settings.json` 构建矩阵内（`settings.gradle` 只包含矩阵版本），因此无法做 Gradle 构建、打包或服务端冒烟（临时加回 `settings.json` 会在配置阶段因 `project.mcVersion` 未定义失败，`common.gradle` 对 <1.20.5 还要求本机不存在的 JDK 16）；如需发布这些版本，必须先把版本接入 preprocessor 版本图并补齐工具链
- issue #24 对照实验：`handshake-compat` 套件的第一个断言（握手已进入 Fabric play C2S 注册表）具备判别力 —— 临时删掉 `CarpetFGAAddition` 的注册块后同一套件失败并输出 `FGA_HANDSHAKE_COMPAT_FAIL java.lang.AssertionError: handshake registered in Fabric play C2S registry`，恢复注册后同一套件通过；两次运行的服务器日志分别为 `scripts/logs/handshake-compat-1.21.1-20260925-185129-377997`（对照，失败）与 `scripts/logs/handshake-compat-1.21.1-20260925-183954-336944`（修复后，通过）
- issue #24 尚未验证：报错者那套约 200 模组客户端的实际崩溃**没有**在本地复现（要同时满足"Fabric 注册表缺条目"与"payload 列表条目被丢弃"；测试内的列表破坏尝试因注入顺序未生效），也**没有**做真实客户端经网络的登录握手验证；`1.21.1` 是 preprocessor 根节点，其源码按原样编译，因此不能用改 `//#if` 条件的方式构造对照
- 客户端/服务端要求：握手仍是自定义 Payload，但改为同时注册到 Fabric 的 payload 注册表；配置格式、存档数据、规则默认值、权限模型均未改变；未安装 FGA 的客户端仍按原握手要求处理

### 背包/容器堆叠上限生效时的物品消失修复（`droppedItemStackLimit`）

- 功能：修复背包或容器堆叠上限高于物品级上限（原版 64）后，原版菜单、创造模式槽位同步和背包归还逻辑仍按物品级上限计算容量，导致物品消失、重复或服务端死循环的问题；改为按作用域内真实槽位容量计算
- 具体修复点：`Inventory.placeItemBackInInventory` 的归还批量（原先会 `split(0)`/`split(负数)`，造成关闭容器或配方书放置时服务端无限循环甚至堆叠增长）；`ServerGamePacketListenerImpl.handleSetCreativeModeSlot` 的创造模式槽位校验（原先数量超过物品级上限的更新被静默丢弃，客户端仍保留物品，重同步后表现为物品消失）；`AbstractContainerMenu` 的 `doClick`、`canItemQuickReplace`、`getQuickCraftPlaceCount`（携带堆叠、拖拽、双击收集、创造模式复制）；客户端 `AbstractContainerScreen` 的拖拽预览与余量显示
- 源码预处理条件：4 个 Mixin 在 1.5.15 起放宽为 `MC >= 1.16.5 && MC <= 26.3`（创造模式槽位校验与 `getQuickCraftPlaceCount` 为 `MC >= 1.20.6`）；`placeItemBackInInventory` 在 `MC < 1.17` 使用 `(Level, ItemStack)` 描述符、`MC >= 26.3` 使用 `(ItemStack, boolean, Prediction)` 描述符；客户端槽位方法在 `MC >= 26.0` 为 `extractSlot`，此前为 `renderSlot`
- 容量取值：新增 `DroppedItemStackLimitConfig.effectiveMenuCapacity`，返回物品级上限与当前生效的背包/容器上限中的较大值；所有下游放置仍按具体槽位容量收口，地面掉落物上限不参与
- 实际适配版本：当前 `settings.json` 中的 10 个构建版本 `1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2` 和 `26.3`
- 未同步版本：无（1.5.15 起 4 个 Mixin 的条件已覆盖 `versions/` 中全部 19 个源码集；其中 9 个非矩阵版本只有静态注入核对与 javac 编译核对，未做打包与服务端冒烟）
- 已完成编译/构建版本：上述 10 个版本 `compileJava` 全部通过，`1.21.11` 另完成 `:1.21.11:build`（含 `test`、`jar`、`remapJar`）并归档于 `mod-builds/20260923-200507`；`1.21.1` 的 `processResources` 已重新生成并确认 4 个新 Mixin 均已登记
- 注入目标核对：按各版本官方映射 JAR 用 `javap` 逐版本确认目标方法存在且调用点数量符合预期（`doClick` 在 `1.21.1` 至 `26.1.2` 为 6 处、`26.2`/`26.3` 为 5 处；`canItemQuickReplace`、`getQuickCraftPlaceCount` 各 1 处；`placeItemBackInInventory`、`handleSetCreativeModeSlot` 各 1 处）；`1.21.11` 的 refmap 已解析出全部目标中介名
- 已完成服务端冒烟：`scripts/tests/run-rule-compat.py --suite inventory-compat` 在 `1.21.1`、`1.21.11` 和 `26.2` 均通过（`FGA_INVENTORY_COMPAT_PASS checks=46`，含新增的归还批量终止/整栈归还/超出原版上限三项断言），报告为 `scripts/logs/inventory-compat-1.21.1-20260923-193609-824743`、`scripts/logs/inventory-compat-1.21.11-20260923-193332-045740` 和 `scripts/logs/inventory-compat-26.2-20260923-193704-805229`
- 客户端/服务端要求：不新增自定义 Payload、网络协议、配置格式、存档数据、规则默认值或权限变化；服务端仍为最终判定方；客户端 Mixin 只修正拖拽预览，未安装 FGA 的客户端仍按原有握手要求处理
- 尚未完成验证：真实客户端中创造模式取物/复制、拖拽分发、双击收集、关闭容器归还，以及安装第三方堆叠模组（客户端改变物品级上限）时的跨端表现；`extractSlot`（`26.x` 客户端）与客户端拖拽预览 Mixin 只在服务端冒烟之外静态核对，未做真实客户端验证；容器作用域下漏斗仍按物品级上限停止合并（原版 `canMergeItems` 行为，不丢物品，本次未改动）

### 1.5.14：生物掉落物全量开关与夺舍边界规则

- 功能：`/entityDropRemoval list <entity>` 末尾新增“开启此生物所有掉落物”和“关闭此生物所有掉落物”两个点击按钮，并新增持久化 `allDrops` 配置；同时新增 `playerPossessionDistance` 与 `playerPossessionCrossDimension` 两个规则
- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`
- 规则默认值：`playerPossessionDistance=-1`（同维度不限制距离），`playerPossessionCrossDimension=true`（保持旧版跨维度行为）
- 实际适配版本：源码已写入当前矩阵节点；本轮基线先验证 `26.3`
- 已完成编译/构建版本：`26.3` 的 `:26.3:compileJava :26.3:test` 与 `:26.3:build` 均通过，基线归档为 `mod-builds/20260921-185946`
- 已完成服务端冒烟：`scripts/powershell/server-startup-smoke-all.ps1 -VersionList 26.3` 验证 `disableAllDrops`、`enableAllDrops`、`list <entity>`、`playerPossessionDistance` 和 `playerPossessionCrossDimension`，报告为 `scripts/logs/server-startup-smoke-20260921-190002/summary.json`
- 尚未同步构建版本：其余 `1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2` 和 `26.2`，需基线确认后继续
- 客户端/服务端要求：服务端安装 FGA，客户端不需要安装 FGA；配置文件版本保持兼容，旧 `entity-drop-removal.json` 缺少 `allDrops` 时按 `false` 读取；未新增网络协议或客户端 Mixin
- 尚未完成验证：真实客户端点击两个全量掉落按钮、真实多人夺舍距离限制、跨维度限制和规则动态变更的多人表现

### FGA 与 Team/Tab 前缀同步兼容

- 功能：恢复长假人名字网络扩展的线程作用域；仅 FGA 明确发送的长名字玩家信息包使用 128 字符限制，原版 Team 包和其他模组的玩家前缀包继续使用原版编码
- 源码预处理条件：`MC >= 1.19.3` 的玩家信息包处理；`MC >= 1.20.2` 使用 `ModifyArg`，更早版本使用 `ModifyVariable`；`MC >= 1.20.5` 的服务器包发送路径在写包期间启用作用域
- 实际适配版本：源码覆盖当前 `settings.json` 中的 `1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2` 和 `26.3`
- 本轮基线：`26.3`；已加入源码冒烟流程，覆盖 MCDR ZaiGanMa `!!zgm set` 前缀、`bot_` 假人标签、清除/再次设置以及无需重连的客户端观察；当前矩阵 10 个版本均完成构建与服务端启动冒烟
- TAB 前缀刷新补充：源码条件 `MC >= 1.19.4`，覆盖当前 10 个发布节点及 `1.19.4`、`1.20.1`、`1.20.4`、`1.20.6`、`1.21` 历史源码集；未启用 FGA 名称装饰时保留 vanilla 的空 `displayName`，启用血量显示时跟踪队伍名称组件变化并仅发送 `UPDATE_DISPLAY_NAME`；`1.20.1` 和 `1.21.1` 额外保留加载距离名称装饰
- 本次修复已完成构建：当前 `settings.json` 的 10 个发布节点 `1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3` 均通过各自的 `build`（包含现有单元测试）；对应独立归档依次为 `mod-builds/20260925-194509`、`20260925-194650`、`20260925-194831`、`20260925-194952`、`20260925-195149`、`20260925-195321`、`20260925-195451`、`20260925-195642`、`20260925-195822`、`20260925-195938`
- 尚未完成构建的历史源码集：`1.19.4`、`1.20.1`、`1.20.4`、`1.20.6`、`1.21` 不在当前 `settings.json` 构建矩阵内，只有源码条件覆盖，尚未完成 Gradle 构建或游戏内测试；`1.16.5`、`1.17.1`、`1.18.2`、`1.19.2` 不包含此玩家信息包功能
- TAB 修复尚未适配版本：仓库中已包含此玩家信息包功能的源码集无；历史源码集的未构建状态如上
- 回归步骤：健康显示关闭/开启、`nofake`、订阅 `playerHealth` 时，保持玩家不重连且血量不变，修改队伍前缀、后缀、颜色、成员并移除队伍；确认假人和真实玩家 TAB 名称即时更新
- 客户端/服务端要求：保持现有要求；未新增自定义 Payload、配置格式、存档数据或权限变化；FGA 长名字兼容仍按原有客户端安装/别名路径处理
- 尚未完成验证：真实 MCDR/ZaiGanMa 客户端冒烟及跨版本真实客户端行为

### 满潜影盒合成规则门控与数量修复（`fullShulkerBoxCrafting`）

- 功能：修复集成服务器把物理客户端环境误当成逻辑客户端、导致规则为 `false` 时仍可满盒合成的问题；修复普通工作台混合材料配方把所有输入盒数量除以第一种材料槽数、导致活塞等配方倍增产出的问题
- 修复源码条件：已同步到当前构建矩阵 `MC >= 1.21 && MC <= 26.3`；规则原有实现范围仍为 `MC >= 1.16.5 && MC <= 26.3`
- 数量行为：每个已占用配方槽提供相同数量材料时，批量合成次数就是单个输入盒的材料总数，不再乘以配方占用槽数；满盒活塞配方应只产出一盒活塞
- 源码测试：`FullShulkerCraftingPolicyTest` 覆盖逻辑服务端关闭规则、客户端宽松预览、`only64` 服务端判断、活塞一盒产出和多产物配方倍率；`FeatureSmokeTestPlanTest` 记录集成服务器规则关闭与活塞数量的客户端操作流程
- 已完成编译/测试版本：当前矩阵 10 个版本的 `buildAllVersions --no-daemon --configure-on-demand --max-workers=1` 均通过，构建脚本归档为 `build/libs/20260922-191913`；可安装 JAR 分别归档于 `mod-builds/20260922-194130`、`20260922-194132`、`20260922-194133`、`20260922-194134`、`20260922-194135`、`20260922-194137`、`20260922-194138`、`20260922-194139`、`20260922-194141` 和 `20260922-194142`
- 已完成服务端启动冒烟：10/10 版本均启动到 `Done`，依次接受 `false`、`any`、`only64`、`false` 规则切换并干净停服；1.21.1 至 26.2 的报告为 `scripts/logs/server-startup-smoke-20260922-192122/summary.json`，26.3 补测报告为 `scripts/logs/server-startup-smoke-20260922-193739/summary.json`
- 当前构建矩阵同步版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2` 和 `26.3`
- 客户端/服务端要求：未新增网络协议、配置格式、存档数据、规则默认值或权限变化；服务端继续作为最终合成判定方
- 尚未完成验证：真实客户端中规则关闭后的工作台结果、满盒活塞实际取出数量、快速合成/丢出路径及跨版本 GUI 行为

### 生物掉落物列表点击删除资源 ID（`entityDropRemoval`）

- 功能：修复 `/entityDropRemoval list` 与 `/entityDropRemoval list <entity>` 中点击物品或 `allEquipment` 删除按钮时，含命名空间的 ID 被错误解析为普通字符串、最终在 `allEquipment` 处报“参数后应有空格”的问题
- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`
- 实际适配版本：当前 `settings.json` 中的 10 个构建版本 `1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2` 和 `26.3`
- 已完成编译/构建版本：当前 `settings.json` 中的 10 个构建节点全部通过 `buildAllVersions --configure-on-demand`，并分别归档于 `mod-builds/20260921-180734`、`mod-builds/20260921-180734-2`、`mod-builds/20260921-180735`、`mod-builds/20260921-180735-2`、`mod-builds/20260921-180735-3`、`mod-builds/20260921-180735-4`、`mod-builds/20260921-180736`、`mod-builds/20260921-180736-2`、`mod-builds/20260921-180736-3` 和 `mod-builds/20260921-180736-4`
- 尚未适配版本：无（当前构建矩阵内）
- 客户端/服务端要求：服务端安装 FGA，客户端不需要安装 FGA；仅修复命令参数解析，不改变配置格式、存档数据、网络协议、规则默认值或权限模型
- 已完成服务端冒烟：`scripts/powershell/server-startup-smoke-all.ps1` 对上述 10 个版本执行规则开启、命名空间实体/物品 `set`、`list`、`list <entity>`、`allEquipment` 删除、物品删除和 `status`，10/10 启动到 `Done` 并干净停服；报告为 `scripts/logs/server-startup-smoke-20260921-180745/summary.json`
- 尚未完成验证：真实客户端中逐一点击物品删除、装备删除和 `/fga entityDropRemoval` 重定向入口；本次冒烟通过服务端命令序列覆盖了点击按钮所调用的同一删除解析路径

### 高版本夺舍命令树刷新（`playerPossession` / `/player ... possess`）

- 功能：修复高版本客户端首次进入服务器后没有收到最新夺舍命令树、必须重进才显示 `/player <名字> possess` 或 `/controlPlayer` 的问题；相关 Carpet 规则变化时直接重发命令树，玩家 JOIN 完成后再排队重发一次，避免被原版首次命令树覆盖
- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述全部 10 个版本；最终 `buildAllVersions` 构建结果以 1.5.13 发布构建为准
- 尚未适配版本：无（当前 `settings.json` 构建矩阵内）
- 客户端/服务端要求：服务端安装 FGA，客户端不需要安装 FGA；只重发原版命令树数据包，不新增自定义 Payload、网络协议、配置格式、存档数据、规则默认值或权限模型
- 已完成客户端冒烟：`scripts/powershell/player-possession-command-refresh-smoke-26.2.ps1`；在无重连的首次登录后执行 `/player FGARefreshTarget possess` 和 `/controlPlayer` 均通过，报告为 `scripts/logs/player-possession-command-refresh-smoke-26.2-20260921-094411/summary.json`
- 已完成客户端冒烟：26.2 首次登录后无需重连即可使用夺舍命令；1.21.1、1.21.3、1.21.4、1.21.5、1.21.8、1.21.11 的首次登录命令树验证通过；1.21.10 测试被外部 Fabric API 的 `fabric-particles-v1` `BlockDustParticleMixin` 注入崩溃阻断，未归因于 FGA
- 尚未完成验证：`1.21.10` 外部依赖修复后的客户端冒烟、`1.21.1` 至 `26.3` 全部版本的真实多人连接，以及规则动态切换后的各客户端命令树刷新

### 多人游戏玩家无上限（`unlimitedMultiplayerPlayers`）

- 功能：允许多人游戏服务器接纳超过 `server.properties` 的 `max-players`；当 GCA 已加载且 `fakePlayerResident` 为 `true` 时自动生效
- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`；`1.21.1` 至 `1.21.8` 注入 `canPlayerLogin(SocketAddress, GameProfile)` 的 `maxPlayers` 检查，`1.21.10` 至 `26.3` 注入 `canPlayerLogin(SocketAddress, NameAndId)` 的 `getMaxPlayers()` 检查
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述全部 10 个版本；对应 `:<版本>:compileJava` 和 `:<版本>:build` 均通过，最终产物分别归档于 `mod-builds/20260920-135809`、`mod-builds/20260920-135835`、`mod-builds/20260920-135859`、`mod-builds/20260920-135928`、`mod-builds/20260920-140000`、`mod-builds/20260920-140027`、`mod-builds/20260920-140309`、`mod-builds/20260920-140338`、`mod-builds/20260920-140400`、`mod-builds/20260920-140430`
- 尚未适配版本：无（当前 `settings.json` 构建矩阵内）
- 客户端/服务端要求：服务端安装 FGA，客户端不需要安装 FGA；自动检测 GCA 时服务端还需要安装 GCA，未增加自定义 Payload、客户端 Mixin 或网络协议变化，也未改变配置格式、存档数据、规则默认值或权限模型
- 行为范围：只跳过原版 `server_full` 玩家数量检查，保留封禁、白名单和 IP 封禁检查；`max-players` 的服务端配置值仍保留
- 已完成跨版本服务端启动冒烟：`scripts/powershell/server-startup-smoke-all.ps1` 对上述 10 个版本均启动到 `Done` 并正常停服，报告为 `scripts/logs/server-startup-smoke-20260920-140805/summary.json`
- 已完成服务端/客户端网络冒烟：`scripts/powershell/unlimited-multiplayer-players-network-smoke-1.21.1.ps1` 在 `max-players=8` 的独立服务器中，以 FGA 规则保持 `false`、GCA `fakePlayerResident=true` 为条件召唤 20 个 GCA 假人；20 个假人首次全部加入，服务器与客户端重启后 20 个假人仍全部存在并可重新进入，报告为 `scripts/logs/unlimited-multiplayer-players-network-smoke-20260920-134503/summary.json`
- 尚未完成验证：其他 9 个版本未逐版本重复客户端游戏内冒烟；未在真实多客户端条件下单独验证超过 `max-players` 的多个真实玩家加入，以及客户端玩家列表/服务器列表显示

### 手持地图立即加载命令（`mapLoad`）

- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述全部 10 个构建版本；本次最终产物分别归档于 `mod-builds/20260919-223315` 至 `mod-builds/20260919-223744`
- 尚未适配版本：无
- 客户端/服务端要求：服务端安装 FGA，客户端不需要安装 FGA；玩家可见消息在服务端解析成纯文本后发送（客户端没装 FGA 也不会显示翻译键），语言跟随服务器 JVM 语言环境；使用 `/mapLoad` 或 `/fga mapLoad`，权限由 `mapLoadCommandPermission` 控制，默认 `ops`，加载方式由命令参数选择（`/mapLoad start smooth|fast|loaded`，默认 `smooth`），不额外注册规则，mapLoad 相关规则只有 `mapLoadCommandPermission` 一个
- 行为范围：异步流式加载，采样点按到玩家的距离由近到远排列，主手没有地图时使用副手；加载开始就按缩放等级算出每个采样点需要的区块矩形（已按原版 `(center / scale)` 取整方式换算，覆盖 `MapItem.update` 的列窗口与 `checkBanners` 的告示牌读取），用 `TicketType` 票据把区块交给原版区块流水线，票据层级为 `ChunkLevel.byStatus(FULL)`，只加载不 ticking（不刷怪、不随机刻、不方块刻），加载方式由 `/mapLoad start <模式>` 参数给出：`smooth`（默认）每任务在途上限 384 个区块、`fast` 为 1024（每 tick 新发票据数不再作为节流手段：真正影响卡顿的是同时在途数量，已经生成过的区域因此可以直接跑到区块流水线的速度，避免误把"已探索区域"也拖慢），两者都大于单采样点最多约 289 个区块的窗口，否则当前采样点永远凑不齐，票据按任务计以避免并发加载互相饿住；`loaded` 完全不申请票据，只渲染已加载的区块，其余采样点按未加载跳过并在结束时报告，票据随游标推进释放，完成、终止、暂停、玩家退出、切维度、停服时全部释放；渲染前用非阻塞的 `getChunkNow` 逐个确认所需区块已就绪，未就绪就留到下个 tick，因此服务端线程不会再等区块生成（旧实现每次遇到未加载区块都会在 `mainThreadProcessor.managedBlock` 上同步等待）；采样步长取 `min(窗口半径, round(1.414 × (窗口半径 - 2)))`——原版每个采样点只保证写出距中心 `半径 - 2` 内的像素，再外面一圈按 `(o + p)` 奇偶门控，因此相邻采样点必须近于 `√2 × (半径 - 2)`，该公式已用原版写入条件的模拟程序对缩放 0-4 与有/无天花板全部 10 种组合验证为 0 像素缺失（整形步长 `半径` 在下界缩放 4 会漏 1024 像素的棋盘空洞，正是本次修复项）；带天花板维度按原版把半径减半；所有任务共享每 tick 3 毫秒预算并按任务均分（单任务下限 0.5 毫秒，预算内至少完成 1 次列渲染以保证推进），单 tick 峰值只相当于一个采样点的约 1/16；锁定地图、无效地图和跨维度地图保持不变；执行期间玩家本体位置不会被改变；像素仍由原版 `MapItem.update` 计算，不复制原版取色逻辑，也不在离线程读取区块数据——每个采样点需要 16 次调用才能覆盖原版 `step & 15` 的 16 个列残差类，这 16 次按预算分散到多个 tick，每次调用前把 `MapItemSavedData.HoldingPlayer.step` 钉到本次负责的列残差类（原版手持地图每 tick 也会推进 step，否则会被打乱成只剩一半列）
- 已修复：票据释放原先按"前瞻样本的外接矩形"判断，而采样点按到玩家的距离排序（环形），几个样本的外接矩形可以横跨整张图，导致"任一时刻只保留少量票据"失效、在途上限被吃满、游标样本缺的区块再也拿不到票据而永久卡住（实测：下界缩放 3 地图在 `smooth` 下停在 22%、等待区块 60/90 两分钟不动，同样条件下 `fast` 因为上限更大能够跑完）。现在改为按前瞻样本的**精确区块集合**释放，并加了兜底：某个采样点连续 30 秒没有任何推进就整体释放票据重新申请，避免任何未知情况把任务挂死
- 实测（本机服务器、1.21.1、假人持图）：修复后同一张下界缩放 3 地图（4096 区块）`smooth` 从 56% 稳定推进到完成约 60 秒，等待区块计数持续变化；缩放 2 地图（1024 区块、地形已生成）不到 2 秒完成
- 控制命令可从控制台执行：`pause`/`resume`/`stop` 指定玩家名时不再要求命令源是玩家（控制他人仍需 OP 2），不指定玩家名时控制台会提示需要玩家或玩家名
- 已修复（严重）：票据回收原本是一次性批量撤掉，会把**仍在生成中的区块**的票据一起撤销。这类区块的生成任务因此无法收尾，滞留在原版卸载队列里每 tick 被重试：表现是服务端线程持续满负荷、不再写盘，停服也卡住（实测一次压力加载后服务端空转 12 分钟、200% CPU、region 文件零写入，线程转储停在 `ChunkMap.processUnloads/scheduleUnload` 的 future 回调里）。现在：①回收限速为每 tick 32 个票据；②**只回收已经到达 FULL 的区块**，仍在生成的区块继续持有票据直到生成完成；③完成/终止/暂停的任务进入滴流回收队列而不是立刻清空；④停服时不再批量回收（交给原版自己保存与卸载）。实测同样量级的加载后停服 <15 秒正常退出
- 已修复（崩溃）：结束消息的参数个数与文案不匹配（`finish()` 仍按旧版 1 个参数调用，文案已改为 4 个 `%s`），触发 `MissingFormatArgumentException` 并逃出 tick 循环导致服务端崩溃（16:51 崩溃报告）。已修正，并把 `report()`/`finish()` 一并纳入异常兜底：消息构造再出问题也只会结束该任务，不会影响服务端；另加了一个脚本检查全部 `text("key", ...)` 调用与文案 `%s` 个数是否一致（当前 0 不匹配）。实测裸 `/mapLoad` 可正常开始并结束加载，服务端无新崩溃
- 命令写法收紧：只允许 `/mapLoad start <smooth|fast|loaded>`（裸 `/mapLoad` 与 `/mapLoad <模式>` 只提示用法），模式必填；结束消息附带耗时（不足 1 分钟显示秒，否则显示分秒）
- 任务必定结束：单个采样点连续 30 秒没有任何区块进展（期间区块计数不动）才会被跳过并计入跳过数量；只"慢但在推进"不会跳。票据交还限速 32/tick，已完成（FULL）或持有超过 10 秒仍未完成的区块才会被交还，避免卡住生成中的区块
- 规则收缩：不再注册 `mapLoadChunkLimit` 与 `mapLoadMode`，加载方式改为 `/mapLoad start <smooth|fast|loaded>` 命令参数（Tab 补全，无效值会被拒绝并提示可选值），权限仍由 `mapLoadCommandPermission` 控制；曾被旧版写入过这两个规则名的 `carpet.conf` 会被 Carpet 当作未知规则忽略并在日志里报一行，测试服务器上的残留行已清理
- 命令行为：开始与结束各发一条聊天消息（开始消息带本次刷新的坐标范围、该地图覆盖的区块数量与本次模式，结束只报告已结束），快捷栏上方的提示同时给出已刷新采样点百分比与当前窗口已就绪/需要的区块数（如 `地图加载中 12%（等待区块 210/289）`），进度消息按 5 tick 节流、等待提示按 10 tick 节流、加载超过 30 秒后每 30 秒在聊天栏报一次进度（避免只有快捷栏被误认为卡死）；快捷栏与聊天显示的是**整张地图的区块进度**（已加载过 / 地图覆盖总数，与开始消息同源，且已加载数按"曾经加载过"累计、只增不减），不再是会随采样点切换而跳动的单点窗口计数，`mapLoad list` 里只保留 加载中/等待区块 状态词，被跳过的采样点数量在结束时额外报告（只有 `loaded` 模式会跳过）；`/mapLoad list` 每行一个任务（玩家名、百分比、状态：加载中/等待区块/已暂停），行尾 `[暂停]` 或 `[继续]` 与 `[终止]` 按钮用 `RUN_COMMAND` 点击事件直接执行对应命令；`pause`、`resume`、`stop` 不带玩家名时作用于自己，带玩家名时要求 OP 2，玩家名 Tab 补全只列出有任务的在线玩家；暂停会释放票据并停止申请新票据，继续时重新申请；终止后可从 `list` 中消失并可立即重新发起
- 已完成服务端冒烟验证：独立 Minecraft 1.21 运行时启动到 `Done`，`/mapLoad` 与 `/fga mapLoad` 均已注册；控制台执行 `/fga mapLoad` 正确进入玩家来源校验；新构建在真实服务器（Minecraft 1.21.1 + Fabric，27 个 mod）启动到 `Done`，mod 列表显示 `carpet-fga-addition 1.5.11+v2609181409`，启动日志报错与改造前逐条一致（无新增）；RCON 验证控制台 `mapLoad` 返回中文前缀「加载地图失败：…」、`mapLoad list` 返回「当前没有正在进行的加载任务」、`fga` 帮助列出三行 mapLoad 说明、`mapLoad` 已注册且只保留 `mapLoadCommandPermission` 一个规则
- 尚未完成验证：真实客户端地图图像刷新与各缩放等级的视觉完整性（含下界缩放 4 的棋盘空洞修复效果）、异步流式是否确实消除卡顿、`smooth`/`fast`/`loaded` 三种命令参数模式的实际手感与耗时差异、快捷栏区块计数是否随生成持续推进、`list` 按钮点击与暂停/继续/终止、服务端解析的消息在未安装 FGA 的客户端上的显示、地图告示牌装饰、多玩家并发加载、锁定地图拒绝、跨维度拒绝
- 当前构建归档：本次适配的 10 个版本分别归档于 `mod-builds/20260919-223315`、`20260919-223347`、`20260919-223413`、`20260919-223447`、`20260919-223515`、`20260919-223547`、`20260919-223618`、`20260919-223645`、`20260919-223710`、`20260919-223744`


### 灵魂疾行不消耗耐久（`soulSpeedNoDurability`）

- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述全部 10 个构建版本
- 尚未适配版本：无
- 客户端/服务端要求：纯服务端规则，客户端不需要安装 FGA
- 行为范围：开启后移除灵魂疾行在兼容方块上移动时产生的靴子耐久损耗，移动速度和其他灵魂疾行效果保持原版
- 兼容修复：`EnchantmentDurabilityMixin` 与 `DamageItemSoulSpeedMixin` 原先只在 `MC == 1.21.1` 的 Mixin 配置分支中注册，已扩展到 `MC >= 1.21 && MC <= 26.3`，覆盖高版本的实际注入路径
- 已完成服务端冒烟验证：16/16 支持子版本均启动到 `Done`、规则切换成功并干净退出；日志未发现上述两个 Mixin 的注入失败，报告为 `scripts/logs/server-startup-smoke-subversions-20260920-181525` 与 `scripts/logs/server-startup-smoke-subversions-20260920-182904`
- 已完成服务端假人行走实测：`1.21.1` 隔离世界中装备附魔耐久值为 100 的灵魂疾行 III 钻石靴，假人在灵魂沙上实际移动 137.19 米，结束后耐久仍为 100；报告为 `scripts/logs/fake-player-stonecutter-soul-speed-20260920-192748`
- 尚未完成验证：真实客户端移动视觉效果、规则动态切换和与耐久、经验修补的组合行为
- 当前基线构建归档：`mod-builds/20260920-193038`

### 荆棘不消耗耐久（`thornsNoDurability`）

- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述全部 10 个构建版本
- 尚未适配版本：无
- 客户端/服务端要求：纯服务端规则，客户端不需要安装 FGA
- 行为范围：开启后移除荆棘触发反伤时产生的护甲耐久损耗，反伤和触发概率保持原版
- 已完成服务端冒烟验证：规则注册、规则描述和 Mixin 加载检查通过，服务端启动到 `Done`
- 尚未完成验证：真实客户端受击、实际护甲耐久变化、规则动态切换和多件荆棘护甲同时触发行为
- 当前构建归档：见本次适配的 10 个版本归档目录 `mod-builds/20260919-223315` 至 `mod-builds/20260919-223744`

### 工具火焰附加熔炼（`fireAspectOnTools`）

- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述全部 10 个构建版本
- 尚未适配版本：无
- 客户端/服务端要求：纯服务端规则，客户端不需要安装 FGA
- 行为范围：允许火焰附加附魔在镐、斧、锹和锄等挖掘工具上，并与精准采集冲突；工具上的火焰附加不会由附魔台提供；方块掉落先完成原版时运计算，火焰附加 I 对每个掉落执行一次烧炼，火焰附加 II 连续执行两次，每次按当前熔炉配方处理，不可烧制或无法继续烧炼的掉落物保持上一次结果
- 已完成服务端冒烟验证：`scripts/powershell/fire-aspect-tool-smoke-all.ps1` 在上述 10 个版本中均通过；每个版本均记录 Fire Aspect I、Fire Aspect II、Fire Aspect+Fortune 镐子的附魔组件、挖掘、熔炼结果、干净停服和无 Mixin 注入失败，最终报告为 `scripts/logs/fire-aspect-tool-smoke-all-20260921-123320`
- 测试流程已写入 `FireAspectToolManager` 源码注释和 `FeatureSmokeTestPlanTest`；升级时可用 `-VersionList <版本[,版本...]>` 执行指定节点，不带参数时执行完整矩阵
- 26.1+ 的 `getDrops` 工具参数按版本使用 `ItemInstance` 描述符并转换为 FGA 的 `ItemStack` 处理路径，26.1 以下保留 `ItemStack` 描述符
- 尚未完成验证：真实客户端附魔台界面、实际客户端挖掘动画、规则动态切换和非测试存档中的不同熔炉配方组合；本次服务端三种镐子场景已完成
- 当前构建归档：`build/libs/20260921-125320`

### 命名末影珍珠传送玩家（`namedEnderPearlTeleport`）

- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`
- Mixin 注册分支：`MC >= 1.21 && MC <= 26.3`，覆盖当前全部十个构建节点
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述十个构建节点的 `buildAllVersions --no-daemon --configure-on-demand --max-workers=1 --rerun-tasks` 均通过，最新构建归档位于 `build/libs/20260921-081905`
- 尚未适配版本：无
- 客户端/服务端要求：纯服务端规则，客户端不需要安装 FGA
- 行为范围：规则开启且末影珍珠带有自定义名称时，按名称精确匹配在线玩家并将传送目标改为该玩家；没有匹配玩家时该命名末影珍珠不执行传送；规则关闭或珍珠未命名时保持原版传送行为
- 已完成全版本服务端行为冒烟：假人 `2` 投掷命名为 `1` 的末影珍珠使假人 `1` 移动到命中位置；假人 `1` 下线后，假人 `2` 再次投掷命名为 `1` 的末影珍珠仍保持原位，确认无匹配假人时无人传送；十个版本均无 Mixin 失败并干净退出，10/10 通过，报告为 `scripts/logs/named-ender-pearl-teleport-smoke-all-20260921-080335`
- 测试流程已写入 `NamedEnderPearlTeleport` 源码注释和 `FeatureSmokeTestPlanTest`，升级时可用 `-VersionList <版本[,版本...]>` 调用单个或多个节点，不带参数时执行完整矩阵
- 尚未完成验证：真实客户端投掷、跨维度目标、规则动态切换行为
- 最新十个版本构建归档：`mod-builds/20260921-082000` 至 `mod-builds/20260921-082009`

### 骨粉最大效率（`boneMealMaxEfficiency`）

- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述全部 10 个构建版本
- 尚未适配版本：无
- 客户端/服务端要求：纯服务端规则，客户端不需要安装 FGA
- 行为范围：成功骨粉操作中的作物成长阶段、竹子和下界藤蔓高度、海泡菜数量取当前条件下的最大值；树苗和杜鹃树一次骨粉直接尝试生成，生成特征选择、随机位置和空间检查保持原版，无法生成时使用有限次重试并保留原方块
- 尚未完成验证：真实客户端各植物骨粉结果、规则动态切换和多人客户端行为
- 当前构建归档：见本次适配的 10 个版本归档目录 `mod-builds/20260919-223315` 至 `20260919-223744`

### 光源方块切石配方（`lightSourceStonecuttingRecipes`）

- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述全部 10 个构建版本；全版本构建归档为 `build/libs/20260920-202704`
- 尚未适配版本：无（当前 `settings.json` 构建矩阵内）
- 配方资源格式：`1.21.1` 使用旧版对象形式的 `ingredient`，`1.21.2+` 使用物品 ID / `#tag` 字符串形式；共享构建脚本按版本生成对应格式
- 客户端/服务端要求：服务端可用，客户端不需要安装 FGA
- 配方行为：列出的发光方块通过物品标签转换为亮度等级15至1的 `minecraft:light`，每次产出4个；15个亮度等级的光源方块组件变体之间可一比一转换
- 高版本铜光源：源码条件为 `MC >= 1.21.9 && MC <= 26.3`，额外加入 `copper_torch`、4 种氧化铜灯笼和 4 种涂蜡铜灯笼；铜墙火把没有独立物品输入，不加入物品标签
- 已完成子版本服务端静默验证：16/16（`1.21.1` 至 `1.21.11`、`26.1`、`26.1.1`、`26.1.2`、`26.2`、`26.3`）均启动到 `Done`、规则切换成功并干净退出；报告为 `scripts/logs/server-startup-smoke-subversions-20260920-204526`
- 已完成全构建节点假人服务端实测：10/10（`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`）假人成功加入并对切石机执行交互，`carpet-fga-addition:light_level_01_15_from_light_source_stonecutting` 均成功注册；报告为 `scripts/logs/fake-player-stonecutter-recipe-smoke-20260920-203104`
- 本次铜光源增补基线：`26.2` 构建和 JAR 标签检查通过，9 个铜光源物品均已打包；假人服务端冒烟通过，报告为 `scripts/logs/fake-player-stonecutter-recipe-smoke-20260920-212215`，构建归档为 `mod-builds/20260920-212212`
- 本次铜光源增补已随 1.5.13 的 `buildAllVersions` 覆盖全部 10 个构建节点；铜光源物品的独立假人交互回归目前只有 `26.2` 基线通过，`1.21.10`、`1.21.11`、`26.1.2`、`26.3` 待补充服务端交互测试
- 尚未完成验证：真实客户端切石机界面、配方书刷新和实际切石操作
- 当前构建产物：`build/libs/20260921-125320`
- 早期更正前构建不建议使用

### 光源方块可破坏（`lightBlockBreakable`）

- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述全部 10 个构建版本
- 尚未适配版本：无
- 客户端/服务端要求：服务端规则，客户端需要安装 FGA 才能发起生存破坏
- 规则行为：`false` 保持原版不可破坏，`true` 与兼容别名 `onlyholding` 均仅允许主手手持光源方块的生存玩家破坏；创造和旁观模式保持原版行为
- 尚未完成验证：真实客户端生存模式破坏进度、主手条件和掉落行为
- 当前构建归档：见本次适配的 10 个版本归档目录 `mod-builds/20260919-223315` 至 `20260919-223744`

### 附魔金胡萝卜（`enchantedGoldenCarrot`）

- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述全部 10 个构建版本，`buildAllVersions` 已通过
- 尚未适配版本：无
- 客户端/服务端要求：服务端可用，客户端不需要安装 FGA
- 资源格式：`1.21.1` 使用对象原料，`1.21.2+` 使用字符串原料，避免现代版本配方解析失败
- 工作台 Mixin 参数：`1.21.1` 使用 `Level`，`1.21.3+` 使用 `ServerLevel`，避免工作台注入描述符不匹配
- 尚未完成验证：真实客户端配方书在规则动态切换后的显示
- 1.5.11 修复：`1.21.6-1.21.8` 工作台注入使用 `ServerLevel`，不再触发客户端启动阶段的参数描述符错误

### 自定义假人预设名（`fakePlayerNamePresets`）

- 源码预处理条件：`MC >= 1.21`，规则注册条件为 `Minecraft1_21_1OrNewerCondition`
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述全部 10 个构建版本，`buildAllVersions` 已通过
- 尚未适配版本：无
- 客户端/服务端要求：服务端功能，客户端不需要安装 FGA
- 尚未完成验证：其他版本的 `/player` 补全和自定义列表校验

### 猪灵交易物品自定义（`piglinBarterItemExclusions`）

- 源码预处理条件：交易配置管理器、编辑命令和自定义抽取逻辑为 `MC >= 1.21 && MC <= 26.3`
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述全部 10 个构建版本，`buildAllVersions` 已通过
- 尚未适配版本：无
- 客户端/服务端要求：服务端功能，客户端不需要安装 FGA
- 配置文件：`world/config/carpetfgaaddition/piglin-barter-customization.json`
- 尚未完成验证：真实客户端语言显示、数据包修改战利品表、概率轮空和多人连续交易
- 构建归档：`mod-builds/20260916-200558` 至 `mod-builds/20260916-200558-4`、`mod-builds/20260916-200558` 至 `mod-builds/20260916-200558`，以及 1.5.11 的 `mod-builds/20260916-200558` 至 `mod-builds/20260916-200558`，每个目录包含对应 JAR 与 `build-manifest.json`

### 食物清空命令权限（`foodCommandPermission`）

- 源码预处理条件：`MC >= 1.21 && MC <= 26.3`
- 实际适配版本：`1.21.1`、`1.21.3`、`1.21.4`、`1.21.5`、`1.21.8`、`1.21.10`、`1.21.11`、`26.1.2`、`26.2`、`26.3`
- 已完成编译/构建版本：上述全部 10 个构建版本，分别归档于 `mod-builds/20260922-211158`、`20260922-211159`、`20260922-211159-2`、`20260922-211159-3`、`20260922-211159-4`、`20260922-211159-5`、`20260922-211159-6`、`20260922-211200`、`20260922-211200-2` 和 `20260922-211200-3`
- 尚未适配版本：无（当前 `settings.json` 构建矩阵内）
- 客户端/服务端要求：纯服务端命令，客户端不需要安装 FGA
- 规则行为：`false` 关闭命令；`true` 允许所有来源清空自己或指定玩家；`onlyself` 允许非 OP 只清空自己、OP 指定其他玩家；`ops` 需要 OP 2 及以上；`0-4` 设置最低权限等级；默认值为 `ops`
- 已完成代码验证：`PlayerFoodCommandPolicyTest` 4/4 通过；全版本服务端启动、规则值切换和干净停服通过，报告为 `scripts/logs/server-startup-smoke-20260922-211208/summary.json`
- 尚未完成验证：真实客户端命令树显示、非 OP 玩家和 OP 玩家在游戏内的实际 `/food clear` 目标限制

项目中原有的单版本功能仍遵循各自源码门控，例如 `playerLoadDistance`、旧版矿车与展示框功能、`fullShulkerBoxCrafting` 和 `villagerUpgradeWhileTrading`，不应按本次变更的全版本适配范围理解
