# Stack client requirement regression

Run the predicate test against the actual compiled classes for each build node:

```powershell
.\gradlew.bat :1.21.1:stackClientRequirementTest -I scripts/tests/stack-client-requirement.gradle
```

Replace `1.21.1` with each supported build node (or pass all nine task paths in
one Gradle invocation after checking them individually in CI).

Each run performs 80 checks covering all accepted rule values, zero/inventory/container/both scoped
limits, failed configuration loading, disabling and re-enabling, and preservation
of the configured state. This is not a network or world persistence test.

Use only disposable test worlds for the following integration matrix. Keep each
accepted connection online for at least 120 server ticks. The existing check uses
a global 40-tick counter, not a per-player handshake grace period.

| Client and configuration | Expected result |
| --- | --- |
| Vanilla, rule false, both scoped limits zero | No stack-requirement disconnect |
| Vanilla, rule false, inventory 1000, container 1000, or each separately | No stack-requirement disconnect |
| Enable rule, set scoped limits, disable before vanilla joins | Join and reconnect without stack-requirement disconnect |
| Persist rule false in Carpet configuration, restart with nonzero scoped JSON | Same result; retain scoped values |
| Vanilla, rule true, ground-only configuration | No stack-requirement disconnect |
| Vanilla, rule true/ops/0-4, nonzero scoped limit | Existing rejection remains |
| FGA client, successful handshake, active scoped limits | No stack-requirement disconnect |
| Same UUID logs out with FGA and reconnects with vanilla, active scoped limits | Reject; do not reuse the previous handshake |
| Vanilla already online while rule is false; enable retained scoped limits | Existing rejection resumes |
| Carpet fake player, active scoped limits | Existing exemption remains |

Repeat connection cases near the global check boundary and with delayed FGA
handshakes. Test existing oversized stacks, pickup, and container clicks separately:
scoped effective-limit computation currently lacks the main-rule gate, which this
client-requirement fix intentionally does not refactor. Do not infer vanilla item
interaction compatibility merely from the absence of this disconnect.

Build nodes to validate: 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10,
1.21.11, 26.1.2, and 26.2. Also validate runtime compatibility for the additional
Minecraft versions declared by each artifact's version properties. The test task
above is currently registered only for the 1.21.1 baseline.
