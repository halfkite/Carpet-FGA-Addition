package carpet.fga.probe;

import carpet.CarpetSettings;
import carpet.fga.FGASettings;
import carpet.fga.PlayerPossessionManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.GameType;

/** Runs in an actual integrated server and uses the local client's command packets. */
public final class IntegratedPermissionProbe implements ClientModInitializer {
    private static volatile String command;
    private static volatile boolean finished;
    private int tick;
    private int phase;
    private int deadline;
    private int failures;
    private int checks;
    private int scenario;
    private int originalOwnerLevel;
    private int originalFakeLevel;
    private ServerPlayer owner;
    private ServerPlayer fake;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) {client.stop(); return;}
            String pending = command;
            if (pending != null && client.getConnection() != null && client.player != null) {
                command = null;
                System.out.println("SOLO_PERMISSION_SEND " + pending);
                client.getConnection().sendCommand(pending);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished || !(server instanceof IntegratedServer solo)) return;
            try {
                tick++;
                if (owner == null) {
                    owner = server.getPlayerList().getPlayer(solo.getSingleplayerProfile().id());
                    if (owner == null) return;
                    check(!solo.isPublished(), "integrated server is not published to LAN");
                    FGASettings.playerPossession = "onlyfake";
                    CarpetSettings.commandPlayer = "ops";
                    CarpetSettings.allowSpawningOfflinePlayers = true;
                    server.services().nameToIdCache().add(NameAndId.createOffline("SoloFake"));
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "player SoloFake spawn at 4 100 4");
                    deadline = tick + 500;
                }
                if (phase == 0) {
                    fake = server.getPlayerList().getPlayerByName("SoloFake");
                    if (fake == null) {if (tick > deadline) throw new AssertionError("Fake login timeout"); return;}
                    owner.setGameMode(GameType.SPECTATOR);
                    fake.setGameMode(GameType.SPECTATOR);
                    server.getWorldData().setAllowCommands(scenario < 2);
                    FGASettings.permissionSwapsToo = scenario % 2 == 0;
                    CarpetSettings.commandPlayer = "ops";
                    originalOwnerLevel = level(solo, owner);
                    originalFakeLevel = level(solo, fake);
                    System.out.println("SOLO_PERMISSION_SCENARIO " + scenario + " cheats=" + (scenario < 2)
                            + " keepPermission=" + FGASettings.permissionSwapsToo
                            + " owner=" + originalOwnerLevel + " fake=" + originalFakeLevel);
                    check(solo.isSingleplayerOwner(new NameAndId(owner.getGameProfile())), "owner recognized before possession");
                    check(originalOwnerLevel == (scenario < 2 ? 4 : 0), "cheats flag controls original host permission");
                    check(originalFakeLevel == 0, "fake has no operator permission");
                    command = "fga possess SoloFake";
                    phase = 1; deadline = tick + 80;
                } else if (phase == 1 && tick >= deadline) {
                    check(PlayerPossessionManager.isParticipant(owner), "client packet started possession");
                    check(solo.isSingleplayerOwner(new NameAndId(owner.getGameProfile())), "owner recognized after renamed profile");
                    check(!solo.isSingleplayerOwner(new NameAndId(fake.getGameProfile())), "fake cannot inherit host ownership by display name");
                    check(level(solo, owner) == (FGASettings.permissionSwapsToo ? originalOwnerLevel : originalFakeLevel), "controller follows configured permission policy");
                    check(level(solo, fake) == originalFakeLevel, "fake never inherits host permission");
                    if (scenario == 3) {
                        CarpetSettings.commandPlayer = "false";
                        server.getCommands().sendCommands(owner);
                    }
                    command = "player SoloFake possess stop";
                    phase = 2; deadline = tick + 60;
                } else if (phase == 2 && tick >= deadline) {
                    boolean playerAllowed = scenario == 0;
                    check(PlayerPossessionManager.isParticipant(owner) != playerAllowed, "player root obeys configured permission");
                    if (!playerAllowed) {
                        command = "fga possess stop";
                        phase = 3; deadline = tick + 60;
                    } else {
                        command = "fga possess SoloFake";
                        phase = 4; deadline = tick + 60;
                    }
                } else if (phase == 3 && tick >= deadline) {
                    check(!PlayerPossessionManager.isParticipant(owner), "FGA stop works when player root is unavailable");
                    restored(solo);
                } else if (phase == 4 && tick >= deadline) {
                    check(PlayerPossessionManager.isParticipant(owner), "second packet starts possession");
                    command = "fga possess stop";
                    phase = 5; deadline = tick + 60;
                } else if (phase == 5 && tick >= deadline) {
                    check(!PlayerPossessionManager.isParticipant(owner), "FGA stop packet ends possession");
                    restored(solo);
                }
            } catch (Throwable error) {error.printStackTrace(); failures++; finish();}
        });
    }
    private void check(boolean result, String message) {
        checks++;
        if (!result) failures++;
        System.out.println("SOLO_PERMISSION_CHECK " + (result ? "PASS " : "FAIL ") + message);
    }
    private void finish() {
        System.out.println("SOLO_PERMISSION_RESULT checks=" + checks + " failures=" + failures);
        finished = true;
    }
    private static int level(IntegratedServer server, ServerPlayer player) {
        return server.getProfilePermissions(new NameAndId(player.getGameProfile())).level().id();
    }
    private void restored(IntegratedServer server) {
        check(!PlayerPossessionManager.isParticipant(owner), "session released");
        check(owner.getGameProfile().name().equals("ReproA") && fake.getGameProfile().name().equals("SoloFake"), "original display names restored");
        check(level(server, owner) == originalOwnerLevel && level(server, fake) == originalFakeLevel, "original permissions restored");
        check(!server.isPublished(), "test never opened LAN");
        if (++scenario == 4) finish(); else {phase = 0; deadline = tick + 500;}
    }
}
