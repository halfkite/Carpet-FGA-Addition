# Carpet FGA Addition Rules

> Documentation version: `1.5.0`

All rules are managed with `/carpet <rule> <value>`. Unless stated otherwise, rules are disabled by default.

## Fake players and general features

| Rule | Type | Default | Values | Versions | Description |
|---|---|---|---|---|---|
| `fakePlayerNameLength` | Integer | `-1` | `-1`, `1-128` | 1.21+ | Controls fake-player name length; `-1` keeps vanilla limits. |
| `fakePlayerRangeControl` | Boolean | `false` | `false`, `true` | All supported versions | Enables fake-player range placing, interaction, breaking, and continuous tasks. |
| `endGatewayRegeneration` | Boolean | `false` | `false`, `true` | 1.21+ | Regenerates destroyed vanilla End gateways without changing surrounding blocks. |
| `wanderingTraderNoDespawn` | Enum | `false` | `false`, `true`, `controlled` | 1.21-26.1.2 | Controls natural wandering-trader despawning; `controlled` protects configured names or foot blocks. |
| `fakePlayerProfilePreload` | Enum | `false` | `false`, `always`, `adaptive` | 1.21.1 | Asynchronously preloads fake-player profiles. |
| `fgaUnicodeArgumentsSupport` | Boolean | `false` | `false`, `true` | All supported versions | Allows unquoted command arguments to contain Unicode characters. |
| `recipeBookAlwaysUnlocked` | Boolean | `false` | `false`, `true` | 1.21+ | Gives every player all registered recipes on login with a one-minute per-player cooldown while preserving saved unlock progress; it never clears recipe data. |
| `playerHealthDisplay` | Enum | `false` | `true`, `false`, `nofake` | 1.21+ | Shows health only at the far right of the multiplayer player list. No scoreboard or nametag display is created. Disabled by default; use `/carpet playerHealthDisplay true` to enable it. |
| `itemFrameBlockification` | Boolean | `false` | `false`, `true` | 1.21.1 | Removes normal and glowing item frames from server entity tick scheduling and validates support-block changes while preserving vanilla clients, interaction, drops, maps, and comparator behavior. |
| `fireworkMinecartBoost` | Boolean | `false` | `false`, `true` | 1.21.1 | Lets a player riding a normal minecart consume a firework for a configurable full-speed boost followed by linear deceleration. |
| `chainMinecartBinding` | Boolean | `false` | `false`, `true` | 1.21.1 | Uses chains to connect normal minecarts into persistent linear trains. |
| `minecartFeatureCommandPermission` | Permission | `false` | `false`, `true`, `ops`, `0-4` | 1.21.1 | Controls `/minecart` and `/fga minecart`; `false` hides them and `true`/`0` allows all players. |
| `vehicleStopOnDismount` | Enum | `false` | `false`, `minecart`, `boat`, `all`, `custom` | All supported versions | Clears horizontal vehicle speed when the controlling player dismounts; `custom` uses per-player `/vehicleStop` settings. |
| `voidWorldGeneration` | Boolean | `false` | `false`, `true` | All supported versions | Makes newly generated chunks empty while retaining biome and structure-location data; existing chunks are unchanged. |
| `terrainRegenerationCommandPermission` | Permission | `ops` | `false`, `true`, `ops`, `0-4` | 1.21-26.2 | Controls `/regenerateTerrain` and `/fga regenerateTerrain`, including destructive clear and regeneration tasks. |
| `fullShulkerBoxCrafting` | Boolean | `false` | `false`, `true` | 1.21-26.2 | Uses the server's current ordinary recipes for full shulker boxes, including multi-material, tag, data-pack, Shift/Q refill, and soft AMS 54-slot support. |
| `spectatorFreeTeleport` | Boolean | `false` | `false`, `true` | 1.21.1-1.21.5 | Lets non-OP spectators teleport themselves only. |
| `PlayerTpEndControl` | Enum | `false` | `false`, `true`, `control` | 1.21.1 | Controls player teleportation through End entrance portals, the End exit portal, and End gateways. `true` blocks all three; `control` uses `/playertpend` preferences and defaults to allow. Non-player entities are unchanged. |
| `clientDimensionIds` | List | `[overworld,the_nether,the_end]` | Three client dimension IDs | 1.21.1+ | Changes client-visible dimension IDs without changing server dimensions. |
| `removeDialogWarning` | Boolean | `false` | `false`, `true` | 1.21.8+ | Removes server-sent command/dialog confirmation warnings. |
| `restorePre26BeeCollisionBox` | Boolean | `false` | `false`, `true` | 26.2 | Restores the pre-26.2 bee collision box. |

