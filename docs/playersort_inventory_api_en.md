# Playersort inventory and Easy Place refill API v1

Baseline: Minecraft 26.3 with this FGA build on the server
Fabric PLAY CustomPayload protocol, not an HTTP mutation API
A client addon can implement the protocol without installing FGA; FGA does not supply the projection-client UI

## Contract

### Optional exact-quantity channels (26.3)

C2S `playersort/query_amount` (`AmountQuery`): version VarInt, requestId Long, itemId UTF256, cursor VarInt, amount VarInt. C2S `playersort/take_amount` (`AmountTake`): version VarInt, requestId Long, token UTF64, itemId UTF256, amount VarInt, silent Boolean. Version remains1; the original channel codecs, DTO constructors and Java entrypoints remain compatible.

Amount0 retains server half-stack sizing; 1–2304 specifies an item count, with out-of-range values returning INVALID_REQUEST. Empty token selects the plain Chinese source directly; a query token selects the original source. The silent flag selects offline withdrawal. Queries find one source with enough matching components, and continuation must retain item and amount. Replies retain `material`/`take` shapes and report actual takeCount/moved. No partial debit for insufficient source stock or recipient space, and no cross-source combining. Permissions, rate/replay guards, locks and persistence checks are shared. Replay identity includes amount and silent flag. Older clients keep half-stack behavior; clients require both advertised optional channels for custom quantities.

Manual acceptance additions (not run): independently set Easy Place and printer amounts1,32,64,128,0; verify replies and inventory deltas. With16 items, amount1 succeeds and32 fails. Test full inventory, simultaneous demands serialized, repeat nonce without duplicate debit, summon/silent modes, overflow fallback and permission revocation.

### Primary inventory restock and empty-box returns (26.3)

Material queries try the primary first, then existing overflow names in descending suffix order (`_999` to `_1`). Missing primary/intermediate files do not prevent taking from the last overflow.

While the item-sort rule is enabled, the server polls registered ordinary-material primaries and primaries discovered by API queries/takes. Below **1152 loose items** in the primary main inventory, matching material is extracted from overflow main inventory, offhand and shulker contents, highest existing suffix first. Destination slots are **9–35** (capacity **1728** for ordinary 64-stack items), subject to available space, components and stack limits. Missing primary files are not created; the API can still withdraw directly from overflow.

Maintenance never summons fakes. Verified offline records and existing live fake inventories are supported. Emptied overflow boxes go to the fake named exactly **`潜影盒`**, independently of the crafting depot `潜影盒补货`. Colors, names and components are retained; boxes with remaining unrelated contents stay at the source. An absent, invalid, busy or full recipient leaves empty boxes at the donor. Deferred returns retry at least 10 seconds apart per primary, including after primary restock is complete. Pending returns are memory-only.

At most one primary job starts per second, reading up to four donors and one box recipient. API-associated primaries have priority. Directory indexing is limited to 65536 filenames; one job attempts up to 32 donor reservations. Empty/invalid offline files are skipped until their file metadata changes. Pending queues hold at most 256 primaries, each with at most 999 existing overflow names. Existing bounded IO, source locks, identity checks and final inventory/file checks apply. Original offline files are backed up as `.dat_old`; failed commits attempt rollback. A rollback failure quarantines involved inventories for the current server run and keeps their locks; inspect logs and all affected records before recovery. Multi-file power-loss atomicity is unsupported.

Manual acceptance (not run in this change): create verified primary, `_1` and `_7` inventories, with 1151 loose items at the primary and sufficient boxed stock at `_7`. Check that `_7` drains first, the primary loose area fills to its available capacity, and emptied boxes reach `潜影盒`. At 1152 no material restock should start. Fill the box recipient, confirm boxes remain, free space and confirm deferred return. Test mixed contents, sparse suffixes, live/offline sources and at least three successive withdrawals, checking item conservation.

### Silent withdrawal extension (26.3)

Optional C2S `playersort/silent_take`, DTO SilentTake, fields: version VarInt=1, requestId Long, token UTF(64), itemId UTF(256). An empty token chooses the plain Chinese-name source directly; a query token chooses the queried source. Replies use the existing `kind:"take"` schema. Permissions, rate limits and replay IDs are shared with normal withdrawals.

Verified offline inventories are read and staged by the bounded IO worker. On the server thread, recheck recipient, permissions, offline source, file version and capacity, then atomically replace the source file and deliver items. Keep the original as `.dat_old`, retain unrelated data and equipment, and never summon the source. Source locks guard other withdrawals, offline sorter operations and Carpet summons. Commit failures attempt rollback; uncertain rollback returns TRANSFER_FAILED. Already-online sources use the original live transfer without closing. Missing channel suspends silent mode. WRITE_FAILED means no transfer or a completed rollback; REQUEST_CANCELLED means the request became inactive. Power-loss atomicity across both inventories remains unsupported.

