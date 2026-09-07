# ItemEntity rule-off compatibility regression

Build 1.21.1 with Java 21 and 26.2 with Java 25. Run the opt-in dedicated-server test
mod for each version and both synthetic mixin priorities:

```powershell
python scripts/tests/run-rule-compat.py --version 1.21.1 --suite item-compat
python scripts/tests/run-rule-compat.py --version 1.21.1 --suite item-compat --priority 900
python scripts/tests/run-rule-compat.py --version 26.2 --suite item-compat
python scripts/tests/run-rule-compat.py --version 26.2 --suite item-compat --priority 900
```

The default priority is 1100. Synthetic mixins veto eligibility, change expression
capacities, replace the caller's merge-count argument, and supply a distinctive AABB.
Tests execute the transformed Minecraft methods, asserting that disabled FGA retains
those values, including the exact supplied AABB object. They also check active large
stack merging, whitelist exclusion and the independent merge-distance rule.

The query observer returns no neighbours so its inspection cannot modify a world.
The runner creates disposable worlds and requires an explicit PASS marker. Existing
Carpet mixins are loaded during these tests. No synthetic classes enter the main jar.

The persistence hooks are intentionally unchanged. Oversized saved-item lifecycle
and arbitrary third-party combinations still need separate gameplay tests. Only
1.21.1 and 26.2 receive the new behavior; other preprocessor nodes retain old code.
