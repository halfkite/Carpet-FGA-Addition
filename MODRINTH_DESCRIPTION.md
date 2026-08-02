# Carpet FGA Addition

Carpet FGA Addition is a server-focused Fabric Carpet extension for fake players, item drops, villagers, compatibility fixes, player-list health, and performance utilities. Features are opt-in and disabled by default.

## Supported builds

Version `1.4.3` is available for Minecraft `1.16.5`, `1.17.1`, `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.4`, `1.20.6`, `1.21`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, and `26.2`.

## Features

- Fake-player range actions, Unicode command arguments, End gateway regeneration, and compatibility fixes.
- Villager trade/gift optimization, wandering-trader protection, and hostile-mob inventory access.
- Configurable ground-item limits, entity/block/container pre-stacking, merge distance, and fill compatibility.
- Recipe-book unlock behavior, hidden inventory advancement optimization, player-list health, and client-visible dimension IDs.
- Minecraft `1.21.1` only: offline `quickopen` and online `summon` fake-player item sorting, shulker handling, inventory rebuilds, and dashboard/API cache.

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

The project is distributed under the [MIT License](LICENSE). Adapted LGPL portions remain under their applicable LGPL terms. Designs inspired by MIT-licensed Org Addition, SaveMyRecipeBook, and InventoryAdvancementAccelerator are credited in the bundled `META-INF/NOTICE-*` files.