## Villagers, entities, and drops

| Rule | Type | Default | Values | Versions | Description |
|---|---|---|---|---|---|
| `villagerBreedingAnimalization` | Enum | `false` | `false`, `true`, `only` | All supported versions | Controls direct player feeding of villagers. |
| `babyMobNoGrowth` | String | `false` | `false`, `true`, `mini`, custom name | 1.21-26.2 | `true` freezes every normally growing baby; `mini` is a name-mode preset; a custom value freezes only babies whose full custom-name text matches exactly and case-sensitively, including tadpoles. |
| `resilientPlants` | String | `false` | `false`, `true`, `[]`, block ID list | 1.21.1 | Lets `BushBlock` plants ignore vanilla survival restrictions with `true`; a list selects supported plant blocks such as cactus, sugar cane, bamboo, vines, and water plants. |
| `comparatorThroughBlocks` | Block list | `false` | `false`, `[chain]`, `[piston]`, `[chain,piston]`, custom block ID list | 1.21.1 | Lets a comparator read the analog container signal one block beyond a configured front block without changing that block's other redstone behavior. |
| `shulkerBedrockDuplication` | Boolean | `false` | `false`, `true` | 1.21.1 | A shulker killed by a shulker bullet, its own or another shulker's, always respawns a new shulker at the same spot, matching Bedrock Edition. |
| `shulkerBedrockLooting` | Boolean | `false` | `false`, `true` | 1.21.1 | Shulker shell drops follow Bedrock Edition looting: a flat 50% chance to drop, dropping 1 to 1+Looting shells uniformly. |
| `shulkerAttackArmorStand` | Enum | `false` | `false`, `true`, `pumpkin` | 1.21.1 | Lets shulkers target and shoot armor stands; `true` targets all armor stands, `pumpkin` targets only those wearing a carved pumpkin on the head. |
| `anvilNoPriorWorkPenalty` | Boolean | `false` | `false`, `true` | 1.21.1 | Removes the anvil prior-work penalty and the 40-level “too expensive” limit while keeping enchantment conflicts, material costs, and normal enchantment-combination costs. |
| `experienceLevelCost` | String | `false` | `false`, `29-30`, `0-1` | 1.21.1 | Flattens level-up costs. `29-30` fixes level 30 and above at 107 XP, the vanilla cost from level 29 to 30; `0-1` fixes every level at 7 XP, the vanilla cost from level 0 to 1. |
| `villagerDoNotCraftBread` | Boolean | `false` | `false`, `true` | 1.21-26.2 (excluding 1.21.3) | Makes farmer villagers handle wheat like 26.3+ by no longer crafting it into bread, without changing other farmer behavior |
| `villagerUpgradeWhileTrading` | Boolean | `false` | `false`, `true` | 1.21-26.2 | Lets villagers finish upgrading while the trading screen remains open and immediately refreshes their level, XP, and offers |
| `villagerPerformanceOptimization` | Enum | `false` | `false`, `true`, `ops`, `1-4` | 1.21+ | Enables villager trade/gift optimization and controls `/villagerPerformance` access. |
| `hostileMobInventoryAccess` | Boolean | `false` | `false`, `true` | All supported versions | Opens hostile-mob equipment with an empty-handed sneak right-click. |
| `droppedItemStackLimit` | Enum | `false` | `false`, `true`, `ops`, `0-4` | All supported versions | Configures independent ground, inventory, and container stack limits. Inventory or container limits require the FGA client; ground-only limits remain server-only. |
| `droppedItemMergeDistance` | Decimal | `-1` | `-1`, `0-16` | 1.21.1+ | Sets the horizontal ground-item merge distance; `-1` keeps vanilla behavior. |
| `unlimitedFillCommands` | Boolean | `false` | `false`, `true` | 1.21.8+ | Removes `/fill` and `/fillbiome` volume limits while keeping vanilla safety checks. |
| `preStackDroppedItems` | Boolean | `false` | `false`, `true` | 1.21-26.2 | Enables entity, block, and container pre-stacking configured by `/dropPreStack`. |
| `zombifiedPiglinDropReduction` | Enum | `false` | `false`, `goldEquipment`, `rottenFlesh`, `all` | All supported versions | Removes selected zombified-piglin drops. |
| `piglinBarterItemExclusions` | List | `false` | `false`, presets, or item IDs | All supported versions | Excludes selected piglin barter results. |

