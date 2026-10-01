# 假人库存 / 轻松放置补货 API v1

实现版本：Minecraft 26.3，服务端安装本次 FGA 构建
这是一组 Fabric PLAY 阶段 CustomPayload，不是 HTTP 取物接口
客户端可以只实现这个协议，不必安装 FGA；FGA 不包含投影客户端的轻松放置 UI

## 行为

- 客户端主动查询才发送库存包，不登录推送、不定时广播
- 按物品 ID 自动寻找分类库存，取出的物品进入发请求玩家自己的主背包
- 材料查询除已登记缓存外，按当前中文/英文/自定义名称及前后缀补查散货、`_box` 和杂盒库存；基础假人或中间编号不存在也会查找实际存在的 `_1` 至 `_999`
- 只有实际存在、身份校验通过且完整快照有效的来源才补入映射；保留已有路线，不覆盖命名配置变更前的缓存，映射在正常停服时按原格式保存
- 服务端按实际物品堆叠上限计算半组：64 → 32，16 → 8，不堆叠物品 → 1
- 本版堆叠上限计算最多按64处理，单次最多32；不使用容器额外扩展的超大堆叠上限
- 单次从一名库存假人取同组件材料，可合并其主背包/副手/潜影盒内多个槽位
- 潜影盒内取出散货，空盒保留；不拆背包中的非潜影盒容器，不递归拆嵌套容器
- 必须够半组，不足时查下一名假人；本版不跨多名假人拼凑半组，也不部分扣货
- 原 v1 频道不接受客户端指定数量；新增数量扩展仅接受有界整数，不接受 NBT、ItemStack、位置、维度或接收者
- 普通模式离线库存取物会按需上线假人；只关闭 API 本次召唤的假人，不关闭原本在线的假人；完成上线及本次关闭时清理待上线标记，下一次取货可立即重新召唤
- 临时库存假人以旁观模式上线，避免摔落、窒息和拾取掉落物；离线档案含 RootVehicle 时拒绝自动上线，避免搬移载具
- 普通模式转移通过主线程在线背包操作完成；静默扩展可直接更新已验证的离线来源，见下文
- 网页开关不影响这个主动游戏 API，原 HTTP 接口仍只读

## 可选指定数量接口（26.3）

halfmasa 的“扩展功能”栏分别提供轻松放置和打印机自动补货，包含允许假人取货、数量和静默子选项。自定义数量使用以下两个可选 C2S 频道（版本仍为1）：

| 频道 | DTO | 按顺序编码的字段 |
| --- | --- | --- |
| `playersort/query_amount` | AmountQuery | version VarInt、requestId Long、itemId UTF256、cursor VarInt、amount VarInt |
| `playersort/take_amount` | AmountTake | version VarInt、requestId Long、token UTF64、itemId UTF256、amount VarInt、silent Boolean |

`amount` 接受0–2304：0沿用服务端半组规则，1–2304为物品个数，越界返回 INVALID_REQUEST。空 token 直接选择无前后缀中文假人名，非空 token 选择原查询来源；silent 控制是否直接处理离线档案。查询寻找单个来源中同组件足量库存，分页必须保持原物品及数量，成功返回实际 takeCount；取货单次交付指定数量，来源或玩家容量不足时不部分扣货、不跨不同假人拼单。响应仍为原 `material` / `take` 格式。

复用原权限、请求编号缓存、防重放、限速、身份和库存锁；请求结果的等同性包括数量及静默标志。原 query/take/direct_take/silent_take 的编解码与 Java DTO 构造器均保持兼容，旧客户端继续取半组。客户端只在服务端广告两个新频道时使用数量扩展；旧服仅能使用0（半组）或与本地材料半组相等的值，其他值暂停并提示升级。

人工验收新增场景：轻松放置和打印机分别设1、32、64、128及0，核对回包数量和玩家/来源库存差额；只有16个材料时数量1可取、32拒绝；背包满时无扣货；两功能同时缺料只串行提交；同编号重试不重复扣货；普通/静默、后位回退及授权撤销分别检查。本次未启动游戏或运行自动测试。

## 首位库存自动补货与空盒回收（26.3）