Normal mode now clears the pending spawn marker as soon as the API observes its spawned fake and when it closes that fake, allowing immediate subsequent refills.

### Direct withdrawal extension (26.3)

New optional C2S channel `carpet-fga-addition:playersort/direct_take`, DTO `DirectTake`. Wire fields: version VarInt=1, requestId Long, itemId UTF(256). The server derives the plain Chinese item name with no configured prefix/suffix and returns the existing `kind:"take"` reply after withdrawing half a stack. No query token or client-supplied name/count is needed.

This shares take permissions, rate limits, request ID sequencing and replay history with existing requests. Each refill uses a fresh ID; a timed-out request must reuse its ID and exact parameters. Offline reading remains bounded and requires a verified fake-player identity; mutations use live inventories and the existing transaction. A missing, insufficient or invalid source can fall back to kind=2 material queries. Do not retry uncertain transfers automatically. Servers without this optional channel retain the query/token flow. If FGA already registered this codec on the client, reuse its DTO.

Client queries trigger replies; there are no unsolicited stock broadcasts
Material queries search both cached routes and names generated from the current language/custom-name/prefix/suffix configuration, including `_box` and mixed-box sources
Existing suffixes `_1` through `_999` are checked even if the primary or intermediate files are absent
Only identity-verified sources with a valid complete snapshot are remembered; old cached routes are not overwritten and newly discovered mappings use the existing normal-shutdown persistence
A take uses an item ID and returns exactly half of the server-defined stack limit into the requesting player's main inventory: 64 -> 32, 16 -> 8, unstackable -> 1
This baseline caps the effective stack limit at64 and a withdrawal at32; expanded container stacking does not enlarge refills
It can combine matching components across one fake player's main inventory, offhand and shulker contents
Empty boxes and unrelated contents remain intact; no recursive extraction or extraction from other containers
It does not combine partial quantities from different fake players
The original channels accept no client quantity; the optional quantity extension accepts only a bounded integer, never client stacks, NBT, destination, coordinates or dimension
Offline sources are brought online through Carpet and API-owned temporary fakes leave afterwards
Temporary sources use spectator mode to avoid damage and ground-item pickup; offline RootVehicle records are rejected to avoid moving vehicles
Sources that were already online are not closed
The HTTP dashboard remains read-only and its switch does not affect explicit game API requests

## Permissions

Queries require stock and withdrawals require the new inventoryTake permission, both OP by default
Administrators can authorize a player with:

```text
/fga playersort permission stock PlayerName true
/fga playersort permission inventoryTake PlayerName true
```

Use 0 instead of PlayerName for all ordinary players; false revokes permission

## Channels and wire format

Namespace: carpet-fga-addition
Register codecs and the client reply receiver before querying; check server channel support first
If installed FGA already registers these codecs, reuse carpet.fga.PlayerSortInventoryPayloads rather than registering duplicate IDs
Move client reply processing onto the client main thread before modifying UI state

| Direction | ID | DTO |
| --- | --- | --- |
| C2S | playersort/query | PlayerSortInventoryPayloads.Query |
| C2S | playersort/take | PlayerSortInventoryPayloads.Take |
| S2C | playersort/reply | PlayerSortInventoryPayloads.Reply |

Fixed Query field order: version VarInt, requestId Long, kind VarInt, target UTF(256), cursor VarInt
Fixed Take field order: version VarInt, requestId Long, token UTF(64), itemId UTF(256)
Fixed Reply field order: requestId Long, json UTF(8192)
Version is 1; nonnegative requestId values increase across both request types per connection
Only identical retries reuse an ID; different parameters with the same ID are rejected

Query kind 0 lists route bases; target must be empty
Kind 1 inspects a registered fake inventory; target is a name up to64 characters
Kind 2 searches for a full half-stack of a canonical item ID, such as minecraft:stone
Start cursor at0 and follow only the returned nextCursor; -1 means end

## Easy Place integration

Trigger only when the player attempts Easy Place on a schematic target and lacks its required material. Resolve the item ID from the schematic's material requirement, not the drops of the real targeted block. Keep one in-flight request per material and back off after failures. This hook belongs in the client addon; the server cannot inspect the local schematic.

1. On material shortage, send Query(1,100,2,"minecraft:stone",0)
2. SEARCH_CONTINUE requires a new request ID and nextCursor after at least1 second
3. OK returns kind=material, itemId, target, token, available, takeCount and expiresInMs
4. Send Take(1,101,token,"minecraft:stone") if a refill is still needed, at least250ms after the previous packet
5. OK returns kind=take, moved=32 and refreshRequired=true
6. Wait for vanilla inventory synchronization, then retry Easy Place
7. Query again before each later withdrawal; a successful take invalidates its snapshot

