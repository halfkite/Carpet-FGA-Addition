# Inventory inherited-capacity regression

This suite requires the independent scoped configuration gate. Build both targets,
then run:

```powershell
python scripts/tests/run-rule-compat.py --version 1.21.1 --suite inventory-compat
python scripts/tests/run-rule-compat.py --version 26.2 --suite inventory-compat
```

The dedicated-server test constructs the real transformed Inventory and checks it
inherits Container's stack-aware capacity method. In-memory synthetic item components
provide maxima 1, 16, 64, 128 and 1000. A synthetic Container mixin changes the no-arg
capacity to 37. Tests cover disabled/default and retained-scope states, normal Slot
capacity, enabled inventory expansion and a one-item special Slot.

Synthetic >99 component values are never put into a saved player or world entity.
The test is opt-in and does not change the distributable artifact. A mod that provides
its own Inventory override requires an additional integration check; this fix does
not replace such overrides. The runner comes from the hopper compatibility change.