- 材料查询先查首位，再从最大的已有编号后位向前查（`_999` → `_1`）；首位没有足量材料或不存在时可从末位取货，允许稀疏编号。
- 开启假人物品分类规则后，服务端轮询已登记的普通材料首位；API 查询/取物发现的当前命名首位也会加入检查。首位主背包同物品 ID 的散货低于 **1152** 时，从后位主背包、副手及潜影盒中提取材料，优先编号最大的后位，补入首位 **9–35 槽**。普通 64 堆叠物品的散货区容量为 **1728**，受实际空槽、组件和堆叠上限限制。
- 自动补货不召唤假人。既有在线假人使用在线背包；离线档案必须通过库存 API 的身份校验。缺失的首位档案不会自动创建，材料 API 仍能直接查找末位。
- 后位被取空的潜影盒送给名字精确为 **`潜影盒`** 的假人，与合成补给站 `潜影盒补货` 分开。保留盒子颜色、名称及组件；有其他内容的盒子留在后位。
- 接收假人缺失、不合格、忙碌或没有空间时，空盒留在后位，材料仍可补入首位。待回收的盒子定期重试，单首位回收尝试间隔至少 10 秒；回收不要求首位仍低于 1152。
- 每秒最多启动一个首位任务，每轮最多读取 4 名后位和 1 名空盒接收者；优先处理查询/取物涉及的首位。扫描最多 65536 个档案文件名，每轮最多尝试预留 32 个后位；无材料或无效档案按文件版本缓存，文件变化后重新检查。内存队列最多记录 256 个首位，每个首位最多已有的 999 个后位回收名称；待回收状态不持久化。
- 与取物、分类离线写入和 Carpet 召唤共享库存锁；主线程提交前复核文件版本/在线背包。离线原文件保留为 `.dat_old`，失败回滚；回滚失败会在本次运行中隔离相关库存，阻止 API 和共享锁覆盖写入，管理员应检查日志及相关档案后再恢复。多个档案之间不保证断电原子提交。

人工验收（本次未启动游戏）：准备合法的首位与 `_1`、`_7` 后位，令首位散货为 1151、`_7` 有足量盒装材料；确认优先减少 `_7`、首位普通散货区补到可用容量，取空盒进入 `潜影盒`。再确认 1152 时不触发材料补货、接收者背包满时空盒保留、腾出空间后回收、含其他材料的盒子不误送，并连续取货至少三次检查总量守恒。离线/在线来源和编号缺失分别验收。

## 管理员授权

查询使用既有 `stock` 权限，取物使用新增 `inventoryTake` 权限，均默认 OP
对指定玩家授权：

```text
/fga playersort permission stock 玩家名 true
/fga playersort permission inventoryTake 玩家名 true
```

允许所有普通玩家调用可将玩家名替换为 `0`；撤销时使用 `false`
只有配置权限的管理员能执行这些指令，客户端声明 OP 或 Capability 不构成授权

## 注册与兼容

频道命名空间均为 `carpet-fga-addition`：

| 方向 | 频道 | Java DTO |
| --- | --- | --- |
| C2S | `playersort/query` | `PlayerSortInventoryPayloads.Query` |
| C2S | `playersort/take` | `PlayerSortInventoryPayloads.Take` |
| C2S | `playersort/direct_take` | `PlayerSortInventoryPayloads.DirectTake` |
| C2S | `playersort/silent_take` | `PlayerSortInventoryPayloads.SilentTake` |
| S2C | `playersort/reply` | `PlayerSortInventoryPayloads.Reply` |

Java 类位于 `carpet.fga`，对应常量 `TYPE` 和 `CODEC`
客户端先注册两种 C2S codec、一种 S2C codec 和 reply receiver，再检查服务器是否能接收 query/take
如果客户端安装了已注册这些 codec 的 FGA 构建，不要再次注册相同频道；可直接复用这些 DTO
没有频道、协议版本不符或权限不足时关闭自动补货，不向旧服务器反复试发
网络回调收到 reply 后应切换客户端主线程，再操作客户端 UI/状态

## 线上字段顺序

所有字段顺序固定，字符串为 Minecraft UTF，整数为 VarInt，requestId 为 8 字节 Long
协议 version 固定为 1；requestId 非负，在同一连接内跨 query/take 统一单调递增
允许重发同一个 requestId + 同一份完整参数；参数不同会拒绝，不得重复取物

Query：

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| version | VarInt | 1 |
| requestId | Long | 非负、单调递增 |
| kind | VarInt | 0 路线列表，1 指定库存，2 按物品 ID 查材料 |
| target | UTF(256) | kind=0 空字符串；kind=1 假人名，最多64字符；kind=2 完整物品ID |
| cursor | VarInt | 首次0；随后只使用返回的 nextCursor |

Take：

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| version | VarInt | 1 |
| requestId | Long | 与查询使用同一编号序列 |
| token | UTF(64) | 服务端库存查询返回的 token |
| itemId | UTF(256) | 例如 minecraft:stone |

Reply：`requestId: Long` 然后 `json: UTF(8192)`
JSON 必含 `version`、`status`；错误回复不包含库存

## 轻松放置接入流程

### 静默取物（26.3 可选扩展）