Do not request per tick or per placement, create client-side items, or optimistically withdraw again after timeout
Identical retries return the cached outcome rather than repeating mutations

Route replies contain entries [{target,itemId}], overflowMax=999 and nextCursor
The listed target is a family base; suffixes _1 through _999 are possible, not guaranteed to exist
Inventory replies contain target, token, expiresInMs, entries and nextCursor
Slots0–35 are main inventory and slot36 is offhand; armor and ender chest are excluded
Empty entries have slot/count; others have itemId/name/count and optional vanilla ItemStack CODEC JSON stack
Large previews use previewOmitted=true; the complete server snapshot still validates withdrawals
Pages share one snapshot and do not extend its30-second lifetime

## Limits and status handling

Per player: one query and one take per second
Global: ten queries and four takes per second;64 sessions and8 pending takes
One bounded IO reader with an8-job queue; each material lookup inspects at most4 files before continuation
Initial material searches index UUID filenames only, off-thread, with a65536-file limit; they do not read arbitrary player inventories
Per item: at most64 name families and4096 existing candidates; a search plan lasts30 seconds
Use only the returned nextCursor, never compute it from the old suffix-number algorithm; expired plans restart at0
Compressed file limit2MiB, NBT budget8MiB, serialized snapshot limit128KiB
Reply limit8192 characters; up to12 inventory slots or24 routes per page, reduced for larger JSON
One snapshot per session,64 cached request outcomes,60-second idle expiry, immediate disconnect cleanup
Ingress packets faster than250ms or excess queued packets can be dropped
Suggested client timeout:3 seconds for queries and15 seconds for takes
After a timeout first retry the original ID and exact parameters, never a new take ID
After REPLAY_EXPIRED inspect inventory before making a new refill decision

RATE_LIMITED, SERVER_BUSY and TARGET_BUSY require backoff and a fresh query
REQUEST_PENDING means the existing request is still running
REQUEST_ID_REUSED and REPLAY_EXPIRED require correcting request bookkeeping
SNAPSHOT_EXPIRED and STALE_SNAPSHOT require a new query
NO_SPACE and NOT_ENOUGH leave inventories untouched
NOT_FOUND means no candidate inventory was found; rejected identities return their protection/identity status
INVENTORY_INDEX_TOO_LARGE stops discovery when the directory exceeds its filename-index limit
PERMISSION_DENIED, DISABLED and UNSUPPORTED_VERSION disable automatic refill
INVALID_ITEM, INVALID_REQUEST, INVALID_CURSOR and INVALID_TARGET indicate invalid input
PROTECTED_TARGET and UNVERIFIED_TARGET prohibit access to that source
LEGACY_OFFHAND rejects obsolete inventory offhand slots before spawning; back up and review migration rather than blindly logging the fake in and out
SPAWN_DISABLED, SPAWN_FAILED and SPAWN_TIMEOUT require checking Carpet fake-player settings
NOT_FOUND, READ_FAILED, INVENTORY_TOO_LARGE, TOO_MANY_ROUTES, REPLY_TOO_LARGE and TRANSFER_FAILED should stop the current attempt, not create an endless retry loop

## Identity, migration and persistence

Direct inventory queries require registered cache names; material queries may also discover server-generated candidate names
Both paths must match deterministic offline UUIDs and pass whitelist/type/busy checks
Carpet shadows representing real players are rejected and never receive the fake-inventory identity marker
Offline files need a matching fgaOfflineSorterName marker
Quickopen files already have that marker; before spawning a legacy summon-mode fake to establish the marker, back up and inspect its identity, legacy offhand slots and vehicle data, and confirm that login is safe
The 26.3 identity Mixin retains this marker only for Carpet fakes; it never marks real players or automatically claims unknown files
Possessed actors and possessed/sorting source fakes are rejected

The 26.3 offhand is read from vanilla equipment; armor is not exposed as stock
Legacy Inventory offhand slots are rejected before any login can overwrite them; this API does not change the existing quickopen save path

The transfer uses vanilla live inventory/save and dat_old backup semantics
It does not guarantee an atomic two-playerdata-file commit across power loss
Back up production worlds, inspect both endpoints after abnormal shutdown, and never blindly replay a take
TRANSFER_FAILED is an unexpected path, not a guarantee that both inventories stayed unchanged; inspect them rather than automatically retrying
Client Easy Place UI integration, crash recovery and other Minecraft versions remain separate validation work
