# Singleplayer possession permissions (26.3)

Run `scripts/powershell/possession-permission-smoke-26.3.ps1` with explicit `-ServerFixture`, `-ClientGameDirectory`, `-TestWorldFixture`, `-FgaJar`, `-CarpetJar` and `-JavaHome` paths.

- ServerFixture: Fabric 26.3 binaries and libraries, including mods/fabric-api.jar
- ClientGameDirectory: existing Minecraft installation containing libraries/, assets/, and versions/26.3/26.3.jar plus its version JSON
- TestWorldFixture: an already-generated disposable 26.3 world under this repository's build/ directory; the runner rejects paths outside build/
- JavaHome: JDK 25

The runner reads the existing game binaries and assets, creates a new client directory under build/, copies only the disposable world, installs FGA/Carpet/Fabric API and a test-only addon, and starts an actual integrated server with quick-play. It uses a fixed offline test identity, no account token, no launcher profile changes, no LAN publishing, and no real player world. The client closes and saves normally when the addon finishes. Timeout fallback terminates only the process launched for this test.

The addon sends commands through the real client's ClientPacketListener rather than calling the manager directly. Four scenarios cover cheats enabled/disabled and permissionSwapsToo true/false. It checks host identity, host permission, fake-player privilege isolation, restoration, and both exit command paths. `/player` is expected to fail when commandPlayer denies the current permission; `/fga possess stop` must still end the active session. The last scenario disables commandPlayer completely during possession and still requires the FGA stop packet to succeed. No scenario publishes LAN.

Inspect stdout.log and summary.json in the new directory. Require SOLO_PERMISSION_RESULT with failures=0 and a successful process exit. Nonzero checks and failures are logged independently, so the test reports the FGA escape path even if the ordinary player exit fails. The original reproducer confirmed the host/target identity bug, while the FGA stop packet succeeded in the minimal setup; preserve that distinction when reviewing the user's report.
