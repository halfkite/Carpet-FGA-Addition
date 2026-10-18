# Player possession — Minecraft 1.21.1

## Recent fixes cross-version regression — 2026-10-18

`scripts/powershell/recent-fixes-port-smoke.ps1` accepts `-MinecraftVersion`, `-Mode server|client`, and `-Java21Home` / `-Java25Home` toolchain paths (or `JAVA21_HOME` / `JAVA25_HOME`). It supports the ten explicit build nodes listed in `docs/version_compatibility.md`. Build the selected version first so its test classes exist. Each invocation creates an isolated timestamped directory under `build/`; never supply a production world.

Run server mode first, optionally supplying a matching official VMP JAR with `-VmpJar`. The runner uses `scripts/gradle/recent-fixes-port-smoke.init.gradle` to add the opt-in `RecentFixesProbe`, and checks that VMP actually loaded when requested. It tests independent/reusable statistic snapshots, missing-key zeroing, possession progress, pending-spawn lifecycle, and simultaneous same-name/case-variant requests across all three dimensions in both preload modes. After shutdown it independently checks the saved vanilla statistic JSON files. Both the probe success marker and saved-stat checks are required; a Gradle success exit alone is insufficient.

Then run client mode with `-ServerRunDirectory` pointing to the successful same-version server run. The runner copies only that disposable world, launches an actual Minecraft client/integrated server and loads `RecentPossessionClientProbe`. Four cases cover cheats on/off and both `permissionSwapsToo` policies without opening to LAN. The probe sends client command packets, verifies original host identity and expected permission levels, tests `/player` exit when permitted, and always requires `/fga possess stop` to work, including after `commandPlayer` is disabled. Expected permission denial under the configured policy is not treated as a defect. Success requires `SOLO_PERMISSION_RESULT checks=58 failures=0`.

These checks use Loom development runtimes built from the same source and dependencies as the distributable JAR. They do not establish that every third-party modpack, online authentication, LAN guest or graphical command-completion path works. They do not cover or fix RankBoard's known disconnect-event offline-cache ordering issue. Per-version results, VMP versions and archived JAR paths are recorded in `docs/version_compatibility.md` and the local `build/recent-fixes-port/report.md`.

If a dependency resolves a newer Fabric Loader than the project's declared minimum, use `-LoaderVersion <resolved version>` for both modes. This passes `-Ploader_version` only to the test invocation so Loom selects that Loader's installer libraries as well. On 1.21.10 the initial development client used Loader 0.17.2 with the old 0.15.11 installer's Mixin 0.8.5 and failed in Fabric API's static constructor injection before the probe ran. Do not suppress that Mixin or count that run as passed; preserve its log and repeat with a matched Loader/library set. This test override does not change the published mod's dependencies.

For the 26.3 real singleplayer permission matrix, use `scripts/powershell/possession-permission-smoke-26.3.ps1`; inputs, isolation and expected command gating are documented in `scripts/tests/possession-permission/README.md`. This launches an actual local client/integrated server and covers host ownership after display-name swaps, both permission policies, cheats on/off and client command packets for exit.

## Statistic ownership regression — Minecraft 26.3

Run `scripts/powershell/possession-stats-smoke-26.3.ps1` with explicit `-ServerFixture`, `-FgaJar`, `-CarpetJar`, `-RankBoardJar` and `-JavaHome` paths. The server fixture must contain a Fabric 26.3 dedicated server, its libraries and `mods/fabric-api.jar`; the supplied mods must match 26.3 and Java must be version 25. The runner reads those files and creates a new disposable world under `build/possession-stats-26.3-<timestamp>`.

The packaged-mod test checks snapshot independence and reuse, asymmetric missing statistics, both participants ending possession, progress earned during possession, repeated sessions, rule disable, removal before vanilla save, online RankBoard values and names by UUID, and outgoing vanilla statistic packet values including explicit zeros. After clean shutdown the runner independently checks both vanilla statistic JSON files. Require both `POSSESSION_STATS_PASS` and `POSSESSION_STATS_SAVED_PASS`; the final summary is saved in the run directory. Never use an existing player world or overwrite historical test results.

Add `-VerifyOfflineRanking` to explicitly dispatch the Fabric disconnect event before vanilla player removal and check the resulting offline RankBoard cache. This stronger integration test currently fails with RankBoard 1.10.11: the disconnect callback captures the swapped identity and values before FGA restores the body. Passing the default removal-only checks does not establish that the offline leaderboard is correct.

Manual 26.3 acceptance on a disposable server with two graphical clients:

1. Install matching Carpet, Fabric API, the fixed FGA JAR and RankBoard 1.10.11 on the test server. Vanilla clients are sufficient. Record each player's jump and fishing values in the vanilla statistics screen and RankBoard; use different nonzero jump counts and let only one player catch fish.
2. Enable `/carpet commandPlayer true` and `/carpet playerPossession true`. Let A start `/fga possess <B>`, then end with `/fga possess stop`. Without earning more statistics, each original name and UUID must recover exactly its original values; the player with no fishing history must show zero.
3. Repeat, earning different jump counts on both bodies during possession. After stopping, progress must follow the respective possessed state without loss or duplication. Check both the vanilla statistics screen and RankBoard.
4. Repeat with B stopping, rule disable and either participant disconnecting. Reconnect and restart the server; statistics and displayed names must stay correct, with no duplicate or residual sidebar rows.

