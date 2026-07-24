# Carpet FGA Addition

Carpet FGA Addition is a Fabric extension for [Carpet](https://github.com/gnembon/fabric-carpet). It adds focused server-side utilities for fake players, villages, mobs, commands, and loot configuration while keeping every feature opt-in through `/carpet` rules.

## Highlights

- **Fake-player utilities**: configurable fake-player name lengths, client-safe aliases for long names, and range-based placing, breaking, and block interaction commands.
- **Fake-player profile preload**: on Minecraft 1.21.1, move authentication profile lookup off the server thread with always-on or adaptive burst detection.
- **Unicode command arguments**: allow unquoted command arguments to contain Chinese and other Unicode characters.
- **Villager interaction**: feed adult villagers while sneaking to grant breeding willingness, or feed baby villagers to speed up growth. Bread provides the same growth boost as four vegetable feedings.
- **Hostile mob equipment access**: sneak-right-click a hostile mob with empty hands to inspect and edit its six vanilla equipment slots.
- **Loot controls**: remove selected zombified piglin drops and exclude selected piglin-bartering results.
- **Ground-item controls**: configure all, blacklist, and per-item whitelist stack limits up to 8192, with lossless hopper and hopper-minecart handling.
- **Mob death pre-stacking**: immediately merge compatible drops from selected nearby mobs that die in the same tick.
- **Unlimited fill commands**: remove `/fill` and `/fillbiome` volume limits while retaining chunk-loading, world-border, and permission checks.
- **Bee collision-box option**: restore the pre-26.2 bee collision box on Minecraft 26.2.

## Requirements

- Fabric Loader 0.15.11 or newer
- Fabric Carpet
- Java 16 or newer for Minecraft 1.17.1, Java 21 for Minecraft 1.21.x, or Java 25 for Minecraft 26.1.2 and 26.2

Install the version of this mod that matches your Minecraft version. Features are disabled by default and can be configured with `/carpet`.

## Main Rules

| Rule | What it does |
| --- | --- |
| `fakePlayerNameLength` | Sets the maximum fake-player name length from 1 to 128, with client-safe aliases for players without the mod. |
| `fakePlayerRangeControl` | Enables range placement, block interaction, breaking, continuous tasks, and optional pathfinding for fake players. |
| `fakePlayerProfilePreload` | On Minecraft 1.21.1, asynchronously preloads profiles before fake-player spawning; supports `false`, `always`, and `adaptive`. |
| `unicodeArgumentsSupport` | Allows unquoted Unicode characters in command arguments. |
| `villagerBreedingAnimalization` | Enables direct villager feeding. `true` retains vanilla breeding; `only` requires direct feeding. |
| `hostileMobInventoryAccess` | Opens hostile mob equipment slots with an empty-handed sneak-right-click. |
| `zombifiedPiglinDropReduction` | Removes configured zombified piglin drops. |
| `piglinBarterItemExclusions` | Excludes configured items from piglin bartering results. |
| `droppedItemStackLimit` | Enables configurable all, blacklist, and whitelist ground-item stack limits up to 8192 on every supported version. |
| `preStackMobDeathDrops` | Pre-stacks compatible same-tick death drops from selected mobs on every supported version. |
| `preStackMobDeathDropsRange` | Sets the three-dimensional death-drop merge range from 0 to 16 blocks. |
| `unlimitedFillCommands` | Removes `/fill` and `/fillbiome` volume limits while preserving vanilla safety checks on every supported version. |
| `restorePre26BeeCollisionBox` | Restores the older bee collision box; available only on Minecraft 26.2. |
| `clientDimensionIds` | Maps client-visible Overworld, Nether, and End IDs for minimap/Voxy data separation on Minecraft 1.21.1 and 26.2 without changing server dimensions. |

## Villager Feeding

With `villagerBreedingAnimalization` enabled, sneak-right-click an adult villager with either 3 bread or 12 carrots, potatoes, or beetroot to grant breeding willingness. Baby villagers consume one supported food item per feeding and use the same percentage-based growth acceleration as vanilla baby animals. Bread counts as four vegetable feedings.

This mod is designed for servers. Clients do not need it for the server-side rules, although installing it on both sides enables full long fake-player name display.
