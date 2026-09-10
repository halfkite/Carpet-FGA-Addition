# Carpet FGA Addition 規則

> 文件版本：`1.5.8`

所有規則透過 `/carpet <規則名> <值>` 管理。未特別說明時，規則預設關閉

提示：可以使用 `Ctrl+F` 快速查詢自己想要的規則

### 奪舍操控玩家(playerPossession) · [相關指令](commands.md#cmd-player-possession)

使用 `/player <名字> possess` 奪舍操控線上玩家或假人，無需安裝 FGA 客戶端<br>
使用 `/player <名字> possess stop` 結束自己或被接管者參與的會話<br>
規則值為 `false` 時關閉本功能<br>
規則值為 `true` 時所有玩家可控制假人或真人<br>
規則值為 `onlyfake` 時所有玩家僅可控制假人<br>
規則值為 `opreal` 時普通玩家僅可控制假人，OP 可控制假人或真人<br>
規則值為 `ops` 時僅 OP 可控制假人或真人<br>
本功能同時遵守 Carpet `commandPlayer` 入口許可權，功能和主要程式碼來源於模組 [PlayerControl](https://modrinth.com/mod/playercontrol)（CC0-1.0）本功能為純服務端實現

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`，`true`，`onlyfake`，`opreal`，`ops`
- 分類：`FGA`，`特性`，`命令`
- 生效版本：`1.21.1`

## 假人與通用功能

### 輕鬆放置實體(quickCraftEasyPlaceEntities)

允許 [QuickCraft](https://modrinth.com/mod/quickcraft-yiyihehe) 客戶端請求放置投影實體；服務端負責校驗距離、實體資料和材料，並在成功生成時扣除材料

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 假人名字最大長度(fakePlayerNameLength)

原版遊戲玩家名稱只允許16字元，本模組可以更改字元限制為（1-128），客戶端可選，超過 16 字元的名字會以相容別名傳送給未安裝本模組的客戶端(例如half...)

- 型別：`整數`
- 預設值：`-1`
- 參考選項：`-1`、`1-128`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 假人範圍控制(fakePlayerRangeControl) · [相關指令](commands.md#cmd-player-range)

啟用假人的區域放置、方塊右鍵、區域破壞等功能(功能未完善，不建議使用)

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.16.5+`

### 末地折躍門再生(endGatewayRegeneration)

允許已被破壞的末地折躍門再生，擊殺末影龍即可，只恢復折躍門方塊和自身資料，不改變周圍方塊

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 流浪商人不消失(wanderingTraderNoDespawn) · [相關指令](commands.md#cmd-villager-performance)

false：保持原版<br>
true：使全部流浪商人不消失<br>
controlled：僅保護命中 /villagerPerformance wanderingTrader 命名或腳下方塊名單的流浪商人不消失

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`true`、`controlled`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 假人檔案預載入(fakePlayerProfilePreload)

在召喚假人前非同步查詢玩家檔案，避免正版驗證請求阻塞服務端主執行緒<br>
false：保持 Carpet 原有的同步檔案查詢<br>
always：每次召喚假人前都非同步預載入檔案<br>
adaptive：第一次召喚保持原行為；30 秒內第二次召喚開啟 2 分鐘預載入視窗，視窗內每次召喚都會重置剩餘時間

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`always`、`adaptive`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### FGA Unicode 指令引數支援(fgaUnicodeArgumentsSupport)

允許未加引號的指令引數包含中文及其他 Unicode 字元，可以實現中文假人等操作<br>
來源於[YACA](https://modrinth.com/mod/yaca),由於其在26.1+版本停更遂移植，由於怕26.1以下版本於原本的YACA原此功能衝突，隨改規則名

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.16.5+`

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

在多人遊戲列表名稱最右側顯示生命值<br>
true 顯示全部玩家<br>
false 僅向 /log playerHealth 訂閱者顯示<br>
nofake 不顯示假人血量

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

允許使用鎖鏈將普通礦車連線為可持久儲存的線性列車(不建議使用)
- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.21.1`

### 礦車功能命令許可權(minecartFeatureCommandPermission) · [相關指令](commands.md#cmd-minecart)

控制礦車煙花加速與鎖鏈列車配置命令的使用許可權
false：禁用相關命令<br>
true：允許所有玩家使用<br>
ops：需要 OP 2 及以上<br>
0-4：設定命令的最低許可權等級

- 型別：`許可權`
- 預設值：`false`
- 參考選項：`false`、`true`、`ops`、`0-4`
- 分類：`FGA`，`特性`，`命令`
- 生效版本：`1.21.1`

### 玩家離開載具急停(vehicleStopOnDismount) · [相關指令](commands.md#cmd-vehicle-stop)

駕駛者離開礦車或船時立即清除載具速度<br>
false：關閉急停<br>
minecart：駕駛者離開礦車時清除水平速度<br>
boat：駕駛者離開船時清除水平速度<br>
all：同時處理礦車和船<br>
custom：按 `/vehicleStop` 為每名玩家儲存的礦車和船設定處理<br>
急停只清除水平速度，保留垂直速度；載具仍有其他玩家乘坐時保留速度

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`minecart`、`boat`、`all`、`custom`
- 分類：`FGA`，`特性`
- 生效版本：`全部支援版本`

### 虛空世界生成(voidWorldGeneration) · [相關指令](commands.md#cmd-regenerate-terrain)

讓新生成區塊為虛空，同時保留群系和結構定位資料，可以用指令重新生成地形

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`全部支援版本`

### 地形重生成命令許可權(terrainRegenerationCommandPermission) · [相關指令](commands.md#cmd-regenerate-terrain)

控制地形重生成與地形清空命令的使用許可權
false：禁用相關命令<br>
true：允許所有玩家使用<br>
ops：需要 OP 2 及以上<br>
0-4：設定命令的最低許可權等級

- 型別：`許可權`
- 預設值：`ops`
- 參考選項：`false`、`true`、`ops`、`0-4`
- 分類：`FGA`，`特性`，`命令`
- 生效版本：`1.21+`

### 滿潛影盒合成(fullShulkerBoxCrafting)

允許裝單種物品的潛影盒按照對應普通合成/切石配方直接合成為成品盒<br>
false：關閉功能<br>
only64：輸入盒必須按原版堆疊上限裝滿，產物和配方返還物必須恰好組成整數個滿盒<br>
any：輸入盒內可為 1 至容器堆疊上限的相同數量，按總材料一次完成合成，最後一個成品盒可不滿，餘料留在輸入盒<br>
所有輸入盒必須裝相同種類、相同數量且容量相同的可堆疊物品
舊版值 `true` 會自動按 `any` 處理

- 型別：`字串`
- 預設值：`false`
- 參考選項：`false`、`only64`、`any`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 旁觀者免許可權自身傳送(spectatorFreeTeleport)

允許旁觀模式玩家使用 `/tp` 與 `/teleport`，但是隻能控制自己傳送；[TIS](https://modrinth.com/mod/carpet-tis-addition) 或 [AMS](https://modrinth.com/mod/carpet-ams-addition) 的禁止管理員作弊規則未開啟時，OP 保持完整傳送許可權<br>
false：保持原版旁觀者傳送許可權<br>
true：旁觀者只能傳送自己，不能借此傳送其他實體；TIS 或 AMS 禁止管理員作弊規則開啟時 OP 也受同樣限制

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 地獄門不發光(netherPortalNoLight)

控制地獄門是否發出光照<br>
false：保持原版<br>
true：關閉所有地獄門光照<br>
onlynew：僅規則啟用新生成的地獄門無光照<br>
關閉規則不會主動重新整理地獄門光照，會自動同步客戶端 [MiniHUD](https://modrinth.com/mod/minihud) 的光照顯示

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`true`、`onlynew`
- 分類：`FGA`，`特性`
- 生效版本：`1.21.1`

### 玩家末地門傳送控制(PlayerTpEndControl) · [相關指令](commands.md#cmd-playertpend)

控制玩家透過末地傳送門、末地主島出口和末地折躍門傳送<br>
false：保持原版<br>
true：阻止所有玩家傳送<br>
control：按 `/playertpend` 的個人設定決定，未設定的門預設允許傳送；此純服務端規則不阻止非玩家實體

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`true`、`control`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

### 客戶端維度 ID(clientDimensionIds)

對映客戶端所見的主世界、下界和末地維度 ID，用於分離小地圖和 Voxy 資料，不修改服務端維度<br>
使用 `[overworld,the_nether,the_end]` 或按相同順序填寫三個客戶端維度 ID，省略名稱空間時預設使用 `minecraft`<br>
修改後需要重新連線；不會改變假人召喚、傳送、服務端存檔或服務端維度

- 型別：`列表`
- 預設值：`[overworld,the_nether,the_end]`
- 參考選項：`三個客戶端維度 ID`
- 分類：`FGA`，`特性`
- 生效版本：`1.21+`

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
- 生效版本：`26.2+`


## 村民、生物與掉落物

### 村民繁殖動物化(villagerBreedingAnimalization)

潛行右鍵餵食成年村民可產生繁殖意願；餵食幼年村民可像其他動物一樣加快成長<br>
false：僅保留原版村民繁殖方式<br>
true：同時保留原版拾取食物和玩家直接餵食兩種方式<br>
only：只有玩家直接餵食可以產生繁殖意願<br>
幼年村民每次消耗 1 個食物；麵包的成長加速效果相當於連續餵食 4 個胡蘿蔔、馬鈴薯或甜菜根

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`true`、`only`
- 分類：`FGA`，`生存`
- 生效版本：`1.16.5+`

### 幼年生物不長大(babyMobNoGrowth)

阻止幼年生物生長，包括蝌蚪<br>
false：關閉<br>
true：阻止全部幼年生物生長，包括蝌蚪<br>
mini：僅阻止自定義名稱完整等於小寫 `mini` 的幼體，`Mini` 不匹配<br>
其他值：按實體自定義名稱的完整文字、區分大小寫匹配；帶空格的名稱需要用引號傳入<br>
隻影響自然成長和餵食加速，不攔截 `/data` 或 NBT 直接修改年齡

- 型別：`字串`
- 預設值：`false`
- 參考選項：`false`、`true`、`mini`、`自定義名稱`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 堅韌的花草(resilientPlants)

讓匹配的花草忽略原版存活限制，可以放在空氣位置或任意方塊上<br>
false：關閉<br>
true：匹配全部支援的花草候選方塊<br>
[]：清空匹配列表<br>
方塊 ID 列表：只匹配列表中的方塊，名稱空間可省略

- 型別：`字串`
- 預設值：`false`
- 參考選項：`false`、`true`、`[]`、`方塊 ID 列表`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 堅韌方塊(resilientBlocks)

自定義方塊被放置時不檢查下方方塊型別，收到更新時不檢查自身狀態<br>
false 或 `[]`：關閉<br>
方塊 ID 列表：跳過放置時的支撐檢查、方塊更新時的存活檢查和下落排程；名稱空間可省略，儲存時會歸一化為完整 ID 列表

- 型別：`字串`
- 預設值：`false`
- 參考選項：`false`、`[]`、`方塊 ID 列表`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 比較器隔方塊檢測容器訊號(comparatorThroughBlocks)

允許比較器隔著配置的前方方塊讀取後方容器訊號，例如 `[chain,piston]`，不改變其他紅石行為<br>
false：關閉<br>
方塊 ID 列表：允許比較器隔著列表中的方塊讀取後方容器訊號，名稱空間可省略

- 型別：`方塊列表`
- 預設值：`false`
- 參考選項：`false`、`[chain]`、`[piston]`、`[chain,piston]`、`自定義方塊 ID 列表`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 基岩版潛影貝複製(shulkerBedrockDuplication)

直接致命傷害來源為潛影貝子彈時必定複製；新潛影貝在受擊前位置生成並繼承顏色與附著面朝向，原實體仍正常播放死亡動畫並掉落戰利品，原版受擊複製機制不受影響

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 潛影貝基岩版掠奪(shulkerBedrockLooting)

潛影殼掉落同步基岩版：固定 50% 機率掉落，掉落時均勻掉落 1 至 1+搶奪等級 個潛影殼<br>
無搶奪時與 Java 版的期望掉落相同，啟用後按基岩版公式替換戰利品表擲骰

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 潛影貝攻擊盔甲架(shulkerAttackArmorStand)

允許潛影貝瞄準並射擊盔甲架<br>
false：保持原版，不攻擊盔甲架<br>
true：攻擊範圍內的所有盔甲架<br>
pumpkin：僅攻擊頭戴雕刻南瓜的盔甲架

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

啟用村民效能最佳化並控制 `/villagerPerformance` 許可權<br>
false：關閉最佳化並禁用相關命令<br>
true：允許所有玩家使用<br>
ops：需要 OP 2 及以上<br>
1-4：設定命令的最低許可權等級

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
- 生效版本：`1.16.5+`

### 地面掉落物堆疊上限(droppedItemStackLimit) · [相關指令](commands.md#cmd-dropped-item-stack-limit)

啟用地面掉落物、玩家揹包和容器的獨立服務端堆疊上限；使用 `/droppedItemStackLimit` 配置，最大數量為 1000000000<br>
false：關閉規則並保持原版上限<br>
true：允許所有玩家管理配置<br>
ops：僅 OP 2 及以上可管理配置<br>
0-4：設定管理命令的最低許可權等級

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`true`、`ops`、`0-4`
- 分類：`FGA`，`生存`
- 生效版本：`全部支援版本`

### 地面掉落物合併距離(droppedItemMergeDistance)

修改地面掉落物實體的水平合併搜尋距離；`-1` 保持原版 0.5 格，垂直搜尋範圍不變，規則仍保持註冊

- 型別：`小數`
- 預設值：`-1`
- 參考選項：`-1`、`0-16`
- 分類：`FGA`，`生存`
- 生效版本：`1.21.1-26.2`

### 解除填充命令上限(unlimitedFillCommands)

解除 /fill 與 /fillbiome 的體積上限；區塊仍須載入，其他原版檢查保持不變<br>
false：保持原版體積上限<br>
true：移除體積上限

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`生存`，`命令`
- 生效版本：`1.21+`

### 掉落物預堆疊(preStackDroppedItems) · [相關指令](commands.md#cmd-drop-pre-stack)

開啟後由 `/dropPreStack` 配置的生物死亡與方塊掉落物預堆疊，新命令條目預設範圍為 1

- 型別：`布林`
- 預設值：`false`
- 參考選項：`false`、`true`
- 分類：`FGA`，`生存`
- 生效版本：`1.21+`

### 殭屍豬靈掉落物自定義去除(zombifiedPiglinDropReduction)

自定義去除殭屍豬靈的指定掉落物<br>
false：保持原版掉落<br>
goldEquipment：去除金制盔甲、金劍和金矛<br>
rottenFlesh：去除腐肉<br>
all：同時去除金制裝備和腐肉<br>
金粒和金錠不受影響

- 型別：`列舉`
- 預設值：`false`
- 參考選項：`false`、`goldEquipment`、`rottenFlesh`、`all`
- 分類：`FGA`，`生存`
- 生效版本：`1.16.5+`

### 生物掉落物自定義去除(entityDropRemoval) · [相關指令](commands.md#cmd-entity-drop-removal)

按生物配置要去除的死亡掉落物<br>
false：關閉命令<br>
true：允許所有玩家配置<br>
ops：需要 OP 2 及以上<br>
0-4：設定配置命令的最低許可權等級
使用 `/entityDropRemoval set <生物ID> <物品ID>` 新增指定物品，或使用 `allEquipment` 去除六個裝備槽掉落<br>
指定物品會過濾戰利品表與裝備掉落；`allEquipment` 只過濾六個裝備槽，不會誤刪戰利品表中的同名物品

- 型別：`許可權`
- 預設值：`false`
- 參考選項：`false`、`true`、`ops`、`0-4`
- 分類：`FGA`，`生存`，`命令`
- 生效版本：`1.21+`

### 豬靈交易物品自定義去除(piglinBarterItemExclusions)

自定義去除豬靈交易返回的指定物品<br>
false：保持原版交易<br>
`[ironBoots]`：去除鐵靴子<br>
`[potions]`：去除普通、噴濺和滯留藥水<br>
`[ironBoots,potions]`：同時去除鐵靴子和藥水<br>
物品 ID 列表：自定義去除物品，可省略 `minecraft` 名稱空間

- 型別：`列表`
- 預設值：`false`
- 參考選項：`false`、`ironBoots`、`potions`、`物品 ID 列表`
- 分類：`FGA`，`生存`
- 生效版本：`1.16.5+`



## 深板岩切石與玩家載入距離

### 深板岩切石配方(deepslateStonecuttingRecipes)

讓深板岩在切石機中的表現與26.1+一樣，可以直接放到切石機裡

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

控制每名玩家的區塊傳送與跟蹤距離，不改變模擬距離<br>
false：關閉相關命令<br>
true：允許所有玩家使用<br>
ops：需要 OP 2 及以上<br>
0-4：設定命令的最低許可權等級
使用 `/playerLoadDistance help` 檢視命令，末尾加 `persistent` 才會跨重啟儲存<br>
`-1` 僅弱載入中心區塊，`0` 強載入中心並保留 3×3 弱載入，`1-32` 設定區塊半徑，`none` 移除玩家載入

- 型別：`許可權字串`
- 預設值：`false`
- 參考選項：`false`、`true`、`ops`、`0-4`
- 分類：`FGA`，`特性`，`命令`
- 生效版本：`1.21.1`

### 試煉刷怪籠等效人數(trialSpawnerPlayerMultiplier)

讓每名符合篩選條件的試煉參與玩家按指定人數計算，僅影響試煉刷怪和獎勵規模<br>
範圍為 1-10000，預設 100；設定為 1 時保持原版一人規模

- 型別：`整數`
- 預設值：`100`
- 參考選項：`1-10000`
- 分類：`FGA`，`特性`，`命令`
- 生效版本：`1.21-26.2`

### 試煉刷怪籠多倍觸發(trialSpawnerPlayerFilter)

選擇哪些玩家觸發試煉等效人數：false、true、bot_ 或自定義名稱字首<br>
false：關閉多倍計算<br>
true：匹配所有玩家<br>
其他值：按名稱區分大小寫的字首匹配

- 型別：`字串`
- 預設值：`false`
- 參考選項：`false`、`true`、`bot_`、`自定義字首`
- 分類：`FGA`，`特性`，`命令`
- 生效版本：`1.21-26.2`

### 試煉截停命令許可權(trialStopCommandPermission) · [相關指令](commands.md#cmd-trial-stop)

啟用並控制 `/trialStop` 與 `/fga trialStop` 截停重新整理命令<br>
false：禁用命令<br>
true：允許所有玩家使用<br>
ops：需要 OP 2 及以上<br>
0-4：設定最低許可權等級；命令只處理已載入區塊內的刷怪籠，獎勵模式支援 `none`、`reward`、`fast`

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

此模組關於存檔的配置與持久化檔案位於 `world/config/carpetfgaaddition/`
