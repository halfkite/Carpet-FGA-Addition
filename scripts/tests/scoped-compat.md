# Scoped limit activation regression

After building each target, run these commands twice with the same fresh test-world
name (use a new name for a new run):

```powershell
python scripts/tests/run-rule-compat.py --version 1.21.1 --suite scoped-compat --world rule-compat-scoped-example
python scripts/tests/run-rule-compat.py --version 26.2 --suite scoped-compat --world rule-compat-scoped-example
```

World directories are separate per version. The first run verifies a fresh default,
enable/set/disable, effective inventory limits, actual Container and Slot consumers,
client-requirement predicates, in-process reload, unchanged saved bytes, re-enable,
and unstackable items. It also damages only the disposable configuration, verifies
safe loading and refusal to overwrite that damaged file, and restores the test file.
The second server process must report `restarted=true` and retained nonzero scopes
that are ineffective while the rule is disabled.

This suite requires the independent stack-client requirement fix (#15). Its client
checks exercise the actual predicate, not network login or reconnect. The Inventory
method fallback for synthetic item maxima above 99 is a separate regression suite.

The source-set test mod is opt-in and is never included in the normal FGA jar.
The two-version runner is provided by the independent hopper compatibility change.
