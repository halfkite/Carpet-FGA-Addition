# Carpet FGA Addition Commands

> Documentation version: `1.5.0`

## Command index

| Command | Related rule | Permission/version | Description |
|---|---|---|---|
| `/player` range actions | `fakePlayerRangeControl` | Carpet player permission/all versions | Runs fake-player range placement, interaction, attack, or continuous tasks. |
| `/droppedItemStackLimit` | `droppedItemStackLimit` | Rule permission/all supported versions | Configures independent ground, inventory, and container stack limits. Inventory or container limits require the FGA client. |
| `/dropPreStack` | `preStackDroppedItems` | Drop configuration permission/1.20.5-26.2 | Configures entity, block, and container pre-stacking. |
| `/villagerPerformance` | `villagerPerformanceOptimization` | Rule permission/1.20.1+ | Configures villager trades, gifts, and wandering-trader protection. |
| `/fakePlayerItemSort` | 1.21-26.2 (excluding 1.21.3) sorter rules | `commandPlayer`/1.21-26.2 (excluding 1.21.3) | Configures the sorter core; dashboard, rebuild, restock, and worker settings are only available on 1.21.1. |
| `/player <name> bot_sort` | 1.21-26.2 (excluding 1.21.3) sorter rules | `commandPlayer`/1.21-26.2 (excluding 1.21.3) | Starts or stops a target fake player's sorter job; rebuild syntax is only available on 1.21.1. |
| `/minecart` | Minecart firework and chain rules | `minecartFeatureCommandPermission`/1.21.1 | Configures firework minecart speed and chain train distance. |
| `/vehicleStop` | `vehicleStopOnDismount` | Self; OP manages online players/all supported versions | Configures per-player minecart and boat stopping on dismount. |
| `/regenerateTerrain` | `voidWorldGeneration`, `terrainRegenerationCommandPermission` | Configured permission/1.21-26.2 | Queues normal-terrain regeneration or full-air clearing for the next restart. |
| `/trialStop` | `trialStopCommandPermission` | Rule permission/1.21-26.2 | Stops and refreshes loaded trial spawners with no, normal, or immediate rewards. |
| `/playertpend` | `PlayerTpEndControl` | `control` mode/1.21.1 | Manages each player's three End portal preferences. |

## `/playertpend`

First run `/carpet PlayerTpEndControl control`. `enter` is an End entrance portal, `exit` is the main-island End exit portal, and `gateway` is an End gateway.

```text
/playertpend status [player]
/playertpend set <enter|exit|gateway> <allow|deny>
/playertpend set <player> <enter|exit|gateway> <allow|deny>
/playertpend reset [enter|exit|gateway]
/playertpend reset <player> [enter|exit|gateway]
```

Preferences are saved by UUID at `world/config/carpetfgaaddition/player-tp-end-control.json`. Operators may modify any online player; non-operators may modify themselves and online Carpet fake players.

## `/regenerateTerrain`

Related rules: `voidWorldGeneration`, `terrainRegenerationCommandPermission`

```text
/regenerateTerrain regenerate box <x1> <z1> <x2> <z2>
/regenerateTerrain clear radius <x> <z> <radius>
/regenerateTerrain regenerate|clear dimension <dimension> box|radius ...
/regenerateTerrain confirm <taskId>
/regenerateTerrain cancel <taskId>
/regenerateTerrain retry <taskId>
/regenerateTerrain list [page]
```

Coordinates are block coordinates and expand to whole chunks. Every X/Z argument offers Tab suggestions for the player's position and targeted block, and previews show the exact chunk count and effective range. The green confirmation button executes the confirmation directly; confirmation only queues the task, and the world changes on the next server restart. Multiple confirmed tasks can run together. `regenerate` deletes and normally regenerates terrain. `clear` reads an all-air network payload into every section palette, clears block entities, non-player entities, POI, scheduled ticks, heightmaps, and lighting data, and removes adjacent fluids within eight blocks outside the effective horizontal border, covering the maximum horizontal spread of vanilla water and Nether lava; waterlogged blocks keep the block and lose only their waterlogged state. Region files touched by the clear range or its border are backed up before execution. A failed task can be retried without overwriting its original backup.
| `/log playerHealth` | `playerHealthDisplay` | Carpet Logger/1.16.5+ | Toggles the current player's Tab health subscription. |
| `/fga` | FGA features | Version-gated | Shows the FGA index and redirects FGA command roots. |

## `/player` range actions

### Related rule

`fakePlayerRangeControl`

### Syntax

```text
/player <fake> use range <from> to <to> [options]
/player <fake> use continuous range <from> to <to> [options]
/player <fake> attack range <from> to <to> [options]
/player <fake> attack continuous range <from> to <to> [options]
/player <fake> stop
/player <fake> use|attack range help
```

Options can be combined: `pathfinding`, `reach <0.1-64>`, `airPlace`, `ignoreObstruction`, `placeBlock`, `interactBlock`, and `interactSpeed <1-64>`.

## `/droppedItemStackLimit`

### Related rule

`droppedItemStackLimit`

