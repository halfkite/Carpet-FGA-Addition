# Carpet FGA Addition Commands

> Documentation version: `1.5.16`

## Spectator cross-dimension teleport

`/carpet spectatorFreeTeleport true` keeps spectator self-teleport behavior. Setting the rule to `full` lets players in every game mode use complete `/tp` and `/teleport` commands, including teleporting other players or entities and cross-dimension travel. `full` bypasses TIS and AMS permission wrappers only for these two commands; only the server needs FGA

```text
/tp in <dimension> <x> <y> <z>
/teleport in <dimension> <x> <y> <z>
```

Example: `/tp in minecraft:the_nether 100 64 -20`. This FGA extension syntax teleports the command executor and remains subject to world-bound checks; it is available in every game mode with `full`. Vanilla `/tp <targets> <player>` can teleport targets across dimensions to an online player

On all currently supported build nodes, a dimension can also follow the coordinates to teleport the command executor:

```text
/tp <x> <y> <z> <dimension>
/teleport <x> <y> <z> <dimension>
```

For example, `/tp 1 1 1 minecraft:overworld`. This form keeps the existing `/tp` permission policy: vanilla-authorized users, spectators with `spectatorFreeTeleport=true`, and players in any mode with `full` may use it. Relative coordinates use the executor's original position, and the destination must be within world bounds

<a id="cmd-join-notice"></a>

## Join notice command (joinNotice)

Available on all ten current build versions; only the server needs FGA

```text
/carpet customJoinNotice true|false
/fga joinNotice status
/fga joinNotice preview
/fga joinNotice welcome set <text>
/fga joinNotice welcome clear
/fga joinNotice date set YYYY-MM-DD
/fga joinNotice date enabled true|false
/fga joinNotice date clear
```

<a id="cmd-announcements"></a>

## Server announcement command (announcement)

Available on all ten current build versions. Only the server needs FGA; clients do not need to install it

```text
/carpet serverAnnouncements true|false
/fga announcement help
/fga announcement status
/fga announcement list
/fga announcement info <id>
/fga announcement create <content>                         # create a permanent notice and assign an ID
/fga announcement create id <id> <content>                 # create with a chosen ID
/fga announcement header set <text>
/fga announcement header clear                             # restore "服务器公告如下"
/fga announcement content set <id> <content>
/fga announcement expiry set <id> forever|<number>h|<number>d
/fga announcement enable <id>
/fga announcement disable <id>
/fga announcement hide <id>
/fga announcement show <id>
/fga announcement delete <id>
/fga announcement trigger join <id>
/fga announcement trigger region <id> <dimension> <x1> <y1> <z1> <x2> <y2> <z2>
/fga announcement reload
```

Editing requires permission level 2 or higher. `help`, `status`, `list` and `info` are readable by players with access to `/fga`. `create` assigns an ID and creates a permanent announcement by default. The literal `/n` in content becomes a line break. An expiry countdown starts when the expiry is set; `forever` removes the expiry

The default trigger is player join. `trigger region` switches the announcement to a 3D coordinate cuboid in the selected dimension. It fires once when a player enters; leaving and entering again fires it again. Use Tab to select a dimension currently loaded by the server, such as `minecraft:overworld`

`list` shows one announcement per line; click a row to inspect it. Delete, enable/disable and hide/show buttons on the detail page execute immediately. Content, expiry and region buttons put a command in chat for editing. Join announcements require the global rule to be on and the entry to be enabled, visible and unexpired

The file is stored at `world/config/carpetfgaaddition/announcements.json`. Edit its `header` or an announcement's `content`, then run `/fga announcement reload`. A malformed file is preserved and protected from writes until manually repaired or backed up

Changing the configuration requires OP level 2 or higher; `status` and `preview` are available to players, and preview requires a player executor

Welcome text supports `{player}`, RGB markers such as `&#55AAFF`, `&r` to reset the color, and `\n` for a line break

The opening day is day `0` in the server's local time zone; configuration is saved in the current world's `config/carpetfgaaddition/join-notice.json`

<a id="cmd-food"></a>

## Player status commands (food)

### `/food clear [player]` and `/fga food clear [player]`

Available in Minecraft `1.21+`

