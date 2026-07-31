# Carpet FGA Addition

Carpet FGA Addition is a server-focused Fabric Carpet extension for fake players, item drops, villagers, command compatibility, and performance utilities. Features are opt-in and disabled by default.

## Supported builds

Version `1.4.0` is available for Minecraft `1.16.5`, `1.17.1`, `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, and `26.2`.

## Rules

### General

- `fakePlayerNameLength=-1|1-128`: fake-player name length; `-1` keeps vanilla limits. 1.18+.
- `fakePlayerRangeControl=false|true`: enables fake-player range placing, interaction, breaking, and continuous tasks.
- `endGatewayRegeneration=false|true`: regenerates destroyed vanilla End gateways without changing nearby blocks. 1.16.5+.
- `wanderingTraderNoDespawn=false|true|controlled`: vanilla behavior, protect all traders, or protect only configured names/foot blocks. 1.16.5-26.1.2.
- `fakePlayerProfilePreload=false|always|adaptive`: asynchronous fake-player profile preload. 1.21.1 only.
- `fgaUnicodeArgumentsSupport=false|true`: allow unquoted Unicode command arguments.
- `recipeBookAlwaysUnlocked=false|true`: keep all registered recipes available. 1.19.4+.
- `inventoryAdvancementOptimization=false|exact`: index `inventory_changed` advancement candidates. 1.19.4+.
- `playerHealthDisplay=true|false|nofake`: append health to the far right of the player list. 1.19.4+.
- `spectatorFreeTeleport=false|true`: let non-OP spectators teleport themselves only. 1.21.1-1.21.5.
- `clientDimensionIds=[overworld,the_nether,the_end]`: replace client-visible dimension IDs. 1.21.1+.
- `removeDialogWarning=false|true`: remove server command/dialog confirmation warnings. 1.21.8+.
- `restorePre26BeeCollisionBox=false|true`: restore the pre-26.2 bee collision box. 26.2 only.

### Villagers and drops

- `villagerBreedingAnimalization=false|true|only`: control direct villager feeding.
- `villagerPerformanceOptimization=false|true|ops|1|2|3|4`: enable villager trade/gift optimization and command access. 1.21.1+.
- `hostileMobInventoryAccess=false|true`: open hostile-mob equipment with an empty-handed sneak right-click.
- `droppedItemStackLimit=false|true|ops|0|1|2|3|4`: enable configurable ground-item stack limits. 1.21.1+.
- `droppedItemMergeDistance=-1|0-16`: configure horizontal ground-item merge distance. 1.21.1+.
- `unlimitedFillCommands=false|true`: remove `/fill` and `/fillbiome` volume limits while keeping vanilla safety checks. 1.21.8+.
- `preStackDroppedItems=false|true`: enable `/dropPreStack` entity, block, and container pre-stacking. 1.21.1-26.1.2.
- `zombifiedPiglinDropReduction=false|goldEquipment|rottenFlesh|all`: remove selected zombified-piglin drops.
- `piglinBarterItemExclusions=false|ironBoots|potions|[item ids]`: exclude selected piglin barter results.

The legacy `preStackMobDeathDrops` and `preStackMobDeathDropsRange` rules are hidden. Use `/dropPreStack entity ...` instead.

### Fake-player sorting, Minecraft 1.21.1 only

- `fakePlayerItemSortMode=false|summon|quickopen`
- `fakePlayerItemSortWhitelist=false|vanillaWhitelist|modWhitelist`
- `fakePlayerItemSortQuickShulker=false|true`
- `fakePlayerItemSortNameFormat=false|autoDetect|prefix|suffix`
- `fakePlayerItemSortTargetLanguage=english|chinese|custom`
- `fakePlayerItemSortShulkerRestock=false|true`
- `fakePlayerItemSortCleanOpenedTarget=false|true`
- `fakePlayerItemSortInventoryRebuild=false|true|opall`
- `fakePlayerItemSortDashboard=false|true`
- `fakePlayerItemSortCpuThreads=0|1|2`
- `fakePlayerItemSortSpeed=4|8|16`

`quickopen` edits offline playerdata without spawning a fake player. `summon` uses Carpet's online fake player. Armor slots are never read or written by the sorter.

## Commands

### Range actions

```text
/player <fake> use range <from> to <to> [options]
/player <fake> use continuous range <from> to <to> [options]
/player <fake> attack range <from> to <to> [options]
/player <fake> attack continuous range <from> to <to> [options]
/player <fake> stop
/player <fake> use|attack range help
```

Options are `pathfinding`, `reach <0.1-64>`, `airPlace`, `ignoreObstruction`, `placeBlock`, `interactBlock`, and `interactSpeed <1-64>`.

### Ground-item stack limits

```text
/droppedItemStackLimit mode all <count>
/droppedItemStackLimit mode black <count>
/droppedItemStackLimit mode whitelist
/droppedItemStackLimit set black <item>
/droppedItemStackLimit remove black <item>
/droppedItemStackLimit set whitelist <item> <count>
/droppedItemStackLimit remove whitelist <item>
/droppedItemStackLimit list [black|whitelist] [page]
/droppedItemStackLimit clear
```

### Drop pre-stacking

```text
/dropPreStack help|status
/dropPreStack entity add|remove|set <entity id> [range]
/dropPreStack entity list [page]
/dropPreStack block add|remove|set <item id> [range]
/dropPreStack block list [page]
/dropPreStack container add|remove|set <block or entity id> [range]
/dropPreStack container list [page]
```

Ranges are `0-16` and default to `1.0`. IDs accept `minecraft:stone` and `stone` forms. On 1.21.1, the same command is available through `/fga dropPreStack`.

### Villager performance

```text
/villagerPerformance help|status
/villagerPerformance trade false|ai|static
/villagerPerformance trade name|block add|remove <value>
/villagerPerformance trade name|block list [page]
/villagerPerformance gift false|true
/villagerPerformance gift name|block add|remove <value>
/villagerPerformance gift list [page]
/villagerPerformance wanderingTrader false|true|controlled
/villagerPerformance wanderingTrader name|block add|remove|list <value>
```

### Inventory optimization

```text
/inventoryAdvancementOptimization status
/inventoryAdvancementOptimization stats
/inventoryAdvancementOptimization verify
/inventoryAdvancementOptimization resetStats
```

### Fake-player sorting on 1.21.1

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
/player <fake> bot_sort|continuous|stop
/player <fake> bot_sort restart <item name>
/player <fake> bot_sort restart all
/player <fake> bot_sort restart all confirm
```

`restart all` requires confirmation. `/log playerHealth` toggles the current player's player-list health subscription. On 1.21.1, `/fga help` and `/fga status` provide the FGA command index and sorter status; `/fga` also redirects the FGA-owned command roots.

## Credits

Designs inspired by MIT-licensed Org Addition, SaveMyRecipeBook, and InventoryAdvancementAccelerator are credited in the bundled `META-INF/NOTICE-*` files.
