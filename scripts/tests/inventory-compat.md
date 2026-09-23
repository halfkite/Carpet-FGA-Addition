# Inventory inherited-capacity regression

This suite requires the independent scoped configuration gate. Build both targets,
then run:

```powershell
python scripts/tests/run-rule-compat.py --version 1.21.1 --suite inventory-compat
python scripts/tests/run-rule-compat.py --version 1.21.11 --suite inventory-compat
python scripts/tests/run-rule-compat.py --version 26.2 --suite inventory-compat
```

The dedicated-server test constructs the real transformed Inventory and checks that
it preserves Container's stack-aware capacity. In-memory synthetic item components
provide maxima 1, 16, 64, 128 and 1000. A synthetic Container mixin changes the no-arg
capacity to 37. Tests cover disabled/default and retained-scope states, normal Slot
capacity, enabled inventory expansion and a one-item special Slot.

It also covers the returned-stack regression: with the inventory scope at 1000 it seeds
slot 0 with a full vanilla stack of 64, calls `Inventory.placeItemBackInInventory` with
another 100 and requires the call to terminate, to consume the whole stack and to fill
the slot to 164. Before the capacity fix the vanilla batch was `64 - 64`, so `split(0)`
returned an empty stack without shrinking the source and the vanilla loop never ended;
the probe therefore runs on a bounded thread instead of hanging the whole suite.

Synthetic >99 component values are never put into a saved player or world entity.
The test is opt-in and does not change the distributable artifact. A mod that provides
its own Inventory override requires an additional integration check; this fix does
not replace such overrides. The runner comes from the hopper compatibility change.