- `/food clear`: clears the executor's food and saturation
- `/food clear <player>`: clears the food and saturation of one or more online players
- `/fga food clear [player]`: same as `/food clear [player]`
- Related rule: `foodCommandPermission`
- Default: `ops`; `false` disables the commands, `true` allows all players to clear their own or specified players' food, `onlyself` lets non-OP players clear only themselves, `ops` requires OP level 2 or higher, and `0-4` sets the minimum permission level

<a id="cmd-map-load"></a>

## Map command (mapLoad)

### `/mapLoad` and `/fga mapLoad`

Related rules: `mapLoadCommandPermission`

Available in Minecraft `1.21.1`

Client requirement: none (server side only), player facing text is resolved on the server in the server language

```text
/mapLoad start <mode>
/mapLoad list
/mapLoad pause [player]
/mapLoad resume [player]
/mapLoad stop [player]
```

A mode is always required: `/mapLoad start <mode>` is the only way to start a load (`/mapLoad` on its own only prints the usage). Modes are tab completable and `/fga mapLoad start ...` works the same way; the offhand is used when the main hand is empty, and empty, invalid, locked, and cross-dimension maps are not updated.

- `smooth` (default): smoothness first, at most 384 chunks in flight at once
- `fast`: speed first, at most 1024 chunks in flight at once
- `loaded`: only refreshes chunks that are already loaded, requests and generates nothing, areas that are not loaded stay as they are

Neither request mode changes the cost of rendering itself (rendering always stays inside the 3 millisecond per tick budget); what causes stutter is how many chunks are in flight at once rather than how fast they are requested, so areas that are already generated load at the chunk pipeline's own speed and only terrain that has to be generated is slow. An unknown mode name is rejected with the list of accepted values.

One chat message is sent when the load starts and one when it finishes: the start message gives the area, how many chunks the map covers, and the mode used (a scale 3 map, for example, is 4096 chunks), a progress line is written to chat every 30 seconds while loading (so a long wait cannot be mistaken for a hang given the action bar alone), the finish message reports how long the load took (seconds, or minutes and seconds), and the number of skipped samples is reported when the load ends.

Loading is asynchronous and streamed: samples are refreshed nearest to the player first and the chunks they need are handed to the vanilla chunk pipeline, so the server thread never waits for chunk generation. The action bar shows two numbers: the percentage of refreshed samples, and the chunk progress (for example `Loading map 55% (mode smooth, chunks 2579/4096)`); the denominator is every chunk the map covers (the same number the start message reports) and the numerator is how many chunks have been loaded so far, which only ever grows. A big map spends most of its time on chunk generation, so the percentage moves slowly while the chunk counter is the number that keeps changing, and `mapLoad list` additionally labels each task as loading or waiting for chunks.

`list` prints one line per running task (player, progress, state) with clickable `[Pause]`/`[Resume]` and `[Stop]` buttons; `pause`, `resume`, and `stop` target yourself unless a player name is given (which needs a player), and with a player name they can also be run from the server console or RCON; controlling another player needs OP 2, and player names are tab completable from the running tasks. Pausing stops requesting new tickets and hands the existing ones back at a limited rate, resuming requests them again, and stopping lets that player start a new load right away. A sample that makes no chunk progress at all for 30 seconds is skipped so a load can always finish.

## Player and fake-player commands (player)

<a id="cmd-player-possession"></a>

### `/player <name> possess`

Effective versions: `1.21+`. Install FGA on the server; both participants may use vanilla clients.

- `/player <name> possess` follows the PlayerControl reference implementation: it swaps the live entities' game state, identity, position, view, inventory, containers and riding relationship while the controller keeps its own connection and command source.
- `/player <target-name> possess stop` ends that session for either participant.
- `playerPossession` defaults to `false`. `true` allows all targets; `onlyfake` restricts everyone, including OPs, to fake targets; `opreal` restricts real targets to OPs; `ops` restricts all possession to OPs. Carpet `commandPlayer` also applies.
- Both entities and UUIDs remain in place. They continue normal world simulation, and damage, movement, experience and item changes during the session remain with the current entity state. Stopping swaps the states back without copying or writing offline player data.
- Self-possession, overlapping sessions and nested possession are rejected. PlayerControl's fake-player `kill()` lifecycle releases the session; FGA handles disconnects, permission loss and rule shutdown.
- In Minecraft 1.21.1, logout ends possession before the player is saved and removed. Each body retains its current position when ownership is restored; logout does not bring the bodies together.

