package cn.bamgdam.rankboard;

import carpet.CarpetSettings;
import carpet.fga.FGASettings;
import carpet.fga.PlayerPossessionManager;
import carpet.fga.SwapSnapshot;
import carpet.patches.FakeClientConnection;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.GameType;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Opt-in integration test against packaged FGA, Carpet and RankBoard on a disposable 26.3 server. */
public final class PossessionStatsTest implements ModInitializer {
    private static final Stat<?> JUMP = Stats.CUSTOM.get(Stats.JUMP);
    private static final Stat<?> FISH = Stats.CUSTOM.get(Stats.FISH_CAUGHT);
    private static final Stat<?> DEATHS = Stats.CUSTOM.get(Stats.DEATHS);
    private static final boolean VERIFY_OFFLINE_RANKING = Boolean.getBoolean("fga.test.verifyOfflineRanking");
    private final Map<UUID, Capture> connections = new HashMap<>();
    private ServerPlayer a;
    private ServerPlayer b;
    private boolean bLoggedOut;
    private int tick;
    private int checks;
    private boolean finished;

    @Override public void onInitialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (VERIFY_OFFLINE_RANKING && !finished && handler.getPlayer() == b) {
                System.out.println("POSSESSION_STATS_DISCONNECT_OBSERVER name=" + b.getGameProfile().name()
                        + " jumps=" + b.getStats().getValue(JUMP)
                        + " active=" + PlayerPossessionManager.isParticipant(b)
                        + " thread=" + Thread.currentThread().getName());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished) return;
            try {
                if (a != null) a.connection.tick();
                if (b != null && !bLoggedOut) b.connection.tick();
                switch (++tick) {
                    case 80 -> {
                        CarpetSettings.commandPlayer = "true";
                        FGASettings.playerPossession = "true";
                        FGASettings.showControllerPrefix = false;
                        a = add(server, "ReproA");
                        b = add(server, "ReproB");
                        a.getStats().setValue(a, JUMP, 1000);
                        a.getStats().setValue(a, FISH, 50);
                        b.getStats().setValue(b, JUMP, 10);
                        b.getStats().setValue(b, DEATHS, 7);
                        sync(a); sync(b);
                        BoardService.enable(a.createCommandSourceStack(), RankBoardMod.Period.ALL, RankBoardMod.Metric.JUMPS);
                        BoardService.enable(b.createCommandSourceStack(), RankBoardMod.Period.ALL, RankBoardMod.Metric.JUMPS);
                        checkValues(a, 1000, 50, 0);
                        checkValues(b, 10, 0, 7);
                        checkRanking(server, 1000, 10);

                        SwapSnapshot snapshot = SwapSnapshot.capture(a);
                        a.getStats().setValue(a, JUMP, 17);
                        a.getStats().setValue(a, DEATHS, 9);
                        snapshot.applyTo(a, snapshot.gameProfile());
                        checkValues(a, 1000, 50, 0);
                        a.getStats().setValue(a, JUMP, 19);
                        snapshot.applyTo(a, snapshot.gameProfile());
                        checkValues(a, 1000, 50, 0);
                        System.out.println("POSSESSION_STATS_SNAPSHOT_PASS");
                    }
                    case 100 -> {
                        start();
                        checkValues(a, 10, 0, 7);
                        checkValues(b, 1000, 50, 0);
                        check(PlayerPossessionManager.stopCurrent(b), "target can end session");
                        checkRestored(server, 1000, 50, 0, 10, 0, 7);
                        System.out.println("POSSESSION_STATS_ROUNDTRIP_PASS");
                    }
                    case 120 -> {
                        start();
                        a.getStats().increment(a, JUMP, 3);
                        a.getStats().increment(a, FISH, 2);
                        b.getStats().increment(b, JUMP, 5);
                        b.getStats().increment(b, DEATHS, 4);
                        check(PlayerPossessionManager.stopCurrent(a), "controller can end session");
                        checkRestored(server, 1005, 50, 4, 13, 2, 7);
                        System.out.println("POSSESSION_STATS_PROGRESS_PASS");
                    }
                    case 140 -> {
                        for (int i = 0; i < 3; i++) {
                            start();
                            check(PlayerPossessionManager.stopCurrent(i % 2 == 0 ? a : b), "repeated stop " + i);
                            checkRestored(server, 1005, 50, 4, 13, 2, 7);
                        }
                        System.out.println("POSSESSION_STATS_REPEATED_PASS");
                    }
                    case 160 -> {
                        start();
                        FGASettings.playerPossession = "false";
                        PlayerPossessionManager.onRuleChanged();
                        checkRestored(server, 1005, 50, 4, 13, 2, 7);
                        FGASettings.playerPossession = "true";
                        System.out.println("POSSESSION_STATS_RULE_DISABLE_PASS");
                    }
                    case 180 -> {
                        start();
                        if (VERIFY_OFFLINE_RANKING)
                            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(b.connection, server);
                        server.getPlayerList().remove(b);
                        bLoggedOut = true;
                        checkValues(a, 1005, 50, 4);
                        checkValues(b, 13, 2, 7);
                        check(!PlayerPossessionManager.isParticipant(a), "disconnect ends session");
                        check(a.getGameProfile().name().equals("ReproA"), "disconnect restores owner");
                        if (VERIFY_OFFLINE_RANKING) checkRanking(server, 1005, 13);
                        System.out.println("POSSESSION_STATS_DISCONNECT_PASS");
                    }
                    case 200 -> {
                        System.out.println("POSSESSION_STATS_PASS checks=" + checks);
                        finished = true;
                        server.halt(false);
                    }
                }
            } catch (Throwable failure) {
                System.out.println("POSSESSION_STATS_FAIL " + failure);
                failure.printStackTrace();
                finished = true;
                server.halt(false);
            }
        });
    }

    private ServerPlayer add(MinecraftServer server, String name) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)), name);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        Capture connection = new Capture();
        connections.put(player.getUUID(), connection);
        server.getPlayerList().placeNewPlayer(connection, player,
                new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false));
        player.setGameMode(GameType.SPECTATOR);
        return player;
    }

    private void start() {
        String result = PlayerPossessionManager.start(a, b);
        check(result == null, "start session: " + result);
    }

    private void checkRestored(MinecraftServer server, int aj, int af, int ad, int bj, int bf, int bd) {
        checkValues(a, aj, af, ad);
        checkValues(b, bj, bf, bd);
        check(a.getGameProfile().name().equals("ReproA") && b.getGameProfile().name().equals("ReproB"), "names restored");
        check(!PlayerPossessionManager.isParticipant(a) && !PlayerPossessionManager.isParticipant(b), "session cleared");
        checkRanking(server, aj, bj);
    }

    private void checkValues(ServerPlayer player, int jump, int fish, int deaths) {
        for (var entry : Map.of(JUMP, jump, FISH, fish, DEATHS, deaths).entrySet()) {
            check(player.getStats().getValue(entry.getKey()) == entry.getValue(),
                    player.getGameProfile().name() + " server " + entry.getKey() + " expected=" + entry.getValue()
                            + " actual=" + player.getStats().getValue(entry.getKey()));
            check(connections.get(player.getUUID()).clientValues.getOrDefault(entry.getKey(), 0).equals(entry.getValue()),
                    player.getGameProfile().name() + " client packet " + entry.getKey() + " expected=" + entry.getValue());
        }
    }

    private void checkRanking(MinecraftServer server, int aj, int bj) {
        BoardService.refreshAll(server);
        var entries = RankBoardMod.entries(server, RankBoardMod.Period.ALL, RankBoardMod.Metric.JUMPS);
        for (var expected : Map.of(a.getUUID(), aj, b.getUUID(), bj).entrySet()) {
            var entry = entries.stream().filter(row -> row.uuid().equals(expected.getKey())).findFirst().orElseThrow();
            check(entry.value() == expected.getValue(), "RankBoard UUID value " + entry);
            check(entry.name().equals(expected.getKey().equals(a.getUUID()) ? "ReproA" : "ReproB"), "RankBoard UUID name " + entry);
        }
    }

    private static void sync(ServerPlayer player) {
        player.getStats().markAllDirty();
        player.getStats().sendStats(player);
    }

    private void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        checks++;
    }

    private static final class Capture extends FakeClientConnection {
        private final Map<Stat<?>, Integer> clientValues = new HashMap<>();
        Capture() { super(PacketFlow.SERVERBOUND); }
        @Override public void send(Packet<?> packet, ChannelFutureListener callback, boolean flush) {
            if (packet instanceof ClientboundAwardStatsPacket stats) clientValues.putAll(stats.stats());
            super.send(packet, callback, flush);
        }
    }
}
