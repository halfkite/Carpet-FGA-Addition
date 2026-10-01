package carpet.fga;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-maintained smoke-test procedures for feature work and version ports.
 *
 * <p>When a feature is added or changed, update this catalog in the same
 * change as the implementation and the corresponding script. The procedure
 * must name one baseline version, the version gate, the exact commands or
 * actions, and an observable expected result. A build proves compilation only;
 * a gameplay claim belongs here only after the listed smoke test has run.</p>
 *
 * <p>Version-port workflow: run the baseline plan first, archive the tested
 * artifact, obtain confirmation, then copy the plan to each remaining build
 * node and record version-specific differences here.</p>
 */
final class FeatureSmokeTestPlanTest {
    private record SmokePlan(
            String feature,
            String versionGate,
            String baseline,
            String script,
            String procedure,
            String expected) {
    }

    /**
     * Keep every active feature's test flow in source so an upgrade can be
     * repeated from the implementation repository without relying on chat
     * history.
     */
    private static final List<SmokePlan> PLANS = List.of(
            //#if MC == 26.3
            //$$ new SmokePlan("playersortInventoryMissingRouteDiscovery", "MC == 26.3", "26.3",
            //$$         "scripts/powershell/fake-player-item-sort-smoke-26.3.ps1 plus MANUAL: projection client",
            //$$         "In a disposable world omit glass routes, save a Chinese 玻璃 fake with 1728 glass and a matching "
            //$$                 + "identity marker. Keep the requester inventory and carried shulker free of glass. Query minecraft:glass "
            //$$                 + "and withdraw 32; check requester=32 and source=1696. Repeat with custom prefixes/suffixes and only "
            //$$                 + "a boxed _17 source, retaining its box metadata. Query a shortage and an absent item separately. "
            //$$                 + "In a real client, try Easy Place with missing glass, wait for inventory synchronization and verify the same conservation.",
            //$$         "Discovery remembers only verified existing sources, sparse suffixes are found without a primary, "
            //$$                 + "NOT_FOUND and NOT_ENOUGH are distinct, requests cannot claim real/shadow players, and no world-wide inventory scan runs."),
            //#endif
            new SmokePlan(
                    "playersortInventoryHalfStackApi",
                    "MC == 26.3; no older-node payload registration",
                    "26.3",
                    "scripts/powershell/fake-player-item-sort-smoke-26.3.ps1 plus MANUAL: client Easy Place integration",
                    "Run the inventory-api probe in a disposable world. Query material IDs, grant and revoke stock and "
                            + "inventoryTake permissions, withdraw from online and offline fake inventories, repeat a nonce, "
                            + "change its parameters, fill the recipient inventory and invalidate a snapshot. Verify gold-block "
                            + "half stacks are 32 and snowball half stacks are 8, the named shulker is preserved, and the API-owned "
                            + "temporary fake logs out. Compare source playerdata with recipient totals. Separately connect an "
                            + "API client, register the reply channel, request pages only on demand and test Easy Place replenishment; "
                            + "test an unmodified client, rate limits, logout during reads and legacy files without identity markers.",
                    "Exactly one half-stack is moved per successful nonce; denied, full-inventory and stale requests do not "
                            + "debit stock, retries do not duplicate items, containers retain unmatched content and names, and "
                            + "unverified offline files cannot be claimed. No inventory packets are pushed without queries. "
                            + "Vanilla separate player saves do not guarantee atomic recovery after a power failure."),
            new SmokePlan(
                    "fakePlayerItemSort26_3MixedBoxTickBudget",
                    "MC == 26.3 via supportsPlayersortRefactor",
                    "26.3",
                    "scripts/powershell/fake-player-item-sort-smoke-26.3.ps1 -PerformanceOnly",
                    "In a disposable world run eight 27-slot mixed boxes (864 items plus 8 boxes) at speeds 40 and 10, "
                            + "then repeat 40 warm. Measure tick callback time and wall-clock throughput, count collector "
                            + "logins, and verify vanilla PLAYER_DATA_DIR totals after logout. Run quickopen and log its "
                            + "target in through Carpet to verify saved items really load. Run a dense 1728-item mixed box "
                            + "and assert no tick removes more than 64 contents. For an archived baseline, use -BaselineJar "
                            + "and verify the printed implementation path. Also manually stop/restart a pending sorter, "
                            + "block a target and check retry intervals, and supply legacy world/playerdata files to verify "
                            + "writes are rejected without overwriting either directory.",
                    "Items and boxes are conserved; the empty-box collector logs in once per fixture; attempts are "
                            + "bounded and stale plans cannot commit. Separate cold-start maxima from warm tick costs; "
                            + "benchmark tick callback samples do not claim to reproduce the user's world MSPT."),
            new SmokePlan(
                    "fakePlayerItemSort26_3SetupAndPermissionRefactor",
                    "MC == 26.3; older Minecraft nodes retain their existing sorter implementation",
                    "26.3",
                    "MANUAL: disposable 26.3 world, FGA client, vanilla client, and a browser",
                    "Start with a fresh world, enable fakePlayerItemSort, and join with an FGA client. Verify the bilingual "
                            + "first-run notice is sent once and clicking setup only inserts `/fga playersort setup` into chat. "
                            + "Before choosing a language, confirm only the language row is shown; click each option and check "
                            + "that it is suggested but not executed. Choose a language, then check that the remaining rows "
                            + "appear in order: mode, join/leave notices, prefix, quick shulker, auto craft, whitelist, clean opened target, speed, "
                            + "CPU, and dashboard. Verify unconfigured rows are gray, explicitly configured choices are cyan, notes are "
                            + "separate lines, the notice option affects only in-game broadcasts while console records remain, "
                            + "custom prefix accepts text, `/fga playersort set speed <ticks>` accepts a direct 1-120 value, "
                            + "and `/fga playersort set cpu custom <threads>` accepts a custom worker count. Verify help shows this panel "
                            + "before clickable command descriptions. Exercise valid Tab values for every setting, restart the "
                            + "world, and verify choices persist. With a non-OP player, test default denial, `permission sort 0 true`, "
                            + "a per-player false override, and a command-specific override against an `all` rule; confirm OP "
                            + "and console retain access by default and command trees refresh after a permission change.",
                    "The setup is localized, appears only until its eleven fields are configured, never runs a clicked option "
                            + "immediately, and persists across restart. Permissions default to OP level 2, support levels 0-4, "
                            + "ops and player-name overrides with deterministic precedence, and update online command trees."),
            new SmokePlan(
                    "fakePlayerItemSort26_3RoutingAndInventoryConservation",
                    "MC == 26.3; route and shulker refactor is gated to the 26.3 source output",
                    "26.3",
                    "scripts/powershell/fake-player-item-sort-smoke-26.3.ps1 plus MANUAL: isolated item-conservation matrix",
                    "Use a disposable world and record recursive item totals before and after each run. Test both `quickopen` "
                            + "and `summon` with quick shulker handling on and off. With it off, sort loose items, a completely "
                            + "full 27-slot single-item shulker, a mixed shulker, a partial single-item shulker, and an empty "
                            + "shulker; verify the expected `<item>`, `<item>_box`, and mixed-box routes and numbered overflow "
                            + "targets. With it on, fill the primary fake-player inventory first, then sort mixed-box contents "
                            + "and verify they split by item into the base and numbered targets without losing the source box's "
                            + "remaining contents. Repeat with a custom prefix and with cleanOpenedTarget false/true. Exercise "
                            + "speeds 4, 8, 16, and a custom interval for loose stacks; confirm each 16-tick test step moves "
                            + "no more than four loose items while conserving the full stack. Enable restock and exercise an "
                            + "asynchronously preloaded `box_restock` fake with missing materials; wait for its login and "
                            + "logout logs, then confirm it is offline and no pending depot cleanup remains. Also test "
                            + "vanilla and FGA whitelist modes, auto-craft/restock enabled and disabled, a real player occupying "
                            + "a would-be target name, and a failed/blocked summon. Compare loose items plus all nested shulker "
                            + "contents in source inventories and target playerdata; verify no duplicate items, unauthorized "
                            + "real-player inventory writes, or unbounded fake-player cascade.",
                    "Every item and nested shulker content is conserved exactly once in both modes; target names follow the "
                            + "selected language/prefix and shulker mode; blocked targets never cause loss, overwrite, or runaway spawns."),
            new SmokePlan(
                    "fakePlayerItemSort26_3StockRebuildAndWebDashboard",
                    "MC == 26.3; existing read-only web dashboard and cache API remain available",
                    "26.3",
                    "scripts/powershell/fake-player-item-sort-smoke-26.3.ps1 plus MANUAL: isolated browser visual review",
                    "Populate several cached routes. Run `/fga playersort stock list <regex>` with matching and nonmatching "
                            + "patterns, pagination, and an invalid regex; then run `stock list all` and verify the generated "
                            + "text file contains every cached route and quantity. Enable inventory rebuild, request one item, "
                            + "confirm only that route is rebuilt, request `restart all` and verify no work starts before "
                            + "confirmation, confirm once, then separately test the 30-second expiry and `restart stop` on a "
                            + "queued rebuild. Enable the dashboard and open its loopback page; verify search, language selection, "
                            + "zero-stock filter, actions, `/api/cache`, and `/api/v1/inventory` still work. Fetch `/api/v1/stock.txt`, "
                            + "set a dashboard password and enable login mode; verify anonymous and wrong-password requests "
                            + "to the page, both inventory APIs and stock.txt are rejected, correct credentials work before "
                            + "and after config reload, and true mode allows anonymous access again; "
                            + "disable the dashboard, and confirm the listener stops. The isolated smoke script also verifies "
                            + "the page, legacy `/api/cache`, `/api/v1/inventory`, `/api/v1/stock.txt`, and that disabling the "
                            + "dashboard closes the listener. In a browser, visually review search, language selection, the "
                            + "zero-stock filter, and copy buttons. Check there is no mutation endpoint and the listener is "
                            + "bound only to 127.0.0.1.",
                    "Stock search/export and confirmed rebuilds reflect the route cache without changing unrelated inventories; "
                            + "the existing read-only web UI/API works and remains loopback-only; disabling it closes its listener."),
            new SmokePlan(
                    "fakePlayerItemSort26_3CommandPortAndLocalizedFeedback",
                    "MC == 26.3; 1.21.1 retains the existing command set",
                    "26.3",
                    "MANUAL: isolated 26.3 server with an FGA client, a client without FGA, and different client languages",
                    "On 26.3, enable fakePlayerItemSort and inspect `/fga playersort set cleanOpenedTarget <TAB>`; "
                            + "verify that only false and true are suggested. Check targetLanguage, whitelistMode, "
                            + "inventoryRebuild, cpuThreads, speed, shulkerRestock, and dashboard and compare each "
                            + "suggestion list with its accepted values. Check direct numeric `speed <ticks>` and "
                            + "`cpu custom <threads>` syntax, plus `summonNotices` values and public-chat/console behavior. "
                            + "Verify `/fga playersort set workers`, "
                            + "`/fga playersort set dashboard`, and `/player <fake> bot_sort restart` are registered. "
                            + "The root must offer set but not the old setting/mode/speed/dashboard literals; "
                            + "verify both `/fga playersort language` and `/fga playersort set language` paths. "
                            + "Set Carpet language to zh_cn: a client without FGA must receive Chinese feedback, while "
                            + "FGA clients set to English and Chinese must receive their own client-language feedback. "
                            + "Change Carpet language to en_us and confirm the no-FGA fallback changes to English without "
                            + "changing either FGA client's selected language. Click a help command and confirm it is "
                            + "inserted into chat without executing immediately.",
                    "Every setting key suggests only its valid values; the former 1.21.1-only sorter subcommands appear "
                            + "in the 26.3 command tree; command feedback and delayed sorter notices use the FGA client's "
                            + "language when FGA is installed, otherwise the current Carpet server language; clickable help "
                            + "entries only suggest the command."),
            new SmokePlan(
                    "fakePlayerItemSortAsyncSummonInventoryConservation",
                    "MC >= 1.21.1 && MC <= 26.3; isolated service smoke baseline 26.3",
                    "26.3",
                    "scripts/powershell/fake-player-item-sort-smoke-26.3.ps1",
                    "On an isolated 26.3 server, create an empty Carpet fake player and run the probe in quickopen "
                            + "then summon mode with 16 items per run. The summon probe enables asynchronous profile "
                            + "preloading, waits for the target to appear, and the test reads source inventory, online "
                            + "inventory, and the target playerdata before asserting conservation. For each other "
                            + "published Minecraft node, run its test and build tasks; repeat the same inventory steps "
                            + "in an isolated server before claiming gameplay verification for that node. On the 26.3 "
                            + "baseline, also trigger one restock with a temporary `box_restock` fake while async profile "
                            + "loading is enabled; it must log in and then leave after completion, with no pending cleanup.",
                    "quickopen transfers all 16 items to offline target playerdata without logging in a target; summon "
                    + "creates only the requested target fake, transfers all 16 items exactly once, logs it out "
                    + "after the batch, and saves the inventory for its next login. No numbered fake-player "
                    + "cascade or repeated in-flight profile request occurs. On 26.3, an asynchronously preloaded temporary "
                    + "shulker-restock fake also logs out after its cleanup task; it is never left online."),
            new SmokePlan(
                    "tpTrailingDimension",
                    "MC >= 1.21.1 && MC <= 26.3",
                    "26.3",
                    "scripts/powershell/tp-dimension-suffix-smoke-all.ps1; scripts/powershell/spectator-free-teleport-smoke-26.3.ps1",
                    "On an isolated flat server for every published build node, spawn a fake player and execute "
                    + "/tp <x> <y> <z> minecraft:the_nether and /teleport back to the Overworld as that player. "
                            + "Verify the player is present in the expected dimension after each command. On the 26.3 "
                            + "baseline, also run the TIS/AMS permission matrix in spectator-free-teleport-smoke-26.3.ps1.",
                    "The suffix moves only the executor to the chosen dimension and coordinates, preserves the "
                            + "original relative-coordinate origin, and leaves vanilla coordinate commands and "
                            + "the existing /tp in syntax usable."),
            new SmokePlan(
                    "enhancedFakePlayerRejoin",
                    "MC >= 1.21.1 && MC <= 26.3",
                    "26.3",
                    "scripts/powershell/fake-player-rejoin-smoke-26.3.ps1; scripts/powershell/server-startup-smoke-all.ps1",
                    "Build and start an isolated server on every current build node; confirm the generated Mixin config "
                    + "only enables FakePlayerRejoinVehicleMixin on 26.1+. On each node with a matching Carpet TIS release, "
                    + "repeat the integration flow: "
                    + "mount a fake player with a pig in a boat, log out/restart, rejoin at the saved location, then "
                    + "rejoin at explicit coordinates in another dimension twice; disable the rule and check that the "
                    + "TIS no-argument command remains available.",
                    "The vehicle and non-player passenger are absent while the sole player is offline, restored "
                    + "exactly once on repeated rejoin, and remain mounted after requested cross-dimension moves "
                    + "with the same vehicle UUID. Older Carpet APIs complete coordinate rejoin on the first server "
                    + "tick after login; modern Carpet uses its async callback and preserves the vehicle during load. "
                    + "With the rule disabled, TIS's no-argument rejoin still spawns the fake player."),
            new SmokePlan(
                    "fgaUnicodeArgumentsSupportPlayerInventoryScreen",
                    "MC == 26.3",
                    "26.3",
                    "scripts/powershell/unicode-player-inventory-screen-smoke-26.3.ps1",
                    "In an isolated 26.3 server, enable fgaUnicodeArgumentsSupport; execute the test-only "
                            + "fgaUnicodeScreenProbe command, which constructs and encodes an open-screen packet "
                            + "whose player-sprite profile and fallback title contain the Chinese fake-player name "
                            + "睡觉假人. The probe also verifies the transmitted component still displays that name.",
                    "The packet codec encodes successfully, the visible title retains 睡觉假人, the embedded profile "
                            + "has a valid ASCII name with its UUID unchanged, and the isolated server shuts down cleanly. "
                            + "Confirm the real Carpet Org inventory command in a client with a Chinese-named fake player "
                            + "as a separate manual check."),
            new SmokePlan(
                    "fakePlayerLongNamePlayerInfoEncoding",
                    "MC >= 26.3 modern codec/handshake path; MC < 26.3 keeps the legacy buffer/handshake path",
                    "26.3",
                    "scripts/powershell/long-name-player-info-replay-smoke-26.3.ps1; manual FGA/vanilla multiplayer and Flashback recording smoke",
                    "Start an isolated 26.3 server and first join with a client that also has FGA. While that client "
                            + "remains connected, set fakePlayerNameLength to 32 from the server and summon a 17-character "
                            + "fake player; verify the full name appears in TAB without a disconnect. Disconnect and "
                            + "reconnect while the fake player remains online and verify initial player-list synchronization "
                            + "still shows the full name. Then join with a client without FGA and verify it receives the "
                            + "compatible short alias. Check server and client latest.log for player_info_update encode "
                            + "errors, and confirm unrelated scoreboard/team prefixes still update normally. Run the replay "
                            + "codec probe, which decodes incoming names and re-encodes received packets on another thread "
                            + "for 17, 24, 32 Chinese, and 128-character names. Pass -FlashbackJar <installed Flashback jar> "
                            + "to exercise AsyncReplaySaver's real packet queue and save a binary replay chunk. With Flashback recording enabled, also repeat "
                            + "the multiplayer spawn/reconnect scenario and reopen the saved replay.",
                    "The FGA client receives the original long fake-player name on initial sync and updates without "
                            + "disconnecting; a vanilla client receives the <=16-character alias; no unrelated packet "
                            + "gets the widened UTF limit and no player-info encoding error is logged. Replay re-encoding "
                            + "preserves names, UUIDs, skin properties and bytes without a caller-provided scope; 129-character "
                            + "names remain rejected, and encode/decode scopes are restored after success and failure."),
            new SmokePlan(
                    "fakePlayerLongNameMixedFgaVersions",
                    "MC == 26.3; uses published FGA JARs, not current checkout classes",
                    "26.3",
                    "scripts/powershell/mixed-version-long-name-smoke-26.3.ps1",
                    "Pass -ServerFgaJar <1.5.16 jar> and -ClientFgaJar <1.5.15 jar> with "
                            + "-ExpectLongNameFailure to reproduce the legacy-client incompatibility. The opt-in client "
                            + "probe connects only to an isolated loopback server; first summon FGAMixedShort, then "
                            + "FGA_LongFake_0001 with fakePlayerNameLength=32. Repeat with the fixed 1.5.16 client jar "
                            + "without -ExpectLongNameFailure; omit -ClientFgaJar and set -ExpectedProfileName FGA_Lon... "
                            + "for the no-FGA-client alias control. Verify the runtime FGA version in both logs.",
                    "Every client first joins and receives the short name. The legacy 26.3 FGA 1.5.15 client "
                            + "currently disconnects on the complete 17-character profile name with a 16-character "
                            + "decoder limit; this expected-failure control is not a compatibility pass. The fixed "
                            + "1.5.16 client receives the full name, and the no-FGA client receives the short alias. "
                            + "Both test processes stop after each run. This diagnostic does not add server-side "
                            + "version negotiation; the modern full-name path requires the matching fixed client."),
            new SmokePlan(
                    "spectatorFreeTeleport",
                    "MC >= 1.21 && MC <= 26.3; 1.21.1 and 1.21.3+ use distinct ServerPlayer.teleportTo signatures; 1.21.11+ use the updated dimension identifier API",
                    "26.3",
                    "scripts/powershell/spectator-free-teleport-smoke-26.3.ps1 (26.3); manual isolated-server smoke for other nodes",
                    "On each target version, use an isolated server with Carpet TIS, AMS, and Carpet Org Addition installed; "
                            + "enable spectatorFreeTeleport plus opPlayerNoCheat and preventAdministratorCheat. In false, "
                            + "confirm non-OP access stays denied. In true, create non-OP and OP spectators and verify "
                            + "self-only same/cross-dimension teleports while moving other entities remains rejected. In full, "
                            + "run as OP and non-OP through survival, creative, adventure, and spectator; test /tp and "
                            + "/teleport, @s and @a, moving another player, following a player across dimensions, and the "
                            + "explicit dimension-coordinate branch. Repeat with TIS/AMS protections enabled and disabled.",
                    "false keeps vanilla permission gates; true preserves self-only spectator behavior; full grants player "
                            + "sources the complete vanilla teleport target semantics in every game mode, including cross-dimension "
                            + "targets, selectors, and moving other entities; custom dimension coordinates remain world-bound; "
                            + "only /tp and /teleport bypass TIS/AMS permission wrappers and the isolated server stops cleanly."),
            new SmokePlan(
                    "unlimitedMultiplayerPlayers",
                    "MC >= 1.21",
                    "1.21.1",
                    "scripts/powershell/unlimited-multiplayer-players-network-smoke-1.21.1.ps1",
                    "Set max-players below the fake-player count; keep the FGA rule false; enable GCA "
                            + "fakePlayerResident; summon 20 fake players; restart the isolated server; "
                            + "then reconnect all fake players.",
                    "All 20 fake players join before and after restart, while ban, whitelist, and IP-ban checks remain active."),
            new SmokePlan(
                    "lightSourceStonecuttingRecipes",
                    "MC >= 1.21 && MC <= 26.3",
                    "26.2",
                    "scripts/powershell/fake-player-stonecutter-recipe-smoke-all.ps1",
                    "Enable the rule and reload an isolated server; place a stonecutter; summon a fake player; "
                            + "give it a beacon or a high-version copper light source; inspect the generated tag "
                            + "and request light_level_01_15_from_light_source_stonecutter.",
                    "The recipe is registered, the copper tag entries exist only in versions that provide them, and the server stops cleanly."),
            new SmokePlan(
                    "namedEnderPearlTeleport",
                    "MC >= 1.21 && MC <= 26.3",
                    "26.2",
                    "scripts/powershell/named-ender-pearl-teleport-smoke-all.ps1",
                    "Run the script without -VersionList for the complete build matrix, or pass "
                            + "-VersionList <version[,version...]> for an upgrade target; enable the rule; summon "
                            + "fake player 1 as the target and fake player 2 as the thrower; give player 2 an ender "
                            + "pearl named 1 and throw it at a blocking wall; compare player 1's position before and "
                            + "after the hit; take player 1 offline; give player 2 another pearl named 1 and compare "
                            + "player 2's position before and after the hit.",
                    "The first pearl moves fake player 1; after fake player 1 is offline, the second pearl does not "
                            + "teleport fake player 2 or any other fake player; the Mixin loads without an injection error."),
            new SmokePlan(
                    "soulSpeedNoDurability",
                    "MC >= 1.21 && MC <= 26.3",
                    "1.21.1",
                    "scripts/powershell/fake-player-stonecutter-soul-speed-smoke-1.21.1.ps1",
                    "Enable the rule; equip a fake player with Soul Speed III boots at a known durability; walk it "
                            + "at least 50 metres on soul sand; read the position and item durability afterward.",
                    "The player travels at least 50 metres and the boots' durability is unchanged."),
            new SmokePlan(
                    "thornsNoDurability",
                    "MC >= 1.21 && MC <= 26.3",
                    "1.21.1",
                    "MANUAL: server startup plus a controlled in-game hit",
                    "Enable the rule; equip Thorns armor with recorded durability; trigger a reflected hit; "
                            + "repeat with the rule disabled as the vanilla control.",
                    "With the rule enabled, reflected damage still occurs but the Thorns armor does not lose durability; record client and server observations."),
            new SmokePlan(
                    "playerPossessionCommandTreeRefresh",
                    "MC >= 1.21 && MC <= 26.3",
                    "26.2",
                    "scripts/powershell/player-possession-command-refresh-smoke-26.2.ps1",
                    "Use an isolated integrated-server world with temporary commandPlayer true and playerPossession true; "
                            + "join once, without reconnecting, summon FGARefreshTarget, run /player FGARefreshTarget possess, "
                            + "then run /controlPlayer; restore the original Carpet config after the test.",
                    "The client accepts /player ... possess and /controlPlayer after the first join, logs the swapped state "
                            + "and the active controller, and does not report an unknown command."),
            new SmokePlan(
                    "playerPossessionBounds",
                    "MC >= 1.21 && MC <= 26.3",
                    "26.3",
                    "MANUAL: isolated multiplayer possession test",
                    "Enable playerPossession; set playerPossessionDistance to a small value and verify a same-dimension "
                            + "target outside that range is rejected while a target inside it is accepted; set the rule to -1; "
                            + "then set playerPossessionCrossDimension false and verify a cross-dimension target is rejected, "
                            + "before restoring it to true and verifying the existing cross-dimension session path.",
                    "Distance and cross-dimension rules gate new sessions and active sessions are released when a rule change makes "
                            + "the current session invalid; the default -1/true behavior remains backward compatible."),
            new SmokePlan(
                    "playerPossessionSignedChatSession",
                    "MC >= 1.21 && MC <= 26.3",
                    "26.3",
                    "MANUAL: secure-chat multiplayer possession smoke test",
                    "On an isolated online-mode 26.3 server with two authenticated real clients, enable playerPossession; "
                            + "start possession from player A into player B, send distinct chat messages from both clients, "
                            + "stop possession, and send one more message from each. Inspect client and server latest.log "
                            + "throughout; do not disable secure-profile enforcement for the test.",
                    "All messages are delivered under the sending connection's identity without invalid-signature or "
                            + "profile-key validation errors during the swap or after restoration; possession still swaps "
                            + "the intended body state."),
            new SmokePlan(
                    "fireAspectOnTools",
                    "MC >= 1.21 && MC <= 26.3",
                    "26.2",
                    "scripts/powershell/fire-aspect-tool-smoke-all.ps1",
                    "Enable fireAspectOnTools; summon fake players FGAFireOne, FGAFireTwo, and FGAFireFortune; "
                            + "give them a pickaxe with Fire Aspect I, a pickaxe with Fire Aspect II, and a pickaxe "
                            + "with Fire Aspect plus Fortune; have each fake player mine the prepared test block; "
                            + "record the resulting item stacks and the enchantment component before mining.",
                    "Fire Aspect I applies one smelting pass, Fire Aspect II applies two chained passes, and the "
                            + "Fire Aspect plus Fortune pickaxe produces smelted ore with the Fortune-enlarged count; "
                            + "the server stops cleanly without a Mixin injection failure."),
            new SmokePlan(
                    "entityDropRemovalClickableRemoval",
                    "MC >= 1.21 && MC <= 26.3",
                    "26.3",
                    "MANUAL: server startup plus clickable command output",
                    "Enable entityDropRemoval; configure a namespaced entity with an item and allEquipment; run "
                            + "/entityDropRemoval list and /entityDropRemoval list <entity>; click every red [-] "
                            + "and green [+] button, click both all-drop action buttons, then repeat the same set/remove "
                            + "and enableAllDrops/disableAllDrops operations by typing the commands.",
                    "Namespaced entity and item IDs execute without a command parse error, allEquipment is removed "
                            + "by its button, the all-drop buttons toggle the persistent allDrops state, and the configuration "
                            + "disappears from both list views after enableAllDrops."),
            new SmokePlan(
                    "fullShulkerBoxCraftingRuleAndQuantity",
                    "ported fix: MC >= 26.2 && MC <= 26.3; existing feature gate: MC >= 1.16.5 && MC <= 26.3",
                    "26.3",
                    "MANUAL: isolated integrated-server crafting-table smoke test",
                    "Start an isolated singleplayer or integrated-server world with FGA on the client. Keep "
                            + "fullShulkerBoxCrafting false; fill the piston recipe grid with nine shulker boxes "
                            + "whose contents match the three planks, four cobblestone, iron ingot, and redstone slots; "
                            + "verify no custom result can be taken. Set the rule to any without reconnecting and repeat "
                            + "with 1728 matching items in every input box plus enough empty boxes; take the result once "
                            + "and count every output, returned, and consumed box.",
                    "The false rule produces no full-box result even in an integrated server; any produces exactly one "
                            + "full piston box rather than three, consumes each recipe input box once, and conserves all boxes."),
            new SmokePlan(
                    "foodCommandPermission",
                    "MC >= 1.21 && MC <= 26.3",
                    "26.3",
                    "MANUAL: isolated server command-permission smoke test",
                    "Set /carpet foodCommandPermission to false, true, onlyself, ops, and each value from 0 through 4; "
                            + "with a non-OP player run /food clear and /food clear <other>; with an OP repeat both commands; "
                            + "also verify /fga food clear uses the same rule and reconnect after changing the rule if the client "
                            + "does not refresh its command tree.",
                    "false hides and rejects the commands; true allows self and target clearing; onlyself allows non-OP self "
                            + "clearing but rejects non-OP targets while OPs can target; ops requires permission level 2; 0-4 "
                            + "requires the configured minimum level; food and saturation are set to zero only for authorized targets."),
            new SmokePlan(
                    "playerInfoTeamPrefixCompatibility",
                    "MC >= 1.21 && MC <= 26.3",
                    "26.3",
                    "MANUAL: client/server TAB and Team packet smoke test",
                    "With FGA installed on the server, keep playerHealthDisplay false and showControllerPrefix false; "
                            + "join a real client, run !!zgm set 测试, confirm the prefix appears immediately without reconnecting, "
                            + "then summon a bot_ fake player and confirm its 假人 label appears immediately. Repeat after "
                            + "clearing the status and after changing it again; inspect the server log for packet or Mixin errors.",
                    "ZaiGanMa Team prefixes and bot labels update immediately for the existing client, FGA long-name support "
                            + "does not alter unrelated Team packets, and reconnecting is not required."),
            new SmokePlan(
                    "sulfurCubeGrowthTime",
                    "MC >= 26.2 && MC <= 26.3",
                    "26.3",
                    "MANUAL: isolated 26.2 and 26.3 servers, each with a disposable test world",
                    "Repeat independently on 26.2 and 26.3. Set /carpet sulfurCubeGrowthTime to 5 and spawn a small "
                            + "Sulfur Cube; confirm it remains small "
                            + "before five game seconds and grows to medium after about five game seconds at 20 TPS. "
                            + "Spawn a second small cube, change the rule to 1, and confirm the already-growing cube "
                            + "keeps its existing age countdown while a newly spawned or split small cube uses the new "
                            + "duration. Feed a small cube and confirm vanilla feeding acceleration still works. "
                            + "Set the rule to -1 and confirm a newly spawned small cube uses the vanilla 20-minute "
                            + "duration; save and reload a world with a small cube to confirm its age remains persisted.",
                    "Only newly initialized small Sulfur Cubes use the configured positive duration; existing age data "
                            + "is not rewritten when the rule changes, -1 preserves vanilla timing, and feeding retains "
                            + "vanilla acceleration."));

    @Test
    void everyPlanContainsAnExecutableUpgradeProcedure() {
        assertFalse(PLANS.isEmpty(), "the source test catalog must not be empty");
        for (SmokePlan plan : PLANS) {
            assertTrue(notBlank(plan.feature()), "missing feature name");
            assertTrue(notBlank(plan.versionGate()), plan.feature() + " is missing its version gate");
            assertTrue(notBlank(plan.baseline()), plan.feature() + " is missing its baseline version");
            assertTrue(notBlank(plan.script()), plan.feature() + " is missing its script or manual marker");
            assertTrue(notBlank(plan.procedure()), plan.feature() + " is missing its procedure");
            assertTrue(notBlank(plan.expected()), plan.feature() + " is missing its expected result");
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