`babyMobNoGrowth` is server-side only. The `mini` preset matches only babies whose full custom name is lowercase `mini`; `Mini` does not match. Other name values read only an explicitly assigned custom name and compare its complete `Component#getString()` text case-sensitively. Quote names containing spaces, for example `/carpet babyMobNoGrowth "Forever Young"`. The rule blocks natural growth and feeding acceleration, while direct administrator changes through `/data` or NBT remain available. Disabling the rule lets frozen babies continue from their current age.

`shulkerBedrockDuplication` is server-side only. The trigger is a killing blow whose direct damage source is a shulker bullet, matching Bedrock Edition; kills by melee, arrows, fall damage, or anything else never respawn. The new shulker spawns at the pre-hurt position, inherits the dye color and attach face of the original, and is a fresh full-health entity; the original still plays its death animation and drops loot normally. The vanilla Java hit-based chance duplication is untouched and keeps working alongside this rule.

`shulkerBedrockLooting` is server-side only. Java drops exactly one shell with a chance rising 6.25% per Looting level (68.75% at Looting III); Bedrock keeps a flat 50% drop chance and rolls the count uniformly between 1 and 1+Looting. Expected shells without Looting / with I / II / III: Java 0.50 / 0.56 / 0.62 / 0.69, Bedrock 0.50 / 0.75 / 1.00 / 1.25. With the rule on, the shulker loot-table roll is replaced by the Bedrock formula; the Looting level is read from the killer's main hand (same ATTACKING_ENTITY source as the vanilla loot context), and the doMobLoot gamerule and other vanilla gates still apply. Without Looting the two formulas are identical (50% for one shell).

`shulkerAttackArmorStand` is server-side only. Vanilla shulker targeting only covers players and `Enemy` mobs; armor stands are neither and are never targeted. This rule adds a low-priority goal: with `true`, every armor stand inside the targeting range (follow distance, with the search box along the attach axis expanded to 4 blocks); with `pumpkin`, only armor stands whose head slot holds a carved pumpkin — survival players can equip one with a simple right click, no commands needed. The vanilla `ShulkerAttackGoal` then aims and fires bullets as usual; player-first targeting, revenge targets, and the peaceful-difficulty check are all preserved (survival players within 16 blocks are targeted first, creative players never are). Rule changes apply immediately without restarts or re-summoning, and a dying or no-longer-matching armor stand target is released right away so targeting resumes. The legacy `onlyWithPumpkinHead` and `onlyWithShulkerShell` option names normalize to `pumpkin` automatically, so saved configs need no manual migration.

The legacy `preStackMobDeathDrops` and `preStackMobDeathDropsRange` rules are hidden and retained only for save compatibility. Use `/dropPreStack entity ...` for new configuration.

## Deepslate stonecutting and player loading

| Rule | Type | Default | Values | Effective versions | Description |
|---|---|---|---|---|---|
| `deepslateStonecuttingRecipes` | Boolean | `false` | `false`, `true` | `1.21-1.21.11` | Makes deepslate behave in the stonecutter like it does in 26.1+. Only FGA recipes are controlled; vanilla, data-pack, and mod recipes are unchanged. The rule is not registered on `26.1.2` or `26.2`. |
| `woodStonecuttingRecipes` | Boolean | `false` | `false`, `true` | `1.21-26.2` (excluding 1.21.3) | Allows wood products to be crafted in the stonecutter. Logs or stems yield 4 stairs or 8 slabs, planks yield 1 stair or 2 slabs, and bamboo mosaic slabs/stairs yield 4/2. Bamboo blocks, stripped bamboo blocks, and 9 bamboo are equivalent inputs for the bamboo conversion table, with server-side validation for each multi-input recipe. |
| `playerLoadDistance` | Permission string | `false` | `false`, `true`, `ops`, `0-4` | `1.21.1` | Enables per-player chunk sending and tracking overrides without changing simulation distance. `false` disables the command. |
| `trialSpawnerPlayerMultiplier` | Integer | `100` | `1-10000` | `1.21-26.2` | Counts each matching player as this many participants for normal and ominous trial mob and reward scale; `1` is vanilla |
| `trialSpawnerPlayerFilter` | String | `false` | `false`, `true`, `bot_`, custom prefix | `1.21-26.2` | `false` disables scaling; `true` matches everyone; other values use a case-sensitive player-name prefix, with `bot_` as a preset |
| `trialStopCommandPermission` | Permission string | `false` | `false`, `true`, `ops`, `0-4` | `1.21-26.2` | Controls `/trialStop` access |

