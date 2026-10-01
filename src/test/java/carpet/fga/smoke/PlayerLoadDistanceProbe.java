//#if MC == 1.21.1
package carpet.fga.smoke;

import carpet.fga.FGASettings;
import carpet.fga.PlayerLoadDistanceManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.lang.reflect.Field;
import java.util.Map;

/** Test-only: emulate integrated-server view changes without touching a real world. */
public final class PlayerLoadDistanceProbe implements DedicatedServerModInitializer {
    private static ServerPlayer player;
    private static int stage = -1, ticks;

    public void onInitializeServer() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("fgaDistanceProbe")
                        .then(Commands.argument("player", StringArgumentType.word()).executes(context -> {
                            start(context.getSource().getServer(), StringArgumentType.getString(context, "player"));
                            return 1;
                        }))));
        ServerTickEvents.END_SERVER_TICK.register(PlayerLoadDistanceProbe::tick);
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }

    private static boolean empty(String fieldName) throws Exception {
        Field field = PlayerLoadDistanceManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).isEmpty();
    }

    private static void rule(String value) {
        FGASettings.playerLoadDistance = value;
        PlayerLoadDistanceManager.onRuleChanged();
    }

    private static void start(MinecraftServer server, String name) {
        require(stage < 0, "probe already running");
        player = server.getPlayerList().getPlayerByName(name);
        require(player != null, "fixture player missing");
        rule("false");
        PlayerLoadDistanceManager.clear();
        server.getPlayerList().setViewDistance(10);
        PlayerLoadDistanceManager.load(server);
        require(server.getPlayerList().getViewDistance() == 10, "disabled load changed distance");
        server.getPlayerList().setViewDistance(16);
        stage = 0;
        ticks = 0;
        System.out.println("FGA_DISTANCE_PROBE_BEGIN");
    }

    private static void tick(MinecraftServer server) {
        if (stage < 0) return;
        try {
            if (stage == 0 || stage == 2 || stage == 4) {
                int expected = stage == 4 ? 12 : 16;
                PlayerLoadDistanceManager.onLogin(player);
                PlayerLoadDistanceManager.onLogout(player);
                PlayerLoadDistanceManager.onRuleChanged();
                PlayerLoadDistanceManager.tick(server);
                require(server.getPlayerList().getViewDistance() == expected, "false callback rewrote vanilla distance");
                require(empty("APPLIED") && empty("TICKETS"), "disabled tracking state not empty");
                if (++ticks < 40) return;
            }
            if (stage == 0) {
                PlayerLoadDistanceManager.clear();
                require(server.getPlayerList().getViewDistance() == 16, "disabled shutdown restored stale startup distance");
                PlayerLoadDistanceManager.load(server);
                server.getPlayerList().op(player.getGameProfile());
                rule("true");
                PlayerLoadDistanceManager.set(player, 24, false);
                require(server.getPlayerList().getViewDistance() == 24, "OP global override not applied");
                stage = 1;
            } else if (stage == 1) {
                rule("false");
                require(server.getPlayerList().getViewDistance() == 16, "owned global override not restored");
                stage = 2; ticks = 0;
            } else if (stage == 2) {
                rule("true");
                PlayerLoadDistanceManager.set(player, PlayerLoadDistanceManager.NONE, false);
                require(!empty("APPLIED"), "none override was not applied");
                rule("false");
                require(empty("APPLIED") && empty("TICKETS"), "none override cleanup failed");
                server.getPlayerList().setViewDistance(20);
                rule("true");
                PlayerLoadDistanceManager.set(player, 28, false);
                require(server.getPlayerList().getViewDistance() == 28, "second global override not applied");
                rule("false");
                require(server.getPlayerList().getViewDistance() == 20, "second activation used stale baseline");
                rule("true");
                PlayerLoadDistanceManager.set(player, 24, false);
                server.getPlayerList().setViewDistance(12);
                rule("false");
                require(server.getPlayerList().getViewDistance() == 12, "another writer was overwritten");
                stage = 4; ticks = 0;
            } else if (stage == 4) {
                PlayerLoadDistanceManager.clear();
                require(server.getPlayerList().getViewDistance() == 12, "disabled clear touched another writer");
                PlayerLoadDistanceManager.load(server);
                System.out.println("FGA_DISTANCE_PROBE_PASS: disabled=true restore-once=true none-cleanup=true current-baseline=true external-writer=true");
                stage = -1;
            }
        } catch (Exception failure) {
            System.out.println("FGA_DISTANCE_PROBE_FAIL: stage=" + stage + " " + failure);
            stage = -1;
        }
    }
}
//#endif
