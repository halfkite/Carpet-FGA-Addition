# Carpet FGA Addition

Carpet FGA Addition is a server-focused Fabric Carpet extension for fake players, item drops, villagers, compatibility fixes, player-list health, and performance utilities. Features are opt-in and disabled by default.

## Supported builds

Version `1.4.10` is available for Minecraft `1.16.5`, `1.17.1`, `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.4`, `1.20.6`, `1.21`, `1.21.1`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, and `26.2`.

## Features

- Minecraft `1.21.1`: `resilientPlants` lets selected plants ignore vanilla survival restrictions and be placed without normal support.
- Minecraft `1.21-26.2` (excluding `1.21.3`): `woodStonecuttingRecipes` allows wood products to be crafted in the stonecutter, including bidirectional wood conversion, 4-stair/8-slab log yields, 1-stair/2-slab plank recipes, bamboo mosaics, ordinary boats, bamboo rafts, dedicated bamboo conversion ratios, bamboo-block-equivalent 9-bamboo inputs, and server-side input validation.

- All supported versions include void generation for new chunks; Minecraft `1.21-26.2` also provides restart-queued normal terrain regeneration and full-air chunk clearing.

- Fake-player range actions, Unicode command arguments, End gateway regeneration, and compatibility fixes.
- On Minecraft `1.21.1`, player End portal teleport control with global blocking or per-player `/playertpend` preferences for entrance portals, exit portals, and End gateways.
- Villager trade/gift optimization, wandering-trader protection, and hostile-mob inventory access.
- Minecraft `1.21-26.2`: server-side baby growth locking with `true`, `mini`, or an exact case-sensitive custom name, including tadpoles.
- Configurable ground-item limits, entity/block/container pre-stacking, merge distance, and fill compatibility.
- Recipe-book unlock behavior, hidden inventory advancement optimization, player-list health, and client-visible dimension IDs.
- Minecraft `1.21-26.2` (excluding `1.21.3`): core fake-player sorting with offline `quickopen`, online `summon`, whitelist, names, and shulker handling; `1.21.1` additionally keeps restock, rebuild, disk cache, worker tuning, and Dashboard/API features.
- Minecraft `1.21-26.2`: full shulker boxes support multi-material, tag, data-pack, and mod recipes with Shift/Q refill behavior and soft compatibility for Carpet AMS Addition `largeShulkerBox` 54-slot boxes.
- Minecraft `1.17.1-1.21.11`: deepslate can be stonecut directly into the same deepslate products provided by vanilla 26.1+.
- Minecraft `1.17.1-1.21.11`: `deepslateStonecuttingRecipes` makes deepslate behave in the stonecutter like vanilla 26.1+
- Minecraft `1.21-26.2`: farmer villagers can retain wheat instead of crafting bread, and villagers can finish upgrading while their trading screen stays open
- Minecraft `1.21.1`: `/playerLoadDistance` manages temporary or persistent per-player chunk sending and tracking distances
- Minecraft `1.21-26.2`: trial spawner equivalent-player scaling by name prefix and one-shot stop-and-refresh for loaded spawners with optional rewards
- All supported versions: configurable minecart and boat stopping when the controlling player dismounts.

## Documentation

- [Chinese rules](https://github.com/halfkite/Carpet-FGA-Addition/blob/main/docs/rules.md)
- [English rules](https://github.com/halfkite/Carpet-FGA-Addition/blob/main/docs/rules_en.md)
- [Chinese commands](https://github.com/halfkite/Carpet-FGA-Addition/blob/main/docs/commands.md)
- [English commands](https://github.com/halfkite/Carpet-FGA-Addition/blob/main/docs/commands_en.md)

The documentation is the source of truth for rule defaults, version gates, command syntax, permissions, pagination, clickable help, and world configuration migration.

## Main command roots

```text
/dropPreStack
/villagerPerformance
/fakePlayerItemSort
/log playerHealth
/fga help
```

The complete command reference is available in [English command documentation](https://github.com/halfkite/Carpet-FGA-Addition/blob/main/docs/commands_en.md).

## Credits

The project is distributed under the [MIT License](LICENSE). Adapted LGPL portions remain under their applicable LGPL terms. Designs or functionality derived from MIT-licensed Org Addition, SaveMyRecipeBook, InventoryAdvancementAccelerator, and StackSizeTweaks are credited in the bundled `META-INF/NOTICE-*` files.