- While enabled, the shared `/player` name suggestions are filtered by possession permissions. Participants cannot start or continue Carpet automatic manipulation tasks, while the stop command remains available.
- No custom payload or persistent possession configuration is added, and offline player data is not edited. See `scripts/tests/possession.md` for manual multiplayer acceptance checks.

<a id="cmd-control-player"></a>

### `/controlPlayer` and `/fga controlPlayer`

These read-only commands are available while `playerPossession` is enabled and do not require OP

- `/controlPlayer list`: lists every active possession relationship from controller to possessed player; names are clickable for a targeted query
- `/controlPlayer`: shows the executor's own possession relationship
- `/controlPlayer <player name>`: shows whether an online player is controlling or being controlled
- `/fga controlPlayer ...`: equivalent to `/controlPlayer ...`

<a id="cmd-playertpend"></a>

### `/playertpend`

First run `/carpet PlayerTpEndControl control`. `enter` is an End entrance portal, `exit` is the main-island End exit portal, and `gateway` is an End gateway.

```text
/playertpend status [player]
/playertpend set <enter|exit|gateway> <allow|deny>
/playertpend set <player> <enter|exit|gateway> <allow|deny>
/playertpend reset [enter|exit|gateway]
/playertpend reset <player> [enter|exit|gateway]
```

Preferences are saved by UUID at `world/config/carpetfgaaddition/player-tp-end-control.json`. Operators may modify any online player; non-operators may modify themselves and online Carpet fake players.

## World and terrain commands (world)

<a id="cmd-regenerate-terrain"></a>

### `/regenerateTerrain`

Related rules: `voidWorldGeneration`, `terrainRegenerationCommandPermission`

```text
/regenerateTerrain create from <x1> <z1> <x2> <z2>
/regenerateTerrain create radius <radius>
/regenerateTerrain clear from <x1> <z1> <x2> <z2>
/regenerateTerrain clear radius <radius>
/regenerateTerrain create|clear dimension <dimension> from|radius ...
/regenerateTerrain list [page]
/regenerateTerrain confirm <taskId>
/regenerateTerrain run <taskId>
/regenerateTerrain cancel <taskId>
/regenerateTerrain retry <taskId>
```

`from` takes block coordinates and `radius` takes a chunk radius around the chunk the player stands in. The range always expands outward to whole chunks. Every coordinate argument offers Tab suggestions for the player's position and targeted block, and previews show the exact chunk count and effective block range; the green confirmation button starts the task immediately, with no restart needed. `create` only marks the target chunks; they generate normally the next time they are loaded, so a player standing inside the area has to leave first and will see the new terrain after coming back. `clear` reads an all-air network payload into every section palette, clears block entities, non-player entities, POI, scheduled ticks, heightmaps, and lighting data, and removes adjacent fluids within eight blocks outside the effective horizontal border, covering the maximum horizontal spread of vanilla water and Nether lava; waterlogged blocks keep the block and lose only their waterlogged state. Region files touched by the clear range or its border are backed up before execution. A failed task can be retried without overwriting its original backup. A single task (including merged ones) cannot exceed 16384 chunks; oversized drafts cannot be confirmed, and legacy oversized tasks are marked failed on load.

## Player and fake-player range commands (player)

<a id="cmd-player-rejoin"></a>

### Enhanced `/player <name> rejoin`

Requires Carpet TIS on the server and `enhancedFakePlayerRejoin=true`; permissions follow Carpet `commandPlayer`

```text
/player <name> rejoin
/player <name> rejoin at <x> <y> <z> [in <dimension>]
/player <name> rejoin at <x> <y> <z> facing <yaw> <pitch> [in <dimension>]
```

The no-argument command restores the saved location, vehicle and non-player passengers. `at` moves the whole restored vehicle stack. Without a dimension, the command source's dimension is used; without facing, the saved rotation is retained. Disabling the rule leaves TIS's original no-argument command available.

<a id="cmd-player-range"></a>