### Syntax

```text
/droppedItemStackLimit mode all <count>
/droppedItemStackLimit mode black <count>
/droppedItemStackLimit mode whitelist
/droppedItemStackLimit set black <item id>
/droppedItemStackLimit remove black <item id>
/droppedItemStackLimit set whitelist <item id> <count>
/droppedItemStackLimit remove whitelist <item id>
/droppedItemStackLimit list [black|whitelist] [page]
/droppedItemStackLimit clear
```

`list` is paged and shows the display name, full item ID, and count. List entries provide clickable removal commands. Invalid configuration keeps vanilla-safe limits and rejects writes.

## `/dropPreStack` and `/fga dropPreStack`

### Related rule

`preStackDroppedItems`

### Syntax

```text
/dropPreStack help
/dropPreStack status
/dropPreStack entity add <entity id> [range]
/dropPreStack entity remove <entity id>
/dropPreStack entity set <entity id> [range]
/dropPreStack entity list [page]
/dropPreStack block add <item id> [range]
/dropPreStack block remove <item id>
/dropPreStack block set <item id> [range]
/dropPreStack block list [page]
/dropPreStack container add <block or entity id> [range]
/dropPreStack container remove <block or entity id>
/dropPreStack container set <block or entity id> [range]
/dropPreStack container list [page]
```

Ranges are `0-16` and default to `1.0`. IDs accept both `minecraft:stone` and `stone`; the item side also accepts official Chinese names. Lists show the Chinese name, English ID, and range, with clickable edit/remove commands. New entries require `preStackDroppedItems=true`; legacy entity rules remain independent.

## `/villagerPerformance`

### Related rules

`villagerPerformanceOptimization`, `wanderingTraderNoDespawn`

### Syntax

```text
/villagerPerformance help
/villagerPerformance status
/villagerPerformance trade false|ai|static
/villagerPerformance trade name add|remove <name>
/villagerPerformance trade name list [page]
/villagerPerformance trade block add|remove <block id>
/villagerPerformance trade block list [page]
/villagerPerformance gift false|true
/villagerPerformance gift name add|remove <name>
/villagerPerformance gift block add|remove <block id>
/villagerPerformance gift list [page]
/villagerPerformance wanderingTrader false|true|controlled
/villagerPerformance wanderingTrader name add|remove|list <name>
/villagerPerformance wanderingTrader block add|remove|list <block id>
```

Changes apply immediately and are saved to the world configuration. In `controlled` mode, a matching custom name or foot block protects the trader; empty lists protect nobody. List commands are paged.

## `/fakePlayerItemSort` and `bot_sort`

The sorter core is registered on Minecraft `1.21-26.2`. Dashboard/API, disk route cache, inventory rebuild, automatic restock, and worker tuning remain `1.21.1` only.

```text
/fakePlayerItemSort status
/fakePlayerItemSort help
/fakePlayerItemSort mode summon|quickopen
/fakePlayerItemSort setting <name> <value>
/fakePlayerItemSort whitelist add|remove <player>
/fakePlayerItemSort whitelist list [page]
/fakePlayerItemSort format prefix|suffix <text>
/fakePlayerItemSort format status
/fakePlayerItemSort name set <item id> <name>
/fakePlayerItemSort name remove <item id>
/fakePlayerItemSort name list [page]
/fakePlayerItemSort name reload
/fakePlayerItemSort workers <initial> <cached>  # 1.21.1 only
/fakePlayerItemSort dashboard status  # 1.21.1 only
/fakePlayerItemSort dashboard port <1024-65535>  # 1.21.1 only
/player <fake> bot_sort
/player <fake> bot_sort continuous
/player <fake> bot_sort stop
/player <fake> bot_sort restart <item name>
/player <fake> bot_sort restart all
/player <fake> bot_sort restart all confirm
```

`restart all` requires a second confirmation through the clickable button or the `confirm` subcommand. With `opall`, the all-inventory rebuild is OP-only. `quickopen` does not summon target fake players; `summon` uses online fake players. Armor slots are never read or written.

## `/minecart` and `/fga minecart`

### Related rules

`fireworkMinecartBoost`, `chainMinecartBinding`, `minecartFeatureCommandPermission`

### Syntax

```text
/minecart help
/minecart status
/minecart firework set <max speed> <duration per flight gt> <deceleration>
/minecart firework reset
/minecart chain set <max distance>
/minecart chain reset
```

The default firework settings are `1.2 10 0.02`. Flight levels 1/2/3 hold full speed for 10/20/30gt before linear deceleration. Use a firework while riding a normal minecart; survival consumes one rocket. Only vanilla sound and particles are emitted, with no firework entity.

The default chain distance is `1.0` block. Use a chain on two normal minecarts in sequence to link or unlink them. Each cart has at most two links, and branches and cycles are rejected. Links break and refund paid chains beyond 16 blocks, across dimensions, or when a cart is destroyed. Persisted links do not force-load chunks.