`playersort/silent_take` 字段依次为 `version: VarInt=1`、`requestId: Long`、`token: UTF(64)`、`itemId: UTF(256)`。token 空字符串表示直接从无前后缀中文名来源取货；非空 token 使用之前 kind=2 查询得到的来源及快照，允许回退到其他分类库存。成功仍返回 `kind:"take"`、`moved`、`itemId` 和 `refreshRequired`。

离线来源由有界 IO 工作线程读取、验证身份并准备修改；主线程复核请求、权限、来源未上线、文件版本和背包容量后原子替换来源文件并交付物品，原来源文件保留为 `.dat_old`。不召唤假人，不修改位置和载具；保留其他存档数据、盔甲和未交易物品，使用26.3 equipment副手格式。准备阶段锁定来源，阻止其他取物、分类离线操作及 Carpet 召唤；提交失败尝试回滚来源和接收者，回滚失败返回 TRANSFER_FAILED，禁止自动重放。已在线来源使用原在线转移且不关闭。

沿用 stock/inventoryTake 权限、取物限速、共享单调编号和重复编号结果缓存。WRITE_FAILED 表示写入未完成或已回滚，REQUEST_CANCELLED 表示接收者请求已失效。没有频道时客户端应提示并停止静默模式，不得自动召唤。此模式不提供断电时两份玩家存档的原子保证；存盘恢复仍按下文边界处理。

### 优先直接取货（26.3 扩展）

客户端检测服务端支持 `playersort/direct_take` 后，缺料时优先发送 DirectTake：字段依次为 `version: VarInt=1`、`requestId: Long`、`itemId: UTF(256)`。请求不带假人名称、数量或 token，服务端依据内置中文物品名称生成无前后缀的来源名（例如 `minecraft:glass` → `玻璃`），校验来源和当前库存后取回半组，直接返回既有 `kind:"take"` 成功回复。

该请求沿用取物权限、取物限速、共享编号、完整快照校验和重复编号结果缓存。每次补货使用新编号，超时重发原编号不会再次扣货。服务端只在有界 IO 线程读取已推导名称的档案，不在主线程扫描存档；离线来源仍要求身份标记，转移仍通过在线假人完成。直接来源不足、不存在或身份不合格时不扣货，客户端再发送 kind=2 材料查询，查找其他有效来源；背包满、旧副手和转移结果不确定等错误不得自动重复扣货。

客户端未安装 FGA 时可自行注册本频道 codec；安装了新版 FGA 时复用其 DirectTake DTO。旧服务端未提供本频道时继续使用以下查询/token 流程。

当轻松放置缺少材料时查询，而不是每次放置/每 Tick 请求：

建议客户端在玩家对准投影目标、实际尝试轻松放置且背包缺少材料时触发；使用投影材料需求解析出的物品 ID，不使用现实中光标所指方块的掉落物。同一材料只保留一个在途请求，失败应退避；该触发逻辑需要在投影客户端实现，服务端不能直接读取本地投影。

1. 发送 Query(1, 100, 2, "minecraft:stone", 0)
2. 若返回 SEARCH_CONTINUE，至少间隔1秒，用新的 requestId 和 nextCursor 继续查询同一物品
3. OK 返回材料来源、可用数量、半组数量和 token
4. 若本地仍需补货，距前一个发包至少250毫秒后发送 Take(1, 101, token, "minecraft:stone")
5. 等待取物 OK，并等原版背包更新；不要根据这个包伪造客户端 ItemStack
6. 然后重试轻松放置；下次取物必须重新查询，旧 token 已失效

材料查询成功示例：

```json
{"version":1,"status":"OK","kind":"material","itemId":"minecraft:stone","target":"bulk_stone","token":"服务端生成UUID","available":1728,"takeCount":32,"expiresInMs":30000}
```

取物成功：

```json
{"version":1,"status":"OK","kind":"take","itemId":"minecraft:stone","moved":32,"refreshRequired":true}
```

kind=0 返回 `kind:"routes"`、`entries:[{target,itemId}]`、`nextCursor`、`overflowMax:999` 和限额
列表中的 target 是分类首位名，后位名为 `首位_1` 至 `首位_999`，不代表每个后位实际存在
kind=1 返回 `kind:"inventory"`、target、token、expiresInMs、entries、nextCursor
entries 中 slot=0–35 为主背包，slot=36 为副手；不提供盔甲和末影箱
空槽只含 slot/count；非空槽含 itemId/name/count，较小物品包含原版 ItemStack CODEC JSON 的 stack
组件预览过大时返回 previewOmitted=true，服务端仍保留完整快照用于校验
分页是同一份快照，nextCursor=-1 表示结束；分页不延长30秒有效期

## 限额和错误

