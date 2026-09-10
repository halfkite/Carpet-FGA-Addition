# Carpet FGA Addition 規則

> 文件版本：`1.5.8`

所有規則透過 `/carpet <規則名> <值>` 管理。未特別說明時，規則預設關閉

提示：可以使用 `Ctrl+F` 快速查詢自己想要的規則

### 奪舍操控玩家(playerPossession) · [相關指令](commands.md#cmd-player-possession)

使用 `/player <名字> possess` 奪舍操控線上玩家或假人，無需安裝 FGA 客戶端
規則值為 `false` 時關閉本功能
規則值為 `true` 時所有玩家可控制假人或真人
規則值為 `onlyfake` 時所有玩家僅可控制假人
規則值為 `opreal` 時普通玩家僅可控制假人，OP 可控制假人或真人
規則值為 `ops` 時僅 OP 可控制假人或真人
本功能同時遵守 Carpet `commandPlayer` 入口許可權，功能來源為 PlayerControl 模組實現（CC0-1.0），本功能為純服務端實現

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`，`true`，`onlyfake`，`opreal`，`ops`
- 分類：`FGA`，`特性`，`命令`

## 假人與通用功能

### 輕鬆放置實體(quickCraftEasyPlaceEntities)

允許 QuickCraft 客戶端請求放置投影實體；服務端負責校驗距離、實體資料和材料，並在成功生成時扣除材料

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.21-26.2`

### 假人名字最大長度(fakePlayerNameLength)

設定假人玩家名字的最大字元長度（1-128），超過 16 字元的名字會以相容別名傳送給客戶端，無需客戶端安裝模組

