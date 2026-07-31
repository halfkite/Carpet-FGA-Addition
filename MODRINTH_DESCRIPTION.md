# Carpet FGA Addition

Carpet FGA Addition is a server-focused Fabric Carpet extension for fake players, item drops, villagers, commands, and cross-version compatibility. Features are opt-in and disabled by default.

## Supported builds

Version `1.4.0` is built for Minecraft `1.16.5`, `1.17.1`, `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, and `26.2`.

Install the JAR matching the server's Minecraft version. Most features are server-side and do not require the client mod. Long fake-player names display fully when both sides have the mod.

## Features

- Fake-player name length limits, client-safe aliases, profile preloading, and range actions.
- Full fake-player inventory sorting on Minecraft `1.21.1`, including `summon`, `quickopen`, offline playerdata access, shulker-box routing, rebuild tasks, dashboard snapshots, and API access to cached data.
- Unified entity, block, container, and hopper-minecart drop pre-stacking with per-entry ranges.
- Configurable ground-item stack limits with all, blacklist, and whitelist modes.
- Cross-version `/fill` and `/fillbiome` limit compatibility.
- Villager breeding and performance controls, gifts, and hostile-mob equipment access.
- Wandering-trader protection, end-gateway regeneration, spectator self-teleport, and other server utilities.
- Unicode command arguments, client-visible dimension IDs, dialog-warning controls, and the pre-26.2 bee collision box.

## Examples

```text
/carpet unlimitedFillCommands true
/carpet preStackDroppedItems true
/dropPreStack entity add minecraft:zombified_piglin 1.5
/dropPreStack block add minecraft:stone 1
/dropPreStack entity list
```

The drop-pre-stack configuration is stored in `carpet/carpetfgaaddition/drop-pre-stack.json` inside the world directory. Lists show the localized name, stable ID, and configured range.

## Fake-player sorting on 1.21.1

```text
/carpet fakePlayerItemSortMode quickopen
/player <fake-player> bot_sort
```

`quickopen` reads and writes offline playerdata without spawning a fake player just to access its inventory. `summon` uses Carpet's online fake-player inventory. The sorter keeps loose items in the primary target, routes box contents according to the configured rules, and never reads or writes armor slots.

## Build

```powershell
.\gradlew.bat buildAllVersions --no-daemon
```

Build helpers and local logs are organized under `scripts/`. Build outputs under `build/` and `mod-builds/` are ignored by Git.

## Credits

Designs inspired by MIT-licensed Org Addition, SaveMyRecipeBook, and InventoryAdvancementAccelerator are credited in the bundled `META-INF/NOTICE-*` files.
