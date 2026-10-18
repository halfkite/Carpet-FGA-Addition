# Fake-player preload regression (26.3)

Run `scripts/powershell/fake-player-preload-smoke-26.3.ps1` with `-ServerFixture`, `-FgaJar`, `-CarpetJar`, and `-JavaHome`. The server fixture must contain `server.jar`, `fabric-server-launch.jar`, `libraries/`, `versions/26.3/server-26.3.jar`, and `mods/fabric-api.jar`. Add `-VmpJar` to test with VMP; omit it for the control run. Use a JDK 25 installation.

The launcher compiles this test addon, creates a new disposable world under `build/`, starts a server bound to loopback with an automatically assigned port, and waits for its automatic shutdown. No existing world or player data is copied. Results and exact input JAR paths are written to `summary.json`, with detailed evidence in `stdout.log`. A failed assertion or timeout makes the launcher fail.

Coverage:

- Three dimensions receive sixty spawn commands for the same cached identity
- Rule modes `false`, `always`, and a fresh `adaptive` window
- Case variants with `always` and `adaptive`, with exactly one player-list entry and one world entity per UUID
- Queued preload cancellation and successful retry, using a deterministic test-only executor barrier
- Canonical Carpet pending-login marker rejects case variants, and retry succeeds after its release
- A different cached name resolving to an already-online UUID cannot create another player

The test-only reflection accesses the actual Carpet pending set and FGA resolver executor. It does not replace production code or fabricate a successful login. The server tests count actual entities across all dimensions and inspect the global player list. The original unfixed JAR must fail the mixed-case assertion; do not weaken that assertion.

Not covered: authenticated online profiles, third-party direct `createFake` callers, real-client presentation, or recovery of duplicates already saved in a world. Rule-disabled mixed-case behavior belongs to Carpet and is not changed by the preload fix.
