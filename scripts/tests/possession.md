# Player possession — Minecraft 1.21.1

Run the opt-in test mod from the repository root with a Java 21 environment:

```powershell
.\gradlew.bat :1.21.1:runServer -I scripts/tests/possession.gradle --configure-on-demand --max-workers=2
```

The init script creates a unique directory under `build/possession-tests/`, uses a disposable flat world and an automatically assigned server port, and does not reuse `versions/1.21.1/run` or an existing world. The test stops the server itself. Check for `FGA_POSSESSION_PASS` and absence of `FGA_POSSESSION_FAIL`; a successful Gradle exit alone is insufficient.

Tests run actual transformed Minecraft/Carpet server classes with fake connections. They cover the five-value permission matrix, command registration, fake and real targets, exclusive sessions, state/profile/inventory swaps, mounts, dimension changes, rule/OP changes, death and disconnect cleanup, and the no-persistent-data path. This is **not** a graphical vanilla-client or signed-chat integration test.

## Manual acceptance / 人工验收

Use a new test world and two vanilla 1.21.1 clients, A and B; keep a third observer available for body/skin checks. Do not use a production save.

1. Give A and the target different inventories, health, XP and game modes. Enable `playerPossession onlyfake`, spawn a Carpet fake player, and run `/player <fake> possess` as A.
2. Verify that A's live entity has the fake's position, game mode, inventory, effects, XP, ender chest and riding state, while the fake entity has A's state. Check that both entity UUIDs and connections stay unchanged and that the fake does not disconnect.
3. While the session is active, move or damage either entity from the server or an observer. Exit and verify that the current state on each entity is exchanged back, including damage, movement and item changes made during the session.
4. Open inventories and external containers before starting; confirm both are closed. During the session test inventory, crafting, chests, trading, hotbar/offhand and riding, then verify no item duplication or loss on exit.
5. Enable `true` and possess B. B must stay connected, see its body at the swapped position, receive an explanatory message, and have movement and interaction packets ignored. Verify B can chat and use `/player B possess stop`.
6. Test `false`, `true`, `onlyfake`, `opreal` and `ops` with both OP and non-OP A against fake and real targets. Test Carpet `commandPlayer`, completion, self/overlapping/nested sessions, de-op during possession and rule disable.
7. Possess an OP target as a non-OP. Verify commands still execute as A and retain A's permission level and position; target operator status must not authorize command-block or other privileged edits.
8. Start across dimensions, travel through Nether/End portals while possessed, and exit. Check chunk loading, weather, effects, containers and teleport confirmations on both clients. Verify normal signed chat before, during and after possession on an online-mode test server.
9. Test both participants dying, disconnecting, being kicked, fake-player removal, reconnects and a server restart. Verify no stuck input, duplicate items or persistent possession state. Automatic actions must not resume after exit.

中文要点：实现采用参考模组的在线实体状态交换，保留双方实体、UUID 和连接，不复制背包或写入离线数据；状态交换期间的伤害、移动和物品变化随实体保留，真人目标输入会被屏蔽但仍可聊天和退出；OP 权限不借给操控者；跨维度、容器、断线和死亡必须实测。服务端测试通过不能替代以上客户端验收。