- 型別：`整數`
- 預設值：`-1`
- 參考選項：`-1`、`1-128`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 假人範圍控制(fakePlayerRangeControl) · [相關指令](commands.md#cmd-player-range)

啟用假人的區域放置、方塊右鍵、區域破壞、持續執行和基礎尋路功能，預設關閉

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`全版本`

### 末地折躍門再生(endGatewayRegeneration)

再生已被破壞的原版末地折躍門，只恢復折躍門方塊和自身資料，不改變周圍方塊

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 流浪商人不消失(wanderingTraderNoDespawn) · [相關指令](commands.md#cmd-villager-performance)

false 保持原版；true 使全部流浪商人不消失；controlled 僅保護命中 /villagerPerformance wanderingTrader 名稱或腳下方塊名單的流浪商人

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`true`、`controlled`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 假人檔案預載入(fakePlayerProfilePreload)

在召喚假人前非同步查詢玩家檔案，避免正版驗證請求阻塞服務端主執行緒；適用於 Minecraft 1.21 及以上版本

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`always`、`adaptive`
- 分類：`FGA`，`特性`
- 生效版本：`1.21.1`

### FGA Unicode 指令引數支援(fgaUnicodeArgumentsSupport)

允許未加引號的指令引數包含中文及其他 Unicode 字元；此獨立 FGA 規則不與 YACA 的同名規則衝突

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`全版本`

### 配方書始終解鎖(recipeBookAlwaysUnlocked)

玩家進入伺服器時自動獲得全部已註冊配方，每名玩家自動發放冷卻一分鐘，同時保留已儲存的配方解鎖資料

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 揹包進度觸發最佳化(inventoryAdvancementOptimization)

為 inventory_changed 進度使用精確物品候選索引；false 保持原版；exact 保持原版匹配語義，並在發現異常時安全回退

- 型別：`字串`
- 預設值：`false`
- 參考選項：`false`、`exact`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 玩家生命值顯示(playerHealthDisplay) · [相關指令](commands.md#cmd-player-health)

在多人遊戲列表名稱最右側顯示生命值；true 顯示全部玩家，false 僅向 /log playerHealth 訂閱者顯示，nofake 不顯示假人血量

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`true`、`false`、`nofake`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 展示框方塊化(itemFrameBlockification)

將展示框移出服務端實體 tick 排程並在支撐方塊變化時驗證，同時保留原版渲染、互動、掉落、地圖與比較器輸出

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.21.1`

### 煙花礦車加速(fireworkMinecartBoost) · [相關指令](commands.md#cmd-minecart)

允許玩家乘坐普通礦車時使用煙花火箭，以可配置速度維持滿速後線性減速

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.21.1`

### 鎖鏈繫結礦車(chainMinecartBinding) · [相關指令](commands.md#cmd-minecart)

允許使用鎖鏈將普通礦車連線為可持久儲存的線性列車

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.21.1`

### 礦車功能命令許可權(minecartFeatureCommandPermission) · [相關指令](commands.md#cmd-minecart)

控制礦車煙花加速與鎖鏈列車配置命令的使用許可權

- 型別：`許可權`
- 預設值：`false`
- 參考選項：`false`、`true`、`ops`、`0-4`
- 分類：`FGA`，`特性`，`命令`
- 生效版本：`1.21.1`

### 玩家離開載具急停(vehicleStopOnDismount) · [相關指令](commands.md#cmd-vehicle-stop)

控制駕駛者離開礦車或船時是否立即清除載具水平速度

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`minecart`、`boat`、`all`、`custom`
- 分類：`FGA`，`特性`
- 生效版本：`全部支援版本`

### 虛空世界生成(voidWorldGeneration) · [相關指令](commands.md#cmd-regenerate-terrain)

讓新生成區塊為空白，同時保留群系和結構定位資料

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`全部支援版本`

### 地形重生成命令許可權(terrainRegenerationCommandPermission) · [相關指令](commands.md#cmd-regenerate-terrain)

控制地形重生成與虛空清除命令的使用許可權

- 型別：`許可權`
- 預設值：`ops`
- 參考選項：`false`、`true`、`ops`、`0-4`
- 分類：`FGA`，`特性`，`命令`
- 生效版本：`1.21-26.2`

### 滿潛影盒合成(fullShulkerBoxCrafting)

允許裝單種物品的潛影盒按照對應普通合成/切石配方直接合成為成品盒；only64 要求恰好滿盒且產出為整數個滿盒，any 接受任意數量並一次點滿、允許最後一個非滿盒

- 型別：`字串`
- 預設值：`false`
- 參考選項：`false`、`only64`、`any`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 旁觀者免許可權自身傳送(spectatorFreeTeleport)

允許旁觀模式玩家使用 /tp 與 /teleport，但只能傳送自己；除非 TIS 或 AMS 的禁止管理員作弊規則開啟，否則 OP 保持原版完整傳送許可權

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 地獄門不發光(netherPortalNoLight)

控制地獄門是否發出光照，false保持原版，true關閉所有地獄門光照，onlynew僅關閉規則啟用後新生成的地獄門光照，關閉規則不會主動重新整理已有地獄門

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`true`、`onlynew`
- 分類：`FGA`，`特性`
- 生效版本：`1.21.1`

### 玩家末地門傳送控制(PlayerTpEndControl) · [相關指令](commands.md#cmd-playertpend)

控制玩家透過末地傳送門、末地主島出口和末地折躍門傳送：false 保持原版，true 阻止所有玩家傳送，control 按 /playertpend 的個人設定決定

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`true`、`control`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 客戶端維度 ID(clientDimensionIds)

對映客戶端所見的主世界、下界和末地維度 ID，用於分離小地圖和 Voxy 資料，不修改服務端維度

- 型別：`列表`
- 預設值：`[overworld,the_nether,the_end]`
- 參考選項：`三個客戶端維度 ID`
- 分類：`FGA`，`特性`
- 生效版本：`1.21.1+`

### 移除命令確認警告(removeDialogWarning)

移除伺服器傳送的執行命令點選事件和對話方塊操作的確認警告；僅在 Minecraft 1.21.8 及更高版本可用

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.21.8+`

### 恢復 26.2 前蜜蜂碰撞箱(restorePre26BeeCollisionBox)

將蜜蜂碰撞箱恢復為 Minecraft 26.2 前的大小：寬 0.7 格、高 0.6 格

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`26.2`


## 村民、生物與掉落物

### 村民繁殖動物化(villagerBreedingAnimalization)

潛行右鍵餵食成年村民可產生繁殖意願；餵食幼年村民可像其他動物一樣加快成長

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`true`、`only`
- 分類：`FGA`，`生存`
- 生效版本：`全版本`

### 幼年生物不長大(babyMobNoGrowth)

阻止全部幼年生物或自定義名稱完全匹配且區分大小寫的幼年生物成長，包括蝌蚪

- 型別：`字串`
- 預設值：`false`
- 參考選項：`false`、`true`、`mini`、自定義名稱`
- 分類：`FGA`，`生存`
- 生效版本：`1.21-26.2`

### 堅韌的花草(resilientPlants)

讓匹配的花草忽略原版存活限制，可以放在空氣位置或任意方塊上

- 型別：`字串`
- 預設值：`false`
- 參考選項：`false`、`true`、`[]`、方塊 ID 列表`
- 分類：`FGA`，`生存`
- 生效版本：`1.21.1+`

### 堅韌方塊(resilientBlocks)

自定義方塊被放置時不檢查下方方塊型別，收到更新時不檢查自身狀態

- 型別：`字串`
- 預設值：`false`
- 參考選項：`false`、`[]`、方塊 ID 列表`
- 分類：`FGA`，`生存`
- 生效版本：`1.21-26.2`

### 比較器隔方塊檢測容器訊號(comparatorThroughBlocks)

允許比較器隔著配置的前方方塊讀取後方容器訊號，例如 [chain,piston]，不改變其他紅石行為

- 型別：`方塊列表`
- 預設值：`false`
- 參考選項：`false`、`[chain]`、`[piston]`、`[chain,piston]`、自定義方塊 ID 列表`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 基岩版潛影貝複製(shulkerBedrockDuplication)

潛影貝被潛影貝子彈（自己的或其它潛影貝的）擊殺時，必定在原地重新生成一隻潛影貝，移植基岩版行為

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 潛影貝基岩版掠奪(shulkerBedrockLooting)

潛影殼掉落同步基岩版：固定 50% 機率掉落，掉落時均勻掉落 1 至 1+搶奪等級 個潛影殼

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 潛影貝攻擊盔甲架(shulkerAttackArmorStand)

允許潛影貝瞄準並射擊盔甲架：true 攻擊所有盔甲架，pumpkin 僅攻擊頭戴雕刻南瓜的盔甲架

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`true`、`pumpkin`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 取消鐵砧附魔懲罰(anvilNoPriorWorkPenalty)

取消鐵砧重複工作懲罰和過於昂貴限制，保留附魔衝突檢查和材料消耗

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 附魔等級上限增加(enchantmentLevelLimitIncrease)

false 或 0 保持原版上限；直接輸入數字 N 讓所有附魔的原版等級上限增加 N，最高儲存為255級

- 型別：`字串`
- 預設值：`false`
- 參考選項：`false`、`0`、`1`、整數 `0-254`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 附魔升級相加(enchantmentLevelAddition)

鐵砧合併同種附魔時直接相加等級，例如2+2變為4；輸入達到上限時不生成結果，相加超過上限時結果封頂為上限

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 經驗升級消耗扁平化(experienceLevelCost)

false 使用原版經驗曲線；29-30 讓30級後每級升級消耗經驗與29到30一樣；0-1 讓每級升級消耗經驗與0到1一樣

- 型別：`字串`
- 預設值：`false`
- 參考選項：`false`、`29-30`、`0-1`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 村民不合成麵包(villagerDoNotCraftBread)

讓農民村民處理小麥的表現與26.3+一樣，不再把小麥合成麵包

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 交易時村民升級(villagerUpgradeWhileTrading)

讓村民升級時的表現與26.3+一樣，無需關閉交易介面即可等待並完成升級

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`生存`
- 生效版本：`1.21-26.2`

### 村民效能最佳化(villagerPerformanceOptimization) · [相關指令](commands.md#cmd-villager-performance)

啟用村民效能最佳化並控制 /villagerPerformance 許可權：true 允許所有人，ops 需要 OP 2，1-4 表示最低許可權等級

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`true`、`ops`、`1-4`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 敵對生物物品欄訪問(hostileMobInventoryAccess)

雙手空手潛行右鍵敵對生物，開啟其六個裝備槽

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`生存`
- 生效版本：`全版本`

### 地面掉落物堆疊上限(droppedItemStackLimit) · [相關指令](commands.md#cmd-dropped-item-stack-limit)

啟用地面掉落物、玩家揹包和容器的獨立服務端堆疊上限；使用 /droppedItemStackLimit 配置，最大數量為 1000000000；false 關閉功能，true 允許所有玩家管理，ops 或 0-4 設定管理命令的最低許可權等級

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`true`、`ops`、`0-4`
- 分類：`FGA`，`生存`
- 生效版本：`全部支援版本`

### 地面掉落物合併距離(droppedItemMergeDistance)

修改地面掉落物實體的水平合併搜尋距離；-1 保持原版 0.5 格，垂直搜尋範圍不變

- 型別：`小數`
- 預設值：`-1`
- 參考選項：`-1`、`0-16`
- 分類：`FGA`，`生存`
- 生效版本：`1.21.1+`

### 解除填充命令上限(unlimitedFillCommands)

解除 /fill 與 /fillbiome 的體積上限；區塊仍須載入，其他原版檢查保持不變

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`生存`，`命令`
- 生效版本：`1.21.8+`

### 掉落物預堆疊(preStackDroppedItems) · [相關指令](commands.md#cmd-drop-pre-stack)

開啟由 /dropPreStack 配置的生物死亡與方塊掉落物預堆疊；新命令條目預設範圍為 1

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`生存`
- 生效版本：`1.21-26.2`

### 生物死亡掉落預堆疊(preStackMobDeathDrops)

指定生物死亡時立即預堆疊相容掉落物；使用 false 關閉，或填寫 [zombified_piglin,zombie] 形式的列表

- 型別：`字串`
- 預設值：`false`
- 參考選項：`false`、`[zombified_piglin]`
- 分類：`FGA`，`特性`
- 生效版本：`1.21-26.2`

### 生物死亡掉落預堆疊範圍(preStackMobDeathDropsRange)

設定舊版生物死亡預堆疊的三維範圍，可選 0-16 格，預設 1.5；請使用 /dropPreStack 遷移到逐實體範圍

- 型別：`小數`
- 預設值：`1.5`
- 參考選項：`0`、`1`、`3`、`8`、`16`
- 分類：`FGA`，`特性`
- 生效版本：`1.21-26.2`

### 殭屍豬靈掉落物自定義去除(zombifiedPiglinDropReduction)

自定義去除殭屍豬靈的指定掉落物

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`goldEquipment`、`rottenFlesh`、`all`
- 分類：`FGA`，`生存`
- 生效版本：`全版本`

### 生物掉落物自定義去除(entityDropRemoval) · [相關指令](commands.md#cmd-entity-drop-removal)

按生物配置要去除的死亡掉落物；false 關閉命令，true 允許所有人，ops 或 0-4 控制配置許可權

- 型別：`許可權`
- 預設值：`false`
- 參考選項：`false`、`true`、`ops`、`0-4`
- 分類：`FGA`，`生存`，`命令`
- 生效版本：`1.21+`

### 豬靈交易物品自定義去除(piglinBarterItemExclusions)

自定義去除豬靈交易返回的指定物品

- 型別：`列表`
- 預設值：`false`
- 參考選項：`false`、`ironBoots`、`potions`、物品 ID 列表`
- 分類：`FGA`，`生存`
- 生效版本：`全版本`



## 深板岩切石與玩家載入距離

### 深板岩切石配方(deepslateStonecuttingRecipes)

讓深板岩在切石機中的表現與26.1+一樣

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 木材切石機配方(woodStonecuttingRecipes)

允許使用切石機合成木製品

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 玩家載入距離(playerLoadDistance) · [相關指令](commands.md#cmd-player-load-distance)

控制每名玩家的區塊傳送與跟蹤距離，不改變模擬距離

- 型別：`許可權字串`
- 預設值：`false`
- 參考選項：`false`、`true`、`ops`、`0-4`
- 分類：`FGA`，`特性`，`命令`
- 生效版本：`1.21.1`

### 試煉刷怪籠等效人數(trialSpawnerPlayerMultiplier)

讓每名符合篩選條件的試煉參與玩家按指定人數計算，僅影響試煉刷怪和獎勵規模

- 型別：`整數`
- 預設值：`100`
- 參考選項：`1-10000`
- 分類：`FGA`，`特性`，`命令`
- 生效版本：`1.21-26.2`

### 試煉刷怪籠多倍觸發(trialSpawnerPlayerFilter)

選擇哪些玩家觸發試煉等效人數：false、true、bot_ 或自定義名稱字首

- 型別：`字串`
- 預設值：`false`
- 參考選項：`false`、`true`、`bot_`、自定義字首`
- 分類：`FGA`，`特性`，`命令`
- 生效版本：`1.21-26.2`

### 試煉截停命令許可權(trialStopCommandPermission) · [相關指令](commands.md#cmd-trial-stop)

啟用並控制 /trialStop 截停重新整理命令，支援 false、true、ops 和 0-4

- 型別：`許可權字串`
- 預設值：`false`
- 參考選項：`false`、`true`、`ops`、`0-4`
- 分類：`FGA`，`特性`，`命令`
- 生效版本：`1.21-26.2`



## 1.21+ 假人全物品分類

以下規則在 Minecraft `1.21-26.2` 註冊：

### 假人物品分類(fakePlayerItemSort) · [相關指令](commands.md#cmd-fake-player-item-sort)

啟用假人物品分類，使用 /fakePlayerItemSort 管理模式和分類配置

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`，`命令`
- 生效版本：`1.21+`


分類配置儲存在 `world/config/carpetfgaaddition/fake-player-item-sort.json`。`/fakePlayerItemSort mode summon` 使用線上 Carpet 假人，`mode quickopen` 直接讀寫離線 playerdata。舊版 `fakePlayerItemSort*` Carpet 配置只在首次啟動時遷移到該 JSON，不再註冊為規則。


## 配置檔案

世界配置位於 `world/config/carpetfgaaddition/`。升級時會從舊目錄 `world/carpet/carpetfgaaddition/` 安全遷移；遷移成功後舊檔案改名為 `.migrated`。損壞檔案會保留原位置，不會覆蓋新檔案。