### `/player` range actions

#### Related rule

`fakePlayerRangeControl`

#### Syntax

```text
/player <fake> use range <from> to <to> [options]
/player <fake> use continuous range <from> to <to> [options]
/player <fake> attack range <from> to <to> [options]
/player <fake> attack continuous range <from> to <to> [options]
/player <fake> stop
/player <fake> use|attack range help
```

Options can be combined: `pathfinding`, `reach <0.1-64>`, `airPlace`, `ignoreObstruction`, `placeBlock`, `interactBlock`, and `interactSpeed <1-64>`.

## Items and entities (items)

<a id="cmd-dropped-item-stack-limit"></a>

### `/droppedItemStackLimit`

#### Related rule

`droppedItemStackLimit`

#### Syntax

```text
/droppedItemStackLimit mode all <count>
/droppedItemStackLimit mode black <count>
/droppedItemStackLimit mode whitelist
/droppedItemStackLimit mode inventory <count>
/droppedItemStackLimit mode container <count>
/droppedItemStackLimit reset inventory
/droppedItemStackLimit reset container
/droppedItemStackLimit set black <item id>
/droppedItemStackLimit remove black <item id>
/droppedItemStackLimit set whitelist <item id> <count>
/droppedItemStackLimit remove whitelist <item id>
/droppedItemStackLimit list [black|whitelist] [page]
/droppedItemStackLimit clear
```

`list` is paged and shows the display name, full item ID, and count. List entries provide clickable removal commands. Invalid configuration keeps vanilla-safe limits and rejects writes.

On all ten current build versions (`1.21.1` through `26.3`), saved `inventoryLimit` / `containerLimit` values are separate from activation: disabling the main rule, including across a restart, stops applying them without clearing them; re-enabling restores them.

<a id="cmd-entity-drop-removal"></a>

### `/entityDropRemoval` and `/fga entityDropRemoval`

Related rule: `entityDropRemoval`

```text
/entityDropRemoval help
/entityDropRemoval status
/entityDropRemoval set <entity id> <item id|allEquipment>
/entityDropRemoval remove <entity id> <item id|allEquipment>
/entityDropRemoval enableAllDrops <entity id>
/entityDropRemoval disableAllDrops <entity id>
/entityDropRemoval list
/entityDropRemoval list <entity id>
```

The command is unavailable when the rule is `false`; `true`, `ops`, and `0-4` control access according to the rule value. Entity and item IDs support full namespaces, omitted `minecraft:`, and Tab completion. `set` adds to existing entries, while `remove` deletes only one entry. `allEquipment` covers the helmet, chestplate, leggings, boots, main-hand, and off-hand slots. `list` shows configured entities and removal entries with clickable red minus buttons; `list <entity id>` shows the default loot-table ID and currently identifiable drop configuration. The file is `world/config/carpetfgaaddition/entity-drop-removal.json`, written with atomic replacement; corrupt files are preserved and writes are rejected for the current run.

`enableAllDrops <entity id>` clears every removal setting for that entity. `disableAllDrops <entity id>` persists a rule that removes all item and equipment drops for that entity. Both actions are available as buttons at the end of `list <entity id>`.

<a id="cmd-piglin-barter-customization"></a>

### `/piglinBarterItemExclusions` and `/fga piglinBarterItemExclusions`

Related rule: `piglinBarterItemExclusions`

Available in Minecraft `1.21.1`

```text
/piglinBarterItemExclusions list
/piglinBarterItemExclusions add <entry>
/piglinBarterItemExclusions enable <entry>
/piglinBarterItemExclusions disable <entry>
/piglinBarterItemExclusions set <entry> <probability> <min>-<max>
/piglinBarterItemExclusions reset <entry>
```

`/fga piglinBarterItemExclusions ...` is equivalent to the direct command, and the command is hidden from player command trees while the rule is disabled. `list` shows concrete variants from the current loot table, the client-language name, English ID, probability, and quantity range. Default entries removed by the current loot table remain in the disabled section. Click probability or quantity to suggest an edit command, use the red `[-]` to disable an entry, the green `[+]` to enable it, and the final `[+]` to open an add command with Tab completion. Reset restores only the default probability and quantity and preserves the enabled state. Configuration is saved at `world/config/carpetfgaaddition/piglin-barter-customization.json`.

