# Carpet FGA Addition Rules

> Documentation version: `1.5.8`

All rules are managed with `/carpet <rule> <value>`. Unless stated otherwise, rules are disabled by default

Tip: use `Ctrl+F` to quickly find a rule

### Player possession(playerPossession) · [Related command](commands_en.md#cmd-player-possession)

Use `/player <name> possess` to possess an online player or fake player without installing the FGA client
`false` disables the feature
`true` allows every player to possess fake or real players
`onlyfake` allows every player to possess fake players only
`opreal` allows regular players to possess fake players while operators may possess fake or real players
`ops` allows operators only to possess fake or real players
The feature also obeys Carpet `commandPlayer` entry permission, is based on the PlayerControl implementation (CC0-1.0), and is server-side only

- Type: `Enum`
- Default: `false`
- Options: `false`, `true`, `onlyfake`, `opreal`, `ops`
- Categories: `FGA`, `Feature`, `Command`

## Fake players and general features

### QuickCraft Easy Place Entities(quickCraftEasyPlaceEntities)

Allows QuickCraft clients to request schematic entity placement. The server validates reach, entity data, and materials before consuming materials and spawning the entity.

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21-26.2`

### Fake Player Name Length(fakePlayerNameLength)

Sets the maximum fake-player name length from 1 to 128. Long names use a client-compatible network alias.

- Type: `Integer`
- Default: `-1`
- Options: `-1`, `1-128`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Fake Player Range Control(fakePlayerRangeControl) · [Related command](commands_en.md#cmd-player-range)

Enables area placement, block interaction, area breaking, continuous tasks, and basic pathfinding for fake players.

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `All supported versions`

### End Gateway Regeneration(endGatewayRegeneration)

Regenerates destroyed vanilla End gateways using only the gateway block and its own data; surrounding blocks are unchanged.

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Wandering Trader No Despawn(wanderingTraderNoDespawn) · [Related command](commands_en.md#cmd-villager-performance)

false keeps vanilla behavior; true prevents every wandering trader from despawning; controlled protects only traders matching the /villagerPerformance wanderingTrader name or block lists.

- Type: `Enum`
- Default: `false`
- Options: `false`, `true`, `controlled`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Fake Player Profile Preload(fakePlayerProfilePreload)

Moves fake-player profile lookup off the server thread before spawning. Available on Minecraft 1.21 and newer.

- Type: `Enum`
- Default: `false`
- Options: `false`, `always`, `adaptive`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21.1`

### FGA Unicode Arguments Support(fgaUnicodeArgumentsSupport)

Allows unquoted command arguments to contain non-English and other Unicode characters. This independent FGA rule does not conflict with YACA's rule.

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `All supported versions`

### Always-Unlocked Recipe Book(recipeBookAlwaysUnlocked)

Gives every player all registered recipes on login with a one-minute per-player cooldown without discarding saved recipe unlock data

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Inventory Advancement Optimization(inventoryAdvancementOptimization)

Uses exact item candidate indexing for inventory_changed advancements. false keeps vanilla behavior; exact preserves vanilla matching and falls back safely on inconsistencies.

