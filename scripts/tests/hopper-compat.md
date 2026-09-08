# Hopper rule isolation regression

Build `:1.21.1:build` with Java 21 and `:26.2:build` with Java 25, then run:

```powershell
python scripts/tests/run-rule-compat.py --version 1.21.1 --suite hopper-compat
python scripts/tests/run-rule-compat.py --version 26.2 --suite hopper-compat
python scripts/tests/run-rule-compat.py --version 1.21.1 --suite hopper-compat --priority 900
python scripts/tests/run-rule-compat.py --version 26.2 --suite hopper-compat --priority 900
```

The opt-in test mod wraps the actual ItemEntity transfer call, imposing a one-item
allowance like a compatibility mod. It verifies block-container and hopper-minecart
targets, enabled/disabled rules, original input count when disabled, one inner call,
partial-transfer return values, one bounded stone batch and a full destination.
The default synthetic mixin priority is 1100; 900 covers the other side of FGA's
default priority. The test mod is a separate source set and is not packaged in FGA.

The runner requires a PASS marker as well as a successful process exit. It creates
disposable worlds under the version run directory, backs up/restores launch settings,
and retains logs and worlds for diagnosis. It never opens a real saved world.

Only 1.21.1 and 26.2 receive the new implementation. The other seven build nodes
retain their prior code for a later port. Preprocessor source-set dependencies may
compile intermediate nodes even when only the 26.2 task is requested.

This synthetic test does not certify a particular Org Addition release. Test Org
alone versus Org + FGA disabled in a disposable world before claiming that pairing.
Chest-to-minecart extraction is a separate path and is not covered by this suite.