2026-10-17 baseline result: 26.3 build and 81 JUnit tests passed; the default packaged-mod regression passed 174 assertions plus both persisted vanilla statistic files. The unpatched 1.7.0 JAR failed snapshot independence (expected 1000, actual 17). Two real vanilla 26.3 clients passed normal stop, actual jump progress, vanilla statistics-screen checks and reconnect; disconnecting during possession exposed the remaining offline-cache failure. The strict `-VerifyOfflineRanking` case reproduced that failure. See `docs/version_compatibility.md` for exact report and archive paths. Full joint acceptance has not passed.

## Existing 1.21.1 lifecycle regression

Run the opt-in test mod from the repository root with a Java 21 environment:

```powershell
.\gradlew.bat :1.21.1:runServer -I scripts/tests/possession.gradle --configure-on-demand --max-workers=2
```

The init script creates a unique directory under `build/possession-tests/`, uses a disposable flat world and an automatically assigned server port, and does not reuse `versions/1.21.1/run` or an existing world. The test stops the server itself. Check for `FGA_POSSESSION_PASS` and absence of `FGA_POSSESSION_FAIL`; a successful Gradle exit alone is insufficient.

Tests run actual transformed Minecraft/Carpet server classes with fake connections. They cover the five-value permission matrix, command registration, fake and real targets, exclusive sessions, state/profile/inventory swaps, mounts, dimension changes, rule/OP changes, death and disconnect cleanup, and the no-persistent-data path. This is **not** a graphical vanilla-client or signed-chat integration test.

## Manual acceptance / 人工验收

Use a new test world and two vanilla 1.21.1 clients, A and B; keep a third observer available for body/skin checks. Do not use a production save.

1. Give A and the target different inventories, health, XP and game modes. Enable `playerPossession onlyfake`, spawn a Carpet fake player, and run `/player <fake> possess` as A.
2. Verify that A's live entity has the fake's position, game mode, inventory, effects, XP, ender chest and riding state, while the fake entity has A's state. Check that both entity UUIDs and connections stay unchanged and that the fake does not disconnect.
3. While the session is active, move or damage either entity from the server or an observer. Exit and verify that the current state on each entity is exchanged back, including damage, movement and item changes made during the session.
4. Open inventories and external containers before starting; confirm both are closed. During the session test inventory, crafting, chests, trading, hotbar/offhand and riding, then verify no item duplication or loss on exit.
5. Enable `true` and possess B. B must stay connected, see its swapped body, and receive an explanatory message. Verify B can chat and use `/fga possess stop` to end the session.
6. Test `false`, `true`, `onlyfake`, `opreal` and `ops` with both OP and non-OP A against fake and real targets. With `commandPlayer=ops` and `playerPossession=onlyfake`, confirm OP A can start possession of a Carpet fake player and non-OP A cannot; with `commandPlayer=true`, confirm non-OP A can start it. Set `permissionSwapsToo=false`, start possession of a non-OP fake, confirm the controller's command permissions follow the fake, `/player` remains gated by `commandPlayer`, and `/fga possess stop` still works and restores the original command tree. Test completion, self/overlapping/nested sessions, de-op during possession and rule disable.
7. With `permissionSwapsToo=true`, possess an OP target as a non-OP. Verify commands still execute as A and retain A's permission level and position; target operator status must not authorize command-block or other privileged edits.
8. Start across dimensions, travel through Nether/End portals while possessed, and exit. Check chunk loading, weather, effects, containers and teleport confirmations on both clients. Verify normal signed chat before, during and after possession on an online-mode test server.
9. Test both participants dying, disconnecting, being kicked, fake-player `kill()` and a server restart. Verify the reference PlayerControl lifecycle releases the session when the fake kill path runs, with no duplicate items or persistent possession state. Automatic actions must not resume after exit.
10. For each supported version, create a singleplayer world with cheats disabled and `playerPossession=onlyfake` preconfigured as the rule default. Without opening the world to LAN, verify `/fga possess <fake>` starts control and `/fga possess stop` ends it. Verify other Carpet commands remain unavailable, then repeat on a dedicated server and confirm `commandPlayer` still gates possession.

中文要点：实现采用参考模组的在线实体状态交换，保留双方实体、UUID 和连接，不复制背包或写入离线数据；状态交换期间的伤害、移动和物品变化随实体保留；OP 权限策略由 `permissionSwapsToo` 控制；跨维度、容器、断线和死亡必须实测。服务端测试通过不能替代以上客户端验收。

The logout regression calls vanilla PlayerList.remove with separated, moved bodies and asserts that both real-target and fake-target sessions end without co-locating the bodies. Graphical disconnect/reconnect remains a manual check.

## 1.6.2 verification — 2026-10-09

All ten build nodes passed their builds and 529 JUnit checks; see `build/verification-1.6.2-20261009-115004/summary.json`
The isolated 1.21.1 server fixture passed 82 checks, including `/fga possess stop` after losing OP and restoration of the original permission level; see `build/verification-1.6.2-20261009-115004/possession-1.21.1.log`
Singleplayer-owner handling uses GameProfile on 1.21.1, 1.21.3, 1.21.4, 1.21.5 and 1.21.8, and NameAndId on 1.21.10, 1.21.11, 26.1.2, 26.2 and 26.3
Graphical no-LAN singleplayer tests and multiplayer command-tree display remain manual acceptance items