`playerLoadDistance` uses `/playerLoadDistance` and `/fga playerLoadDistance`. Distances are `-1`, `0`, `1-32`, or `none`. `-1` weakly loads only the center chunk, `0` strongly loads the center and weakly keeps a 3x3 area available, `1-32` is a per-player radius capped by the client's requested view distance for real players, and `none` removes the player's loading view. `set` is temporary; append `persistent` to save by UUID in `world/config/carpetfgaaddition/player-load-distance.json`. `reset` restores a persistent value, while `reset ... persistent` removes it. Active overrides are shown as a leftmost Tab-list prefix. This is server-side only

`trialSpawnerPlayerMultiplier` and `trialSpawnerPlayerFilter` apply to normal and ominous trial spawners without creating fake players or writing fake UUIDs. Each matching real participant contributes its own equivalent count, and rewards scale per real participant. The in-game display name of `trialSpawnerPlayerFilter` is “试炼刷怪笼多倍触发” in both language files

`trialStopCommandPermission` both enables and controls `/trialStop` and `/fga trialStop`. The command scans only loaded chunks. `range <radius>` uses the command source as a horizontal center and ignores Y, while `range from` uses a full XYZ box. `clear` removes only loaded mobs tracked by each spawner. `none` and `fast` refresh immediately; `reward` refreshes immediately after vanilla-paced ejection; all modes skip the full cooldown

## Fake-player item sorting, Minecraft 1.21+

| Rule | Type | Default | Values | Description |
|---|---|---|---|---|
| `fakePlayerItemSort` | Boolean | `false` | `false`, `true` | `1.21-26.2` (excluding 1.21.3) | Enables the fake-player sorter core; restock, rebuild, disk cache, dashboard, and worker tuning remain exclusive to `1.21.1`. |

Sorter settings are stored in `world/config/carpetfgaaddition/fake-player-item-sort.json`. `/fakePlayerItemSort mode summon` uses online Carpet fake players; `mode quickopen` edits offline playerdata directly. Legacy `fakePlayerItemSort*` Carpet settings are migrated once at startup and are no longer registered as rules.

## Full shulker box crafting

`fullShulkerBoxCrafting` provides the complex recipe implementation on Minecraft `1.21-26.2` and is server-side only. Place full shulker boxes of the required materials in the ordinary recipe shape in the player 2x2 grid or a crafting table. Recipes are resolved from the server's current recipe manager, including multi-material, tag, data-pack, and mod recipes. Identical full boxes may be stacked in recipe slots; each craft consumes one box per occupied slot, and stacked empty boxes in the inventory are consumed by count. Every input must be stackable, have the same per-box capacity, and be fully consumed; the main output and all remaining recipe items must each form whole full boxes. Box colors, custom names, and other box components are preserved. When Carpet AMS Addition is present and `largeShulkerBox` is enabled, FGA uses 54 slots for validation, conversion, and filled output boxes; disabled or absent AMS keeps the vanilla 27 slots. After a QuickCraft `Alt+C`, result-slot Shift move, or Q result drop finishes, matching full boxes that existed in the inventory before the click are distributed once and evenly across the corresponding recipe slots. Shift-left-click first completes every full-box transaction available from the original crafting-grid materials; the refill does not join that same crafting loop. Normal left-click pickup does not refill, and the client does not need FGA.

## Configuration files

World configuration is stored in `world/config/carpetfgaaddition/`. Upgrades migrate files from `world/carpet/carpetfgaaddition/`; successfully migrated files are renamed with a `.migrated` suffix. Corrupt files are preserved and never overwritten.

`inventoryAdvancementOptimization` is a hidden internal compatibility field. It is not registered as a Carpet rule and has no standalone command; its behavior is controlled by the versioned implementation and lifecycle hooks.