With permission `false`, the commands are hidden. `true`/`0` allows everyone, `ops` allows operators, and `1-4` uses command permission levels. Ranges are speed `0.1-4.0`, duration `1-24000gt`, deceleration `0.001-1.0`, and chain distance `1.0-8.0`.

## `/vehicleStop` and `/fga vehicleStop`

### Related rule

`vehicleStopOnDismount`

### Syntax

```text
/vehicleStop help
/vehicleStop status
/vehicleStop set minecart|boat|all true|false
/vehicleStop reset
/vehicleStop player <online player> status
/vehicleStop player <online player> set minecart|boat|all true|false
/vehicleStop player <online player> reset
```

Players can manage only themselves. Operators and the console can manage online players. Personal settings are always saved but become effective only in `custom` mode, where unconfigured players default to disabled. Only horizontal speed is cleared when the controlling passenger dismounts. Passenger dismounts do not trigger stopping. An unoccupied chain train stops as a whole; if another player remains aboard, the train keeps moving.

## `/log playerHealth`

### Related rule

`playerHealthDisplay`

### Syntax

```text
/log playerHealth
```

### Behavior

- `playerHealthDisplay=true`: every viewer sees health for real players and fake players in the Tab list.
- `playerHealthDisplay=false`: health is hidden by default; after subscribing, only the executing player sees it.
- `playerHealthDisplay=nofake`: real-player health is visible, but fake-player health remains hidden even for subscribers.
- Running the command again removes the current player's subscription.
- Health is appended at the far right of the multiplayer player-list name. A gold absorption segment is added when absorption is greater than zero.
- It does not create scoreboards, nametag text entities, or periodic chat output.

### Permission and version

This is a Carpet Logger player-subscription command. The subscription only affects the player who runs it. The rule is available on `1.16.5+` and requires Carpet on the server.

## `/playerLoadDistance` and `/fga playerLoadDistance`

Related rule: `playerLoadDistance`, Minecraft `1.21.1` only

```text
/playerLoadDistance help
/playerLoadDistance status <online-player>
/playerLoadDistance set <online-player> <distance> [persistent]
/playerLoadDistance reset <online-player> [persistent]
```

`<distance>` accepts `-1`, `0`, `1-32`, or `none`. Temporary settings disappear on restart. `persistent` requires OP and stores the UUID-based record in `world/config/carpetfgaaddition/player-load-distance.json`. Normal players may change only themselves; changing another online player or removing another player's persistent record requires OP. Tab completion suggests online players and distance values. The help and status output explains that the distance controls chunk sending and tracking, not simulation distance. Active overrides are shown as a leftmost player-list prefix, and each joining player receives the persistent-record summary

## `/trialStop` and `/fga trialStop`

### Related rules

`trialStopCommandPermission`

### Syntax

```text
/trialStop help
/trialStop range <radius> [none|reward|fast] [clear]
/trialStop range from <from XYZ> <to XYZ> [none|reward|fast] [clear]
/trialStop dimension <dimension ID> range <radius> [none|reward|fast] [clear]
/trialStop dimension <dimension ID> range from <from XYZ> <to XYZ> [none|reward|fast] [clear]
/fga trialStop help
/fga trialStop range <radius> [none|reward|fast] [clear]
/fga trialStop range from <from XYZ> <to XYZ> [none|reward|fast] [clear]
/fga trialStop dimension <dimension ID> range <radius> [none|reward|fast] [clear]
/fga trialStop dimension <dimension ID> range from <from XYZ> <to XYZ> [none|reward|fast] [clear]
```

`range <radius>` uses the command source as the center of a horizontal cylinder and ignores Y; the radius is measured in blocks. Tab completion offers `16`, `32`, and `64` presets, while other valid values can still be entered manually. A console can choose the center with `/execute positioned`. `range from` uses a full XYZ box, with Tab suggestions for relative coordinates, the player's position, and the targeted block. Only currently loaded chunks are scanned

The reward mode defaults to `none`. `none` skips rewards and refreshes immediately, `reward` preserves vanilla opening and per-ejection timing then refreshes immediately, and `fast` ejects everything and refreshes immediately. `clear` removes only loaded mobs tracked by the spawner. Omitting the `dimension` branch uses the current dimension; use the leading `dimension <dimension ID>` branch for another dimension, with dimension ID Tab completion. `INACTIVE` spawners remain inactive after residual data is cleared, while all other states return to waiting for players without a full cooldown

## `/deepslateStonecuttingRecipes`

This feature is controlled by `/carpet deepslateStonecuttingRecipes false|true` on versions `1.17.1-1.21.11`. It only toggles FGA's own direct deepslate stonecutting recipes. There is no standalone command

## Other commands

```text
/fga help
/fga status
/fga droppedItemStackLimit <subcommand>
/fga dropPreStack <subcommand>
/fga villagerPerformance <subcommand>
/fga fakePlayerItemSort <subcommand>
/fga player <fake> <subcommand>
```

Help messages use gray command text and gold descriptions with clickable command insertion. `/log playerHealth` only toggles the current player's Tab subscription and does not send periodic chat output. Inventory advancement optimization is a hidden internal feature with no standalone command.
