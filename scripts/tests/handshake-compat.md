# FGA handshake codec regression (issue #24)

```powershell
python scripts/tests/run-rule-compat.py --version 1.21.1 --suite handshake-compat
```

The suite targets `1.21.1`, the version from the reported disconnect. Its test mod bundles an interference
mixin that removes `carpet-fga-addition:handshake` from the payload list after FGA appended it and records the
removal, which is the condition the reporting client hit: vanilla's payload codec resolves an id from that
list and otherwise answers with `DiscardedPayload`, whose writer casts the handshake to `DiscardedPayload`
and disconnects the client with
`Failed to encode packet 'serverbound/minecraft:custom_payload' (carpet-fga-addition:handshake)`.

The test first asserts the removal really happened, then encodes and decodes a real
`ServerboundCustomPayloadPacket` carrying the handshake and requires the payload to survive. It passes only
because the handshake is also registered in Fabric's `PayloadTypeRegistry`, which Fabric's payload codec asks
before vanilla's fallback provider.

The test mod is opt-in and is never included in the distributable jar.