- Type: `String`
- Default: `false`
- Options: `false`, `exact`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Player Health Display(playerHealthDisplay) · [Related command](commands_en.md#cmd-player-health)

Shows health at the right side of player-list names. true shows all players, false shows only for /log playerHealth subscribers, and nofake hides fake-player health.

- Type: `Enum`
- Default: `false`
- Options: `true`, `false`, `nofake`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Item Frame Blockification(itemFrameBlockification)

Removes item frames from server entity tick scheduling and validates support changes while preserving vanilla rendering, interaction, drops, maps, and comparator output

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21.1`

### Firework Minecart Boost(fireworkMinecartBoost) · [Related command](commands_en.md#cmd-minecart)

Lets players riding normal minecarts use firework rockets for configurable full-speed boosts followed by linear deceleration

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21.1`

### Chain Minecart Binding(chainMinecartBinding) · [Related command](commands_en.md#cmd-minecart)

Lets chains connect normal minecarts into persistent linear trains

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21.1`

### Minecart Feature Command Permission(minecartFeatureCommandPermission) · [Related command](commands_en.md#cmd-minecart)

Controls access to firework minecart and chain train configuration commands

- Type: `Permission`
- Default: `false`
- Options: `false`, `true`, `ops`, `0-4`
- Categories: `FGA`, `Feature`, `Command`
- Effective versions: `1.21.1`

### Vehicle Stop On Dismount(vehicleStopOnDismount) · [Related command](commands_en.md#cmd-vehicle-stop)

Controls whether a vehicle immediately loses horizontal speed when its controlling player dismounts

- Type: `Enum`
- Default: `false`
- Options: `false`, `minecart`, `boat`, `all`, `custom`
- Categories: `FGA`, `Feature`
- Effective versions: `All supported versions`

### Void World Generation(voidWorldGeneration) · [Related command](commands_en.md#cmd-regenerate-terrain)

Makes newly generated chunks empty while retaining biome and structure-location data

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `All supported versions`

### Terrain Regeneration Command Permission(terrainRegenerationCommandPermission) · [Related command](commands_en.md#cmd-regenerate-terrain)

Controls access to terrain regeneration and void clearing commands

- Type: `Permission`
- Default: `ops`
- Options: `false`, `true`, `ops`, `0-4`
- Categories: `FGA`, `Feature`, `Command`
- Effective versions: `1.21-26.2`

### Full Shulker Box Crafting(fullShulkerBoxCrafting)

Crafts shulker boxes of one item through matching ordinary crafting and stonecutter recipes; only64 requires exact vanilla-full boxes and whole-box outputs, any accepts any content amount and crafts one click's worth with a partial final box

- Type: `String`
- Default: `false`
- Options: `false`, `only64`, `any`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Spectator Free Teleport(spectatorFreeTeleport)

Allows spectators to use /tp and /teleport on themselves only. Operators keep full vanilla teleport permissions unless TIS or AMS cheat prevention is enabled.

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Nether Portal No Light(netherPortalNoLight)

Controls Nether portal block light: false keeps vanilla behavior, true disables light from all portals, and onlynew disables light from portals created while the rule is active; disabling the rule does not actively refresh existing portals

- Type: `Enum`
- Default: `false`
- Options: `false`, `true`, `onlynew`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21.1`

### Player End Portal Teleport Control(PlayerTpEndControl) · [Related command](commands_en.md#cmd-playertpend)

Controls player teleportation through End portals, End exit portals, and End gateways: false keeps vanilla behavior, true blocks all player portal teleports, and control uses per-player preferences managed by /playertpend

- Type: `Enum`
- Default: `false`
- Options: `false`, `true`, `control`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Client Dimension IDs(clientDimensionIds)

Maps the client-visible dimension IDs for the Overworld, Nether, and End. This separates minimap and Voxy data without changing server-side dimensions.

- Type: `List`
- Default: `[overworld,the_nether,the_end]`
- Options: `Three client dimension IDs`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21.1+`

### Remove Command Confirmation Warning(removeDialogWarning)

Removes the confirmation warning for server-sent run-command clicks and dialog actions. Only available on Minecraft 1.21.8 and newer.

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21.8+`

### Restore Pre-26.2 Bee Collision Box(restorePre26BeeCollisionBox)

Restores the bee collision box from before Minecraft 26.2: 0.7 blocks wide and 0.6 blocks high.

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `26.2`


## Villagers, entities, and drops

### Animalized Villager Breeding(villagerBreedingAnimalization)

Allows shift-right-click feeding to give adult villagers breeding willingness and speed up baby villager growth like other animals.

- Type: `Enum`
- Default: `false`
- Options: `false`, `true`, `only`
- Categories: `FGA`, `Feature`
- Effective versions: `All supported versions`

### Baby Mob No Growth(babyMobNoGrowth)

Prevents all baby mobs or baby mobs with an exact case-sensitive custom name from growing, including tadpoles

- Type: `String`
- Default: `false`
- Options: `false`, `true`, `mini`, custom name`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21-26.2`

### Resilient Plants(resilientPlants)

Lets matching plants ignore vanilla survival restrictions and be placed without normal support

- Type: `String`
- Default: `false`
- Options: `false`, `true`, `[]`, block ID list`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21.1+`

### Resilient Blocks(resilientBlocks)

Configured blocks skip the below-block support check when placed and ignore block updates instead of checking their own state

- Type: `String`
- Default: `false`
- Options: `false`, `[]`, block ID list`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21-26.2`

### Comparator Container Signal Through Blocks(comparatorThroughBlocks)

Lets comparators read an analog container signal through configured front blocks such as [chain,piston] without changing their other redstone behavior

- Type: `Block list`
- Default: `false`
- Options: `false`, `[chain]`, `[piston]`, `[chain,piston]`, custom block ID list`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Shulker Bedrock Duplication(shulkerBedrockDuplication)

A shulker killed by a shulker bullet, its own or another shulker's, always respawns a new shulker at the same spot, like Bedrock Edition

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Shulker Bedrock Looting(shulkerBedrockLooting)

Shulker shell drops follow Bedrock Edition looting: a flat 50% chance to drop, dropping 1 to 1+Looting shells uniformly

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Shulker Attack Armor Stand(shulkerAttackArmorStand)

Allows shulkers to target and shoot armor stands: true targets all, pumpkin targets only those wearing a carved pumpkin on the head

- Type: `Enum`
- Default: `false`
- Options: `false`, `true`, `pumpkin`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Remove Anvil Enchantment Penalty(anvilNoPriorWorkPenalty)

Removes the anvil prior-work penalty and too-expensive limit while keeping enchantment conflicts and material costs

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Increase Enchantment Level Limits(enchantmentLevelLimitIncrease)

false or 0 keeps vanilla limits; enter N directly to add N to every enchantment's vanilla maximum, capped at stored level 255

- Type: `String`
- Default: `false`
- Options: `false`, `0`, `1`, integer `0-254`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Add Enchantment Levels(enchantmentLevelAddition)

Adds matching enchantment levels in an anvil, so 2+2 becomes 4; an input at the maximum gives no result, while sums above it are capped at the maximum

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Flat Experience Level Costs(experienceLevelCost)

false keeps vanilla costs; 29-30 makes level 30+ cost the same as level 29 to 30; 0-1 makes every level cost the same as level 0 to 1

- Type: `String`
- Default: `false`
- Options: `false`, `29-30`, `0-1`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Villagers Do Not Craft Bread(villagerDoNotCraftBread)

Makes farmer villagers handle wheat like 26.3+ by no longer crafting it into bread

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Villager Upgrade While Trading(villagerUpgradeWhileTrading)

Lets villagers wait for and complete profession upgrades without closing the trading screen, like 26.3+

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21-26.2`

### Villager Performance Optimization(villagerPerformanceOptimization) · [Related command](commands_en.md#cmd-villager-performance)

Enables villager performance optimization and controls access to /villagerPerformance: true for everyone, ops for OP level 2, or 1-4 for a minimum permission level.

- Type: `Enum`
- Default: `false`
- Options: `false`, `true`, `ops`, `1-4`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Hostile Mob Inventory Access(hostileMobInventoryAccess)

Opens a hostile mob's six equipment slots by shift-right-clicking it with both hands empty.

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `All supported versions`

### Dropped Item Stack Limit(droppedItemStackLimit) · [Related command](commands_en.md#cmd-dropped-item-stack-limit)

Enables separately configured server-side stack limits for ground items, player inventories, and containers through /droppedItemStackLimit, up to 1000000000 items. false disables the feature; true allows everyone to manage it; ops or 0-4 set the manager permission level.

- Type: `Enum`
- Default: `false`
- Options: `false`, `true`, `ops`, `0-4`
- Categories: `FGA`, `Feature`
- Effective versions: `All supported versions`

### Dropped Item Merge Distance(droppedItemMergeDistance)

Changes the horizontal search distance for merging ground item entities; -1 keeps vanilla 0.5 blocks. The vertical search range is unchanged.

- Type: `Decimal`
- Default: `-1`
- Options: `-1`, `0-16`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21.1+`

### Unlimited Fill Commands(unlimitedFillCommands)

Removes the volume limit from /fill and /fillbiome. Chunks must still be loaded and all other vanilla checks remain active.

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`, `Command`
- Effective versions: `1.21.8+`

### Drop Pre-stacking(preStackDroppedItems) · [Related command](commands_en.md#cmd-drop-pre-stack)

Enables entity-death and block-drop pre-stacking configured by /dropPreStack. New command entries default to range 1.

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21-26.2`

### Pre-stack Mob Death Drops(preStackMobDeathDrops)

Immediately pre-stacks compatible death drops from selected mob entity types. Use false or a list such as [zombified_piglin,zombie].

- Type: `String`
- Default: `false`
- Options: `false`, `[zombified_piglin]`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21-26.2`

### Pre-stack Mob Death Drop Range(preStackMobDeathDropsRange)

Sets the legacy three-dimensional same-tick merge range from 0 to 16 blocks; default is 1.5. Migrate to per-entity ranges with /dropPreStack.

- Type: `Decimal`
- Default: `1.5`
- Options: `0`, `1`, `3`, `8`, `16`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21-26.2`

### Zombified Piglin Drop Reduction(zombifiedPiglinDropReduction)

Removes selected drops from zombified piglins.

- Type: `Enum`
- Default: `false`
- Options: `false`, `goldEquipment`, `rottenFlesh`, `all`
- Categories: `FGA`, `Feature`
- Effective versions: `All supported versions`

### Custom Entity Drop Removal(entityDropRemoval) · [Related command](commands_en.md#cmd-entity-drop-removal)

Configures death drops to remove per entity; false disables the command, true allows everyone, and ops or 0-4 controls configuration access

- Type: `Permission`
- Default: `false`
- Options: `false`, `true`, `ops`, `0-4`
- Categories: `FGA`, `Feature`, `Command`
- Effective versions: `1.21+`

### Piglin Barter Item Exclusions(piglinBarterItemExclusions)

Excludes selected items from piglin bartering results.

- Type: `List`
- Default: `false`
- Options: `false`, presets, or item IDs`
- Categories: `FGA`, `Feature`
- Effective versions: `All supported versions`



## Deepslate stonecutting and player loading

### Deepslate Stonecutting Recipes(deepslateStonecuttingRecipes)

Makes deepslate behave in the stonecutter like it does in 26.1+

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Wood Stonecutting Recipes(woodStonecuttingRecipes)

Allows wood products to be crafted in the stonecutter

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`
- Effective versions: `1.21+`

### Player Load Distance(playerLoadDistance) · [Related command](commands_en.md#cmd-player-load-distance)

Controls per-player chunk sending and tracking distance without changing simulation distance

- Type: `Permission string`
- Default: `false`
- Options: `false`, `true`, `ops`, `0-4`
- Categories: `FGA`, `Feature`, `Command`
- Effective versions: `1.21.1`

### Trial Spawner Equivalent Players(trialSpawnerPlayerMultiplier)

Counts each matching trial participant as the configured number of players for trial mobs and rewards

- Type: `Integer`
- Default: `100`
- Options: `1-10000`
- Categories: `FGA`, `Feature`, `Command`
- Effective versions: `1.21-26.2`

### 试炼刷怪笼多倍触发(trialSpawnerPlayerFilter)

Selects players affected by the trial multiplier: false, true, bot_, or a custom name prefix

- Type: `String`
- Default: `false`
- Options: `false`, `true`, `bot_`, custom prefix`
- Categories: `FGA`, `Feature`, `Command`
- Effective versions: `1.21-26.2`

### Trial Stop Command Permission(trialStopCommandPermission) · [Related command](commands_en.md#cmd-trial-stop)

Enables and controls the /trialStop stop-and-refresh command with false, true, ops, or permission levels 0-4

- Type: `Permission string`
- Default: `false`
- Options: `false`, `true`, `ops`, `0-4`
- Categories: `FGA`, `Feature`, `Command`
- Effective versions: `1.21-26.2`



## Fake-player item sorting, Minecraft 1.21+

### Fake Player Item Sorting(fakePlayerItemSort) · [Related command](commands_en.md#cmd-fake-player-item-sort)

Enables fake-player inventory sorting; use /fakePlayerItemSort to manage mode and sorter settings

- Type: `Boolean`
- Default: `false`
- Options: `false`, `true`
- Categories: `FGA`, `Feature`, `Command`
- Effective versions: `1.21+`


Sorter settings are stored in `world/config/carpetfgaaddition/fake-player-item-sort.json`. `/fakePlayerItemSort mode summon` uses online Carpet fake players; `mode quickopen` edits offline playerdata directly. Legacy `fakePlayerItemSort*` Carpet settings are migrated once at startup and are no longer registered as rules.


## Configuration files

World configuration is stored in `world/config/carpetfgaaddition/`. Upgrades migrate files from `world/carpet/carpetfgaaddition/`; successfully migrated files are renamed with a `.migrated` suffix. Corrupt files are preserved and never overwritten.