<a id="cmd-drop-pre-stack"></a>

### `/dropPreStack` and `/fga dropPreStack`

#### Related rule

`preStackDroppedItems`

#### Syntax

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

<a id="cmd-villager-performance"></a>

### `/villagerPerformance`

#### Related rules

`villagerPerformanceOptimization`, `wanderingTraderNoDespawn`

#### Syntax

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

<a id="cmd-fake-player-item-sort"></a>

### `/fakePlayerItemSort`, `/fga playersort`, and `bot_sort`

26.3 provides an on-demand inventory and Easy Place half-stack API; see [Inventory API](playersort_inventory_api_en.md). Queries use `stock` and withdrawals use the separate `inventoryTake` permission, both OP-only by default. Grant either with `/fga playersort permission <permission> <player> true`.

Optional 26.3 `query_amount` / `take_amount` channels reuse these permissions and accept0 (half a stack) or1–2304 items, with normal or silent withdrawal. Invalid quantities are rejected; insufficient single-source stock or recipient capacity causes no partial debit. halfmasa exposes separate Easy Place/printer permission, quantity and silent children under Extensions.

On 26.3, enabling the sorter also maintains ordinary-material primary inventories: below 1152 loose items, transfer matching stock from the highest existing overflow suffix into slots 9–35 (up to 1728 ordinary items). Insufficient primaries also fall back to descending overflow order in material queries. Empty donor boxes return to the inventory named exactly `潜影盒`; unavailable or full recipients leave boxes at the donor for retry. Maintenance uses verified inventories without summoning fakes. This is separate from crafting-depot `autoCraft`; see the API document for bounds and manual acceptance.

The sorter core is registered on Minecraft `1.21+`. The existing Dashboard/API, disk route cache, inventory rebuild, automatic restock, and worker tuning are retained; the base management commands are available on `1.21.1` and `26.3`. Minecraft 26.3 additionally has the setup wizard, per-command permissions, regex stock search/text export, and refactored shulker routing. This refactor is gated to `26.3`; older versions keep their existing behavior.<br>

On 26.3, setup item 11 controls the dashboard with `/fga playersort set dashboard false|true|login`: `false` (default) stops HTTP listening and automatic dashboard snapshots; `true` allows access without login; `login` protects the page, both inventory APIs and the stock text export with HTTP Basic authentication. Set a password first using `/fga playersort set dashboard password <password>`; the username is `fga`. Use 12–128 printable ASCII characters without spaces. Only a salted PBKDF2 hash is saved, and changing the mode/password takes effect without restarting. The dashboard continues to bind only to `127.0.0.1`; clients using its API must supply credentials in login mode. Console password setup is recommended. Explicit in-game stock queries remain available when the dashboard is off.