每玩家查询/取物各最多每秒一次；所有类型的网络请求之间至少间隔250毫秒；全局查询最多10次/秒、取物最多4次/秒
最多64个会话、8个在途取物；一个有界离线读取线程，读取队列最多8个
每次材料查找最多检查4个库存档案，需要时返回 SEARCH_CONTINUE
首次材料查询在有界 IO 线程只索引 playerdata 的 UUID 文件名（最多65536个），不读取任意玩家背包；单物品最多64个名称族、4096个实际候选
材料查找计划有效期30秒，后续查询只能使用返回的 nextCursor；不得根据旧版编号算法计算 cursor，计划过期需从0重新查询
档案压缩大小最多2MiB，NBT内存预算8MiB，完整库存组件快照编码大小最多128KiB
每页最多12个库存槽或24条路线，实际页大小随JSON预算缩小，reply最多8192字符
每会话只保留一份30秒快照和最近64条请求结果，空闲60秒清理，断线立即清理
250ms内的高频网络包或同连接过多待调度包可直接丢弃；正常客户端可设置3秒查询超时、15秒取物超时
超时重发应先使用原 requestId 和原完整参数，不能换编号直接重复 take；REPLAY_EXPIRED 后查询背包/库存再决定

主要 status：

| status | 客户端处理 |
| --- | --- |
| OK | 读取结果；取物完成等待原版背包同步 |
| SEARCH_CONTINUE | 新编号、1秒后从 nextCursor 继续 |
| RATE_LIMITED / SERVER_BUSY / TARGET_BUSY | 退避，新的查询编号重新查询 |
| REQUEST_PENDING | 原请求尚在执行，不新发取物 |
| REQUEST_ID_REUSED / REPLAY_EXPIRED | 编号管理错误或结果过期，不盲目补发取物 |
| SNAPSHOT_EXPIRED / STALE_SNAPSHOT | 重新查询，不扣货 |
| NO_SPACE | 背包不足，不扣货 |
| NOT_ENOUGH | 没有单名库存满足同组件半组，不扣货 |
| NOT_FOUND | 没找到候选库存；身份不合格另返回保护/身份错误 |
| INVENTORY_INDEX_TOO_LARGE | 档案目录超出索引上限，停止并提示管理员 |
| PERMISSION_DENIED / DISABLED / UNSUPPORTED_VERSION | 停止自动补货并提示原因 |
| INVALID_ITEM / INVALID_REQUEST / INVALID_CURSOR / INVALID_TARGET | 修正请求 |
| PROTECTED_TARGET / UNVERIFIED_TARGET | 该档案不可作为 API 假人库存 |
| LEGACY_OFFHAND | 有旧版副手槽位，停止补货；先备份并人工检查迁移，不自动上线覆盖档案 |
| SPAWN_DISABLED / SPAWN_FAILED / SPAWN_TIMEOUT | 不扣货；管理员检查假人规则/日志 |
| NOT_FOUND / READ_FAILED / INVENTORY_TOO_LARGE / TOO_MANY_ROUTES / REPLY_TOO_LARGE / TRANSFER_FAILED | 停止当前补货并提示管理员，不能循环刷请求 |

## 身份、旧档案与存盘边界

指定库存查询仅允许缓存登记的名称/后位；材料查询还可发现按服务端当前命名配置生成的候选。两条路径均校验离线 UUID、白名单排除和在线实体类型；Carpet 真人 shadow 被排除，不会被标记或作为库存取物
离线档案必须有匹配的 fgaOfflineSorterName 标记，真人不会被写入此标记
旧 quickopen 档案已有该标记；没有标记的旧召唤模式假人须先备份并检查旧副手槽位、载具和身份，确认可安全上线后，才由管理员召唤并正常下线一次
当前版本会在保存 Carpet 假人时保留该标记，不会自动覆盖或“认领”未知真人档案
夺舍中的玩家不能调用，正在分类/被夺舍的来源假人不能取物

26.3 副手从原版 equipment 中读取，不读取盔甲作为库存
包含旧 Inventory 副手槽位的档案会返回 LEGACY_OFFHAND，不能用“召唤后下线”盲目修复，应先备份并检查数据迁移
本次只修改 API 的安全读取，不改既有 quickopen 分类的档案写入路径

背包事务使用原版存盘及 dat_old 备份语义，不能保证断电时两个 playerdata 文件的原子提交
本 API 不自动处理断电后可能的不一致；生产环境应先备份，异常停机后检查两端档案/备份，勿直接重放取物
TRANSFER_FAILED 代表异常路径，不能承诺其返回时两端完全未改变，必须检查背包和库存，不可自动重复取物
实现与测试流程在 PlayerSortInventoryApi.java、PlayerSortInventoryApiTest.java 和隔离 smoke probe 中
实际投影客户端的轻松放置 UI、断电恢复及其他 Minecraft 版本仍需单独验证
