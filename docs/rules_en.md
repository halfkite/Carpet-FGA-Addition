# Carpet FGA Addition Rules

> Documentation version: `1.4.2`

All rules are managed with `/carpet <rule> <value>`. Unless stated otherwise, rules are disabled by default.

## Fake players and general features

| Rule | Type | Default | Values | Versions | Description |
|---|---|---|---|---|---|
| `fakePlayerNameLength` | Integer | `-1` | `-1`, `1-128` | 1.18+ | Controls fake-player name length; `-1` keeps vanilla limits. |
| `fakePlayerRangeControl` | Boolean | `false` | `false`, `true` | All supported versions | Enables fake-player range placing, interaction, breaking, and continuous tasks. |
| `endGatewayRegeneration` | Boolean | `false` | `false`, `true` | 1.16.5+ | Regenerates destroyed vanilla End gateways without changing surrounding blocks. |
| `wanderingTraderNoDespawn` | Enum | `false` | `false`, `true`, `controlled` | 1.16.5-26.1.2 | Controls natural wandering-trader despawning; `controlled` protects configured names or foot blocks. |
| `fakePlayerProfilePreload` | Enum | `false` | `false`, `always`, `adaptive` | 1.21.1 | Asynchronously preloads fake-player profiles. |
| `fgaUnicodeArgumentsSupport` | Boolean | `false` | `false`, `true` | All supported versions | Allows unquoted command arguments to contain Unicode characters. |
| `recipeBookAlwaysUnlocked` | Boolean | `false` | `false`, `true` | 1.16.5+ | Keeps registered recipes available without per-player unlock progress storage. |
| `playerHealthDisplay` | Enum | `true` | `true`, `false`, `nofake` | 1.16.5+ | Shows health only at the far right of the multiplayer player list. No scoreboard or nametag display is created. |
| `spectatorFreeTeleport` | Boolean | `false` | `false`, `true` | 1.21.1-1.21.5 | Lets non-OP spectators teleport themselves only. |
| `clientDimensionIds` | List | `[overworld,the_nether,the_end]` | Three client dimension IDs | 1.21.1+ | Changes client-visible dimension IDs without changing server dimensions. |
| `removeDialogWarning` | Boolean | `false` | `false`, `true` | 1.21.8+ | Removes server-sent command/dialog confirmation warnings. |
| `restorePre26BeeCollisionBox` | Boolean | `false` | `false`, `true` | 26.2 | Restores the pre-26.2 bee collision box. |

## Villagers, entities, and drops

| Rule | Type | Default | Values | Versions | Description |
|---|---|---|---|---|---|
| `villagerBreedingAnimalization` | Enum | `false` | `false`, `true`, `only` | All supported versions | Controls direct player feeding of villagers. |
| `villagerPerformanceOptimization` | Enum | `false` | `false`, `true`, `ops`, `1-4` | 1.20.1+ | Enables villager trade/gift optimization and controls `/villagerPerformance` access. |
| `hostileMobInventoryAccess` | Boolean | `false` | `false`, `true` | All supported versions | Opens hostile-mob equipment with an empty-handed sneak right-click. |
| `droppedItemStackLimit` | Enum | `false` | `false`, `true`, `ops`, `0-4` | 1.21.1+ | Enables configurable ground-item stack limits. |
| `droppedItemMergeDistance` | Decimal | `-1` | `-1`, `0-16` | 1.21.1+ | Sets the horizontal ground-item merge distance; `-1` keeps vanilla behavior. |
| `unlimitedFillCommands` | Boolean | `false` | `false`, `true` | 1.21.8+ | Removes `/fill` and `/fillbiome` volume limits while keeping vanilla safety checks. |
| `preStackDroppedItems` | Boolean | `false` | `false`, `true` | 1.20.5-26.1.2 | Enables entity, block, and container pre-stacking configured by `/dropPreStack`. |
| `zombifiedPiglinDropReduction` | Enum | `false` | `false`, `goldEquipment`, `rottenFlesh`, `all` | All supported versions | Removes selected zombified-piglin drops. |
| `piglinBarterItemExclusions` | List | `false` | `false`, presets, or item IDs | All supported versions | Excludes selected piglin barter results. |

The legacy `preStackMobDeathDrops` and `preStackMobDeathDropsRange` rules are hidden and retained only for save compatibility. Use `/dropPreStack entity ...` for new configuration.

## Fake-player item sorting, Minecraft 1.21.1 only

| Rule | Type | Default | Values | Description |
|---|---|---|---|---|
| `fakePlayerItemSortMode` | Enum | `false` | `false`, `summon`, `quickopen` | Enables sorting. `quickopen` edits offline playerdata; `summon` uses online Carpet fake players. |
| `fakePlayerItemSortWhitelist` | Enum | `false` | `false`, `vanillaWhitelist`, `modWhitelist` | Selects whitelist behavior. |
| `fakePlayerItemSortQuickShulker` | Boolean | `false` | `false`, `true` | Enables quick shulker-box sorting. Armor slots are never read or written. |
| `fakePlayerItemSortNameFormat` | Enum | `false` | `false`, `autoDetect`, `prefix`, `suffix` | Controls target fake-player naming. |
| `fakePlayerItemSortTargetLanguage` | Enum | `english` | `english`, `chinese`, `custom` | Selects target-name language. |
| `fakePlayerItemSortShulkerRestock` | Boolean | `false` | `false`, `true` | Allows `box_restock` to craft plain empty shulker boxes. |
| `fakePlayerItemSortCleanOpenedTarget` | Boolean | `false` | `false`, `true` | Routes foreign main-inventory and offhand items when opening a target. |
| `fakePlayerItemSortInventoryRebuild` | Enum | `false` | `false`, `true`, `opall` | Controls inventory rebuild commands and OP-only all rebuilds. |
| `fakePlayerItemSortDashboard` | Boolean | `false` | `false`, `true` | Enables the local sorter dashboard and cache API. |
| `fakePlayerItemSortCpuThreads` | Enum | `0` | `0`, `1`, `2` | Selects the asynchronous worker preset. |
| `fakePlayerItemSortSpeed` | Enum | `8` | `4`, `8`, `16` | Selects the number of main-thread submissions per batch. |

## Configuration files

World configuration is stored in `world/config/carpetfgaaddition/`. Upgrades migrate files from `world/carpet/carpetfgaaddition/`; successfully migrated files are renamed with a `.migrated` suffix. Corrupt files are preserved and never overwritten.

`inventoryAdvancementOptimization` is a hidden internal compatibility field. It is not registered as a Carpet rule and has no standalone command; its behavior is controlled by the versioned implementation and lifecycle hooks.
