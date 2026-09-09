package fga.possessiontest;

import carpet.CarpetSettings;
import carpet.fga.FGASettings;
import carpet.fga.PlayerPossessionManager;
import carpet.patches.EntityPlayerMPFake;
import carpet.patches.FakeClientConnection;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/** Server-side regression checks for the PlayerControl state-swap implementation. */
public final class PossessionTest implements ModInitializer {
    private static final Deque<Runnable> STEPS = new ArrayDeque<>();
    private static MinecraftServer server;
    private static ServerPlayer body;
    private static ServerPlayer real;
    private static EntityPlayerMPFake fake;
    private static int checks;
    private static int ticks;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(value -> {
            server = value;
            try { prepare(); }
            catch (Throwable failure) { fail(failure); }
        });
        ServerTickEvents.END_SERVER_TICK.register(value -> {
            if (server == null) return;
            try {
                if (body != null) body.connection.tick();
                if (real != null) real.connection.tick();
                if (++ticks % 3 != 0) return;
                Runnable step = STEPS.poll();
                if (step != null) step.run();
                else {
                    System.out.println("FGA_POSSESSION_PASS checks=" + checks);
                    server.halt(false);
                    server = null;
                }
            } catch (Throwable failure) { fail(failure); }
        });
    }

    private static void prepare() {
        for (String rule : List.of("false", "true", "onlyfake", "opreal", "ops")) {
            for (boolean op : new boolean[]{false, true}) for (boolean isFake : new boolean[]{false, true}) {
                boolean expected = rule.equals("true") || rule.equals("onlyfake") && isFake
                        || rule.equals("opreal") && (op || isFake) || rule.equals("ops") && op;
                check(PlayerPossessionManager.allows(rule, op, isFake) == expected, "matrix " + rule + op + isFake);
            }
        }
        check(!PlayerPossessionManager.allows("unknown", true, true), "unknown rule fails closed");

        server.overworld().setDefaultSpawnPos(new net.minecraft.core.BlockPos(0, -60, 0), 0);
        body = add("Possessor");
        real = add("Possessed");
        GameProfile profile = new GameProfile(UUID.randomUUID(), "PossessionBot");
        fake = EntityPlayerMPFake.respawnFake(server, server.overworld(), profile, ClientInformation.createDefault());
        server.getPlayerList().placeNewPlayer(new FakeClientConnection(PacketFlow.SERVERBOUND), fake,
                new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false));
        body.teleportTo(server.overworld(), 0, -60, 0, 0, 0);
        real.teleportTo(server.overworld(), 6, -60, 0, 0, 0);
        fake.teleportTo(server.overworld(), 12, -60, 0, 0, 0);
        body.setGameMode(GameType.SURVIVAL);
        real.setGameMode(GameType.CREATIVE);
        fake.setGameMode(GameType.SURVIVAL);
        body.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        real.getInventory().setItem(0, new ItemStack(Items.COMMAND_BLOCK));
        fake.getInventory().setItem(0, new ItemStack(Items.STONE, 12));
        body.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, 5));
        fake.getEnderChestInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 7));
        CarpetSettings.commandPlayer = "true";
        FGASettings.playerPossession = "onlyfake";

        STEPS.add(() -> {
            check(PlayerPossessionManager.start(body, real).equals("denied"), "onlyfake rejects real");
            check(runCommand("player PossessionBot possess", body) == 1, "command starts fake");
            check(PlayerPossessionManager.isParticipant(body), "controller is participant");
            check(body.getGameProfile().getName().equals("PossessionBot"), "controller profile swapped");
            check(fake.getGameProfile().getName().equals("Possessor"), "fake profile swapped");
            check(body.getInventory().getItem(0).is(Items.STONE) && fake.getInventory().getItem(0).is(Items.DIAMOND),
                    "state and inventory swapped");
            check(body.getX() > 11 && fake.getX() < 1, "positions swapped");
        });
        STEPS.add(() -> {
            check(PlayerPossessionManager.stop(body, "PossessionBot"), "controller exits by swapped name");
            check(!PlayerPossessionManager.isParticipant(body), "session removed");
            check(body.getGameProfile().getName().equals("Possessor") && fake.getGameProfile().getName().equals("PossessionBot"),
                    "profiles restored");
            check(body.getInventory().getItem(0).is(Items.DIAMOND) && fake.getInventory().getItem(0).is(Items.STONE),
                    "inventories restored to owners");
            check(body.getEnderChestInventory().getItem(0).is(Items.EMERALD)
                    && fake.getEnderChestInventory().getItem(0).is(Items.GOLD_INGOT), "ender chests stay owned");
        });
        STEPS.add(() -> {
            FGASettings.playerPossession = "true";
            check(PlayerPossessionManager.start(body, real) == null, "true allows real");
            check(body.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "target game mode applied");
            check(body.getInventory().getItem(0).is(Items.COMMAND_BLOCK), "target inventory applied");
        });
        STEPS.add(() -> {
            check(PlayerPossessionManager.stop(real, "Possessor"), "target exits by partner name");
            check(!PlayerPossessionManager.isParticipant(real), "real session removed");
            FGASettings.playerPossession = "opreal";
            check(PlayerPossessionManager.start(body, real).equals("denied"), "opreal rejects non-op real");
            server.getPlayerList().op(body.getGameProfile());
            check(PlayerPossessionManager.start(body, real) == null, "opreal allows op real");
        });
        STEPS.add(() -> {
            FGASettings.playerPossession = "false";
            PlayerPossessionManager.onRuleChanged();
            check(!PlayerPossessionManager.isParticipant(body), "rule change releases session");
            server.getPlayerList().deop(body.getGameProfile());
            FGASettings.playerPossession = "true";
            check(PlayerPossessionManager.start(body, real) == null, "start before disconnect");
            PlayerPossessionManager.disconnected(real, server);
            check(!PlayerPossessionManager.isParticipant(body), "disconnect releases both players");
        });
        STEPS.add(() -> {
            Boat boat = new Boat(net.minecraft.world.entity.EntityType.BOAT, fake.serverLevel());
            boat.setPos(fake.position());
            fake.serverLevel().addFreshEntity(boat);
            check(fake.startRiding(boat, true), "fake has mount before swap");
            check(PlayerPossessionManager.start(body, fake) == null, "fake swap with mount");
            check(body.getVehicle() == boat, "mount follows swapped state");
            check(PlayerPossessionManager.stop(body, "PossessionBot"), "mounted session exits");
            check(fake.getVehicle() == boat && body.getVehicle() == null, "mount restored to fake");
        });
        STEPS.add(() -> {
            FGASettings.playerPossession = "onlyfake";
            fake.teleportTo(server.getLevel(net.minecraft.world.level.Level.NETHER), 0, 80, 0, 0, 0);
            check(PlayerPossessionManager.start(body, fake) == null, "cross-dimension fake swap");
            check(body.serverLevel().dimension() == net.minecraft.world.level.Level.NETHER, "controller enters target dimension");
            check(fake.serverLevel().dimension() == net.minecraft.world.level.Level.OVERWORLD, "fake enters controller dimension");
            check(PlayerPossessionManager.stop(body, "PossessionBot"), "cross-dimension session exits");
        });
        STEPS.add(() -> {
            check(PlayerPossessionManager.start(body, fake) == null, "death session starts");
            fake.hurt(fake.damageSources().genericKill(), Float.MAX_VALUE);
            check(!PlayerPossessionManager.isParticipant(body), "death releases session before drops");
            FGASettings.playerPossession = "false";
        });
    }

    private static ServerPlayer add(String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        server.getPlayerList().placeNewPlayer(new Capture(), player,
                new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false));
        return player;
    }

    private static int runCommand(String command, ServerPlayer actor) {
        try { return server.getCommands().getDispatcher().execute(command, actor.createCommandSourceStack()); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) { throw new AssertionError(exception); }
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }

    private static void fail(Throwable failure) {
        System.out.println("FGA_POSSESSION_FAIL " + failure);
        failure.printStackTrace();
        if (server != null) server.halt(false);
        server = null;
        STEPS.clear();
    }

    private static final class Capture extends FakeClientConnection {
        private final List<net.minecraft.network.protocol.Packet<?>> packets = new ArrayList<>();
        Capture() { super(PacketFlow.SERVERBOUND); }

        @Override
        public void send(net.minecraft.network.protocol.Packet<?> packet,
                         net.minecraft.network.PacketSendListener callback, boolean flush) {
            packets.add(packet);
            if (callback != null) callback.onSuccess();
        }
    }
}