All eleven 26.3 settings are grouped under `/fga playersort set`; running it without arguments displays the setup panel. Language also retains `/fga playersort language` as a shortcut. The 26.3 `/fakePlayerItemSort` alias uses the same layout; legacy outer mode/setting/format/workers/dashboard syntax below applies only before 26.3. Administration of lists, item names, stock and permissions remains at the root.
On 26.3, `/fga playersort` defaults to OP level 2 and console; individual command permissions can be changed with `permission`. Older versions continue to use Carpet's `commandPlayer` permission model.

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
/fakePlayerItemSort workers <initial> <cached>  # legacy: 1.21.1
/fakePlayerItemSort dashboard status  # legacy: 1.21.1
/fakePlayerItemSort dashboard port <1024-65535>  # legacy: 1.21.1
/fga playersort set  # 26.3: display settings
/fga playersort set mode summon|quickopen
/fga playersort set whitelistMode false|vanillaWhitelist|modWhitelist
/fga playersort set inventoryRebuild false|true|opall
/fga playersort set format prefix|suffix <text>
/fga playersort set workers <initial> <cached>  # legacy fields; cpu controls actual workers
/fga playersort set dashboard false|true|login
/fga playersort set dashboard password <password>
/fga playersort set dashboard status
/fga playersort set dashboard port <1024-65535>
/fga playersort setup  # 26.3: display the setup wizard
/fga playersort set language chinese|english|custom  # 26.3: set sorter language
/fga playersort language chinese|english|custom  # 26.3: language shortcut
/fga playersort set summonNotices true|false  # 26.3: broadcast sorter fake-player join/leave messages in game; console logs remain
/fga playersort set prefix default|off  # 26.3: use bulk_ or disable the prefix
/fga playersort set prefix custom <text>  # 26.3: set a custom prefix
/fga playersort set quickShulker true|false  # 26.3: split contents or sort boxes as boxes
/fga playersort set autoCraft true|false  # 26.3: configure empty-shulker restock/crafting
/fga playersort set cleanOpenedTarget true|false  # 26.3: route unrelated items found in a target
/fga playersort set speed <ticks>  # 26.3: interval from 1-120 ticks; 4/8/16 have Tab suggestions
/fga playersort set cpu 0|1|2  # 26.3: 0 uses half available CPUs; 1 and 2 select worker count
/fga playersort set cpu custom <threads>  # 26.3: custom worker count from 1-256, capped to available CPUs
/fga playersort stock list <regex> [page]  # 26.3: search cached stock
/fga playersort stock list all  # 26.3: export all stock to a text file
/fga playersort permission <command> <ops|0-4|player> <true|false>  # 26.3: set command access
/player <fake> bot_sort
/player <fake> bot_sort continuous
/player <fake> bot_sort stop
/player <fake> bot_sort restart <item name>
/player <fake> bot_sort restart all
/player <fake> bot_sort restart all confirm
/player <fake> bot_sort restart stop
```

On 26.3, `set` offers language, mode, summonNotices, prefix, quickShulker, autoCraft, whitelistMode, cleanOpenedTarget, speed, cpu and dashboard with matching value suggestions. Dashboard accepts false|true|login, speed accepts 1–120 ticks and cpu custom accepts 1–256. Internal aliases targetLanguage, shulkerRestock, cpuThreads and inventoryRebuild remain available through `set <name> <value>` using the existing settings permission; named setting branches retain their original feature permissions and accept legacy settings grants for previously supported options; password and port changes still require dashboard permission. Earlier versions retain their existing setting commands and values.

On 26.3, first enablement prompts for the sorter language; the other ten settings appear after a language is chosen. Setting 3 controls whether sorter-summoned fake-player join/leave messages are broadcast to in-game chat; when off, the dedicated-server console still records them. Clicking an option only suggests its command in chat. Settings and completion state are saved in the world configuration. The interval setting also batches loose-item transfers; the temporary shulker-restock fake logs out after its restock/cleanup task, including when profile preloading delays its login. With quick shulker handling enabled, contents are routed by item and numbered targets are used when the primary inventory fills. When disabled, loose items, full single-item boxes, and mixed/partial boxes use item, `_box`, and mixed-box routes respectively. The default prefix is `bulk_`. Whitelisting can use either the vanilla server list or the FGA built-in list.

For 26.3, `<regex>` matches item names, item IDs, and target fake-player names; `stock list all` writes a text file under the world configuration directory. `restart all` must be confirmed within 30 seconds and is queued with rate limiting; `restart stop` cancels pending rebuilds for that fake player. Per-command access supports `all`, command names, levels `0-4`, `ops`, and specific player names. OP level 2 and above are allowed by default; console access is always allowed.

Command responses and sorter notifications use the FGA client's language when that client has FGA installed. Clients without FGA see the server language selected by `/carpet language`. Commands in `help` are clickable and only inserted into chat; they are not executed.

On 26.3, `stock list all` retains translatable success feedback but sends its export path as a string, avoiding a system-chat packet encoding failure caused by a Java Path argument. The TXT file is saved on the server, not automatically downloaded to the client.

`restart all` requires a second confirmation through the clickable button or the `confirm` subcommand. With `opall`, the all-inventory rebuild is OP-only. `quickopen` does not summon target fake players and writes their offline playerdata directly; `summon` logs target fake players in temporarily and logs them out after the current batch, saving their items to playerdata for their next login. Armor slots are never read or written. Sorter targets use the deterministic offline profile for their name, seeded into the server profile cache before the summon, so localized target names never trigger a main-thread Mojang profile lookup.

On 26.3, `speed` is the number of game ticks between transfer attempts; smaller values are faster, and failed attempts also wait. Each shulker split transfers at most 64 matching items, combining matching inner slots in one transfer. Queued jobs are not planned again, and stopped or replaced jobs cannot commit stale plans. The empty-box collector stays online while the source still contains boxes to unpack. Increasing the tick rate increases attempts per second without changing the per-tick limits.

26.3 uses vanilla's `world/players/data` directory. If an older FGA build wrote a sorter inventory with the same UUID under `world/playerdata`, sorting refuses further writes to that target and preserves the legacy file. Reconcile both inventories before recovery; do not overwrite or delete either copy blindly.

The empty-shulker restock fake (`box_restock`, or `潜影盒补货` in Chinese) now lives for the whole sorting round: while any sorting job still runs it stays online, a restock attempt that fails for missing material no longer logs it out, and it crafts once logs and shells arrive later in the round. It only logs out when the round is over (the source inventory is drained, no sorting job left) or when the rule is disabled. A depot the player summoned manually is never logged out by this logic.

The depot reads boxed material: a shulker box in its inventory counts as depot material while it holds only logs or shulker shells, so such boxes are never treated as foreign items. Crafting takes 2 logs and 2 shells straight out of those boxes, which means the depot keeps producing empty boxes even when all 36 slots are filled with material boxes and no slot is free. A box that runs empty stays in place as a usable empty box. The same `shulkerRestock` switch still gates all crafting.

## Minecart and vehicle commands (vehicle)

<a id="cmd-minecart"></a>

### `/minecart` and `/fga minecart`

#### Related rules

`fireworkMinecartBoost`, `chainMinecartBinding`, `minecartFeatureCommandPermission`

#### Syntax

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

<a id="cmd-vehicle-stop"></a>

### `/vehicleStop` and `/fga vehicleStop`

#### Related rule

`vehicleStopOnDismount`

#### Syntax

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

## Logger commands (logger)

<a id="cmd-player-health"></a>

### `/log playerHealth`

#### Related rule

`playerHealthDisplay`

#### Syntax

```text
/log playerHealth
```

#### Behavior

- `playerHealthDisplay=true`: every viewer sees health for real players and fake players in the Tab list.
- `playerHealthDisplay=false`: health is hidden by default; after subscribing, only the executing player sees it.
- `playerHealthDisplay=nofake`: real-player health is visible, but fake-player health remains hidden even for subscribers.
- Running the command again removes the current player's subscription.
- It does not create scoreboards, nametag text entities, or periodic chat output.

#### Permission and version

This is a Carpet Logger player-subscription command. The subscription only affects the player who runs it. The rule is available on `1.21+` and requires Carpet on the server.

## Player loading commands (player)

<a id="cmd-player-load-distance"></a>

### `/playerLoadDistance` and `/fga playerLoadDistance`

Related rule: `playerLoadDistance`, Minecraft `1.21.1` only

```text
/playerLoadDistance help
/playerLoadDistance status <online-player>
/playerLoadDistance set <online-player> <distance> [persistent]
/playerLoadDistance reset <online-player> [persistent]
```

`<distance>` accepts `-1`, `0`, `1-32`, or `none`. Temporary settings disappear on restart. `persistent` requires OP and stores the UUID-based record in `world/config/carpetfgaaddition/player-load-distance.json`. Normal players may change only themselves; changing another online player or removing another player's persistent record requires OP. Tab completion suggests online players and distance values. The help and status output explains that the distance controls chunk sending and tracking, not simulation distance. Active overrides are shown as a leftmost player-list prefix, and each joining player receives the persistent-record summary

## Trial spawner commands (trial)

<a id="cmd-trial-stop"></a>

### `/trialStop` and `/fga trialStop`

#### Related rules

`trialStopCommandPermission`

#### Syntax

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

## Carpet rule entry points (carpet)

<a id="cmd-deepslate"></a>

### `/deepslateStonecuttingRecipes`

This feature is controlled by `/carpet deepslateStonecuttingRecipes false|true` on versions `1.21+`. It only toggles FGA's own direct deepslate stonecutting recipes. There is no standalone command

<a id="cmd-fga"></a>

### Other commands

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
