# Carpet FGA Addition Commands

> Documentation version: `1.4.3`

## Command index

| Command | Related rule | Permission/version | Description |
|---|---|---|---|
| `/player` range actions | `fakePlayerRangeControl` | Carpet player permission/all versions | Runs fake-player range placement, interaction, attack, or continuous tasks. |
| `/droppedItemStackLimit` | `droppedItemStackLimit` | Rule permission/1.21.1+ | Configures ground-item stack limits. |
| `/dropPreStack` | `preStackDroppedItems` | Drop configuration permission/1.20.5-26.1.2 | Configures entity, block, and container pre-stacking. |
| `/villagerPerformance` | `villagerPerformanceOptimization` | Rule permission/1.20.1+ | Configures villager trades, gifts, and wandering-trader protection. |
| `/fakePlayerItemSort` | 1.21.1 sorter rules | `commandPlayer`/1.21.1 | Configures the sorter, whitelist, names, workers, and dashboard. |
| `/player <name> bot_sort` | 1.21.1 sorter rules | `commandPlayer`/1.21.1 | Starts, stops, or rebuilds a target fake player's sorter job. |
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

Registered only on Minecraft `1.21.1`.

```text
/fakePlayerItemSort status
/fakePlayerItemSort whitelist add|remove <player>
/fakePlayerItemSort whitelist list [page]
/fakePlayerItemSort format prefix|suffix <text>
/fakePlayerItemSort format status
/fakePlayerItemSort name set <item id> <name>
/fakePlayerItemSort name remove <item id>
/fakePlayerItemSort name list [page]
/fakePlayerItemSort name reload
/fakePlayerItemSort workers <initial> <cached>
/fakePlayerItemSort dashboard status
/fakePlayerItemSort dashboard port <1024-65535>
/player <fake> bot_sort
/player <fake> bot_sort continuous
/player <fake> bot_sort stop
/player <fake> bot_sort restart <item name>
/player <fake> bot_sort restart all
/player <fake> bot_sort restart all confirm
```

`restart all` requires a second confirmation through the clickable button or the `confirm` subcommand. With `opall`, the all-inventory rebuild is OP-only. `quickopen` does not summon target fake players; `summon` uses online fake players. Armor slots are never read or written.

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
