# FGA handshake registration check (issue #24)

```powershell
python scripts/tests/run-rule-compat.py --version 1.21.1 --suite handshake-compat
```

Targets `1.21.1`, the version from the reported disconnect. Two checks run on a real dedicated server:

1. `carpet-fga-addition:handshake` is present in Fabric's play C2S payload registry
   (`PayloadTypeRegistryImpl.PLAY_C2S.get(...)`, queried through the implementation because the public API
   only writes). This is what keeps the channel resolvable when another mod rebuilds
   `CustomPacketPayload.codec`'s payload list from a copy, which previously sent vanilla to its
   `DiscardedPayload` fallback and produced
   `Failed to encode packet 'serverbound/minecraft:custom_payload' (carpet-fga-addition:handshake)`.
2. A real `ServerboundCustomPayloadPacket` carrying the handshake encodes and decodes back to
   `FGAPayloads.HandshakePayload` through `ServerboundCustomPayloadPacket.STREAM_CODEC`.

Limits of this suite, stated for the record: it does **not** reproduce the reporter's failure. The failing
client ran ~200 mods and failed over the network; here the codec is exercised directly on a dedicated
server, and a locally built pre-fix control was not achieved (disabling the registration by editing a
`//#if` condition has no effect on 1.21.1, because 1.21.1 is the preprocessor root node whose sources are
compiled raw). Use it as a regression guard for the registration, not as a reproduction of issue #24.

The test mod is opt-in and is never included in the distributable jar.
