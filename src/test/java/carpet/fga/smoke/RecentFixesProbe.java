//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga.smoke;

import carpet.CarpetSettings;
import carpet.fga.FGASettings;
import carpet.fga.FakePlayerProfilePreloadManager;
import carpet.fga.PlayerPossessionManager;
import carpet.fga.SwapSnapshot;
import carpet.patches.EntityPlayerMPFake;
import carpet.patches.FakeClientConnection;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
//#if MC >= 1.21.10
//$$ import net.minecraft.server.players.NameAndId;
//#endif
import net.minecraft.stats.Stats;
import net.minecraft.world.level.GameType;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Opt-in test addon, never included in distributable FGA jars. */
public final class RecentFixesProbe implements ModInitializer {
    private int tick;
    private int checks;
    private int phase;
    private int phaseStart;
    private int settled;
    private boolean done;
    private String name;

    @Override public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (done || !server.isDedicatedServer()) return;
            try {
                tick++;
                if (tick == 20) verifyStats(server);
                if (tick < 40) return;
                if (phaseStart == 0) {
                    phaseStart = tick;
                    name = "RecentBot" + phase;
                    seed(server, name);
                    FakePlayerProfilePreloadManager.clearAll();
                    FGASettings.fakePlayerProfilePreload = phase % 2 == 0 ? "always" : "adaptive";
                }
                int age = tick - phaseStart;
                if (age < 20) {
                    int variant = 0;
                    for (String dimension : new String[]{"overworld", "the_nether", "the_end"}) {
                        String spelling = name;
                        if (phase >= 2) spelling = switch (variant++) {
                            case 0 -> phase == 3 ? name.toLowerCase(Locale.ROOT) : name;
                            case 1 -> name.toUpperCase(Locale.ROOT);
                            default -> name.toLowerCase(Locale.ROOT);
                        };
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                                "execute in minecraft:" + dimension + " run player " + spelling + " spawn at 0 100 0");
                    }
                }
                if (age >= 40) {
                    int count = entities(server, id(name));
                    check(count <= 1, "no duplicate entity phase=" + phase + " count=" + count);
                    if (count == 1 && ++settled >= 20) {
                        check(server.getPlayerList().getPlayers().stream().filter(p -> p.getUUID().equals(id(name))).count() == 1,
                                "one player-list entry phase=" + phase);
                        System.out.println("RECENT_FIXES_SPAWN_PASS phase=" + phase);
                        if (++phase == 4) {
                            verifyPending(server);
                            System.out.println("RECENT_FIXES_PASS checks=" + checks);
                            done = true;
                            server.halt(false);
                        } else {phaseStart = 0; settled = 0;}
                    }
                    if (age > 1200) throw new AssertionError("spawn timeout phase=" + phase);
                }
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.out.println("RECENT_FIXES_FAIL " + failure);
                done = true;
                server.halt(false);
            }
        });
    }

    private void verifyStats(MinecraftServer server) {
        CarpetSettings.commandPlayer = "true";
        CarpetSettings.allowSpawningOfflinePlayers = true;
        FGASettings.playerPossession = "true";
        ServerPlayer a = add(server, "StatsActor");
        ServerPlayer b = add(server, "StatsTarget");
        var jump = Stats.CUSTOM.get(Stats.JUMP);
        var fish = Stats.CUSTOM.get(Stats.FISH_CAUGHT);
        var deaths = Stats.CUSTOM.get(Stats.DEATHS);
        a.getStats().setValue(a, jump, 1000);
        a.getStats().setValue(a, fish, 50);
        b.getStats().setValue(b, jump, 10);
        b.getStats().setValue(b, deaths, 7);
        SwapSnapshot snapshot = SwapSnapshot.capture(a);
        a.getStats().setValue(a, jump, 17);
        a.getStats().setValue(a, deaths, 9);
        snapshot.applyTo(a, snapshot.gameProfile());
        check(a.getStats().getValue(jump) == 1000 && a.getStats().getValue(deaths) == 0, "independent snapshot and missing-item clear");
        snapshot.applyTo(a, snapshot.gameProfile());
        check(a.getStats().getValue(jump) == 1000, "snapshot reusable");
        check(PlayerPossessionManager.start(a, b) == null, "possession starts");
        check(a.getStats().getValue(jump) == 10 && a.getStats().getValue(fish) == 0, "controller receives target stats");
        check(b.getStats().getValue(jump) == 1000 && b.getStats().getValue(fish) == 50 && b.getStats().getValue(deaths) == 0, "target receives controller stats without residue");
        a.getStats().increment(a, jump, 3);
        check(PlayerPossessionManager.stopCurrent(a), "possession ends");
        check(a.getStats().getValue(jump) == 1000 && a.getStats().getValue(fish) == 50 && a.getStats().getValue(deaths) == 0, "controller stats restored");
        check(b.getStats().getValue(jump) == 13 && b.getStats().getValue(fish) == 0 && b.getStats().getValue(deaths) == 7, "earned stats follow target identity");
        System.out.println("RECENT_FIXES_STATS_PASS");
    }

    private void verifyPending(MinecraftServer server) throws Exception {
        //#if MC < 1.21.4
        CompletableFuture<Void> first = new CompletableFuture<>();
        CompletableFuture<Void> second = new CompletableFuture<>();
        FakePlayerProfilePreloadManager.trackLegacySpawn(server, "PendingBot", first);
        FakePlayerProfilePreloadManager.trackLegacySpawn(server, "pendingbot", second);
        FakePlayerProfilePreloadManager.clearAll();
        var method = FakePlayerProfilePreloadManager.class.getDeclaredMethod("isSpawnPending", MinecraftServer.class, String.class, String.class);
        method.setAccessible(true);
        check((boolean) method.invoke(null, server, "PENDINGBOT", "PendingBot"), "pending spawn survives rule reset");
        first.complete(null);
        check((boolean) method.invoke(null, server, "PENDINGBOT", "PendingBot"), "first completion retains second pending spawn");
        second.completeExceptionally(new IllegalStateException("test-only spawn failure"));
        check(!(boolean) method.invoke(null, server, "PENDINGBOT", "PendingBot"), "failure releases last reservation");
        //#else
        //$$ var field = EntityPlayerMPFake.class.getDeclaredField("spawning");
        //$$ field.setAccessible(true);
        //$$ @SuppressWarnings("unchecked") var pending = (java.util.Set<String>) field.get(null);
        //$$ var method = FakePlayerProfilePreloadManager.class.getDeclaredMethod("isSpawnPending", MinecraftServer.class, String.class, String.class);
        //$$ method.setAccessible(true);
        //$$ pending.add("pendingbot");
        //$$ try {check((boolean) method.invoke(null, server, "PENDINGBOT", "PendingBot"), "case-insensitive Carpet pending marker");}
        //$$ finally {pending.remove("pendingbot");}
        //#endif
    }

    private static UUID id(String name) {return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));}
    private static void seed(MinecraftServer server, String name) {
        //#if MC >= 1.21.10
        //$$ server.services().nameToIdCache().add(NameAndId.createOffline(name));
        //#else
        server.getProfileCache().add(new GameProfile(id(name), name));
        //#endif
    }
    private static ServerPlayer add(MinecraftServer server, String name) {
        GameProfile profile = new GameProfile(id(name), name);
        var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        server.getPlayerList().placeNewPlayer(new FakeClientConnection(PacketFlow.SERVERBOUND), player,
                new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false));
        player.setGameMode(GameType.SPECTATOR);
        return player;
    }
    private static int entities(MinecraftServer server, UUID id) {
        int count = 0;
        for (var level : server.getAllLevels()) for (var entity : level.getAllEntities()) {
            if (entity.getUUID().equals(id)) count++;
        }
        return count;
    }
    private void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
        checks++;
    }
}
//#endif
