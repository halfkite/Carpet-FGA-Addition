package carpet.fga.probe;

import carpet.CarpetSettings;
import carpet.fga.FGASettings;
import carpet.fga.FakePlayerProfilePreloadManager;
import carpet.patches.EntityPlayerMPFake;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class PreloadProbe implements ModInitializer {
    private int tick;
    private boolean done;
    @Override public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (done) return;
            try {
                ++tick;
                if (tick == 20) {
                    CarpetSettings.commandPlayer = "true";
                    CarpetSettings.allowSpawningOfflinePlayers = true;
                }
                for (int phase = 0; phase < 8; phase++) {
                    int start = 40 + phase * 120;
                    String mode = new String[]{"false", "always", "adaptive", "always", "adaptive", "always", "always", "always"}[phase];
                    if (tick == start) {
                        FakePlayerProfilePreloadManager.clearAll();
                        FGASettings.fakePlayerProfilePreload = mode;
                        String name = "VmpProbe" + phase;
                        server.services().nameToIdCache().add(NameAndId.createOffline(name));
                        System.out.println("PRELOAD_PROBE_BEGIN mode=" + mode);
                        if (phase == 5) cancelPending(server, name);
                        if (phase == 6) spawning().add(name.toLowerCase(Locale.ROOT));
                        if (phase == 7) server.services().nameToIdCache().add(
                            new NameAndId(NameAndId.createOffline("VmpProbe0").id(), name));
                    }
                    if (phase != 5 && tick >= start && tick < start + 20) {
                        int variant = 0;
                        for (String dim : new String[]{"overworld", "the_nether", "the_end"}) {
                            String commandName = "VmpProbe" + phase;
                            if (phase == 3 || phase == 4 || phase == 6) commandName = switch (variant++) {
                                case 0 -> phase == 4 ? commandName.toLowerCase(Locale.ROOT) : commandName;
                                case 1 -> phase == 4 ? commandName : commandName.toLowerCase(Locale.ROOT);
                                default -> commandName.toUpperCase(Locale.ROOT);
                            };
                            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                                "execute in minecraft:" + dim + " run player " + commandName + " spawn at 0 100 0");
                        }
                    }
                    if ((phase == 5 || phase == 6) && tick == start + 30) {
                        inspect(server, "VmpProbe" + phase, phase == 5 ? "cancelled" : "canonical-pending", 0);
                        if (phase == 6) spawning().remove(("VmpProbe" + phase).toLowerCase(Locale.ROOT));
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                            "player " + ("VmpProbe" + phase).toUpperCase(Locale.ROOT) + " spawn at 0 100 0");
                    }
                    if (tick == start + 100) {
                        inspect(server, phase == 7 ? "VmpProbe0" : "VmpProbe" + phase, "phase-" + phase + "-" + mode, 1);
                        if (phase == 7 && server.getPlayerList().getPlayerByName("VmpProbe7") != null) {
                            throw new AssertionError("Alias for an online UUID created a new player");
                        }
                    }
                }
                if (tick == 1000) {
                    System.out.println("PRELOAD_PROBE_COMPLETE");
                    done = true;
                    server.halt(false);
                }
            } catch (Throwable error) {
                error.printStackTrace();
                System.out.println("PRELOAD_PROBE_ERROR " + error);
                done = true;
                server.halt(false);
            }
        });
    }
    @SuppressWarnings("unchecked")
    private static Set<String> spawning() throws Exception {
        var field = EntityPlayerMPFake.class.getDeclaredField("spawning");
        field.setAccessible(true);
        return (Set<String>) field.get(null);
    }
    private static void cancelPending(MinecraftServer server, String name) throws Exception {
        // Hold both resolver threads so cancellation is deterministic, without network timing.
        var method = FakePlayerProfilePreloadManager.class.getDeclaredMethod("state", MinecraftServer.class);
        method.setAccessible(true);
        Object state = method.invoke(null, server);
        var field = state.getClass().getDeclaredField("executor");
        field.setAccessible(true);
        var executor = (ExecutorService) field.get(state);
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try {
            for (int i = 0; i < 2; i++) executor.execute(() -> {
                started.countDown();
                try { release.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            if (!started.await(5, TimeUnit.SECONDS)) throw new AssertionError("Resolver workers did not start");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "player " + name + " spawn at 0 100 0");
            FakePlayerProfilePreloadManager.clearAll();
        } finally { release.countDown(); }
    }
    private void inspect(MinecraftServer server, String name, String mode, int expected) {
        var uuid = NameAndId.createOffline(name).id();
        int entities = 0;
        int listed = 0;
        for (var p : server.getPlayerList().getPlayers()) if (p.getUUID().equals(uuid)) listed++;
        for (var level : server.getAllLevels()) {
            for (var e : level.getAllEntities()) {
                if (e.getUUID().equals(uuid)) {
                    entities++;
                    System.out.println("PRELOAD_PROBE_ENTITY mode=" + mode + " dimension=" + level.dimension()
                        + " uuid=" + uuid + " entity=" + e.getId() + " removed=" + e.isRemoved());
                }
            }
        }
        System.out.println("PRELOAD_PROBE_RESULT mode=" + mode + " listed=" + listed + " entities=" + entities);
        if (listed != expected || entities != expected) {
            throw new AssertionError(mode + " expected=" + expected + " listed=" + listed + " entities=" + entities);
        }
    }
}
