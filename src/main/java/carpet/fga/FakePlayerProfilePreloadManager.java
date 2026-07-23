//#if MC >= 1.21.1
package carpet.fga;

import carpet.CarpetSettings;
import carpet.patches.EntityPlayerMPFake;
import carpet.utils.Messenger;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
//#if MC >= 1.21.10
//$$ import net.minecraft.server.players.NameAndId;
//$$ import net.minecraft.server.players.UserNameToIdResolver;
//#else
import net.minecraft.server.players.GameProfileCache;
//#endif
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Moves profile-cache misses off the server thread before fake-player creation.
 * The preload/task model is adapted from Carpet Org Addition's MIT-licensed
 * BatchSpawnFakePlayerTask, with a bounded executor and Carpet command/API hooks.
 */
public final class FakePlayerProfilePreloadManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/fake-player-profile-preload");
    private static final long SECOND_ATTEMPT_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final long ADAPTIVE_ACTIVE_NANOS = TimeUnit.MINUTES.toNanos(2);
    private static final long PROFILE_TIMEOUT_SECONDS = 60L;
    private static final Map<MinecraftServer, ServerState> STATES = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Integer> COMMAND_PASSTHROUGH = ThreadLocal.withInitial(() -> 0);

    private FakePlayerProfilePreloadManager() {
    }

    public static boolean interceptCommandSpawn(CommandContext<CommandSourceStack> context) {
        if (REPLAYING.get()) {
            return false;
        }
        MinecraftServer server = context.getSource().getServer();
        if (!shouldPreload(server)) {
            return false;
        }
        String name = StringArgumentType.getString(context, "player");
        if (server.getPlayerList().getPlayerByName(name) != null) {
            return false;
        }
        Messenger.m(context.getSource(), "gi Preloading profile for " + name + "...");
        preload(server, name, context.getSource(), () -> replayCommand(context));
        return true;
    }

    public static boolean interceptDirectSpawn(String name, MinecraftServer server, Vec3 position,
                                               double yaw, double pitch, ResourceKey<Level> dimension,
                                               GameType gameMode, boolean flying) {
        if (REPLAYING.get() || COMMAND_PASSTHROUGH.get() > 0 || !shouldPreload(server)) {
            return false;
        }
        if (server.getPlayerList().getPlayerByName(name) != null) {
            return false;
        }
        preload(server, name, null, () -> withReplay(() -> EntityPlayerMPFake.createFake(
                name, server, position, yaw, pitch, dimension, gameMode, flying)));
        return true;
    }

    public static void beginCommandPassthrough() {
        COMMAND_PASSTHROUGH.set(COMMAND_PASSTHROUGH.get() + 1);
    }

    public static void endCommandPassthrough() {
        int depth = COMMAND_PASSTHROUGH.get() - 1;
        if (depth <= 0) {
            COMMAND_PASSTHROUGH.remove();
        } else {
            COMMAND_PASSTHROUGH.set(depth);
        }
    }

    public static synchronized void close(MinecraftServer server) {
        ServerState state = STATES.remove(server);
        if (state != null) {
            state.close();
        }
    }

    public static synchronized void clearAll() {
        STATES.values().forEach(state -> {
            state.close();
        });
        STATES.clear();
    }

    private static void replayCommand(CommandContext<CommandSourceStack> context) {
        withReplay(() -> context.getSource().getServer().getCommands()
                .performPrefixedCommand(context.getSource(), context.getInput()));
    }

    private static void preload(MinecraftServer server, String name,
                                CommandSourceStack source, Runnable continuation) {
        ServerState state = state(server);
        String key = name.toLowerCase(Locale.ROOT);
        CompletableFuture<Optional<GameProfile>> future = state.inFlight.computeIfAbsent(key,
                ignored -> CompletableFuture.supplyAsync(() -> resolveProfile(server, name), state.executor)
                        .orTimeout(PROFILE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        future.whenComplete((profile, error) -> server.execute(() -> {
            if (state.closed) {
                return;
            }
            state.inFlight.remove(key, future);
            if (error != null) {
                LOGGER.warn("Failed to preload fake-player profile {}", name, error);
                if (source != null) {
                    Messenger.m(source, "r Timed out or failed to preload profile for " + name);
                }
                return;
            }
            if (profile.isEmpty()) {
                if (source != null) {
                    Messenger.m(source, "r Player " + name + " does not exist or authentication servers are unavailable");
                } else {
                    LOGGER.warn("Profile preload returned no result for {}; fake player was not spawned", name);
                }
                return;
            }
            continuation.run();
        }));
    }

    private static Optional<GameProfile> resolveProfile(MinecraftServer server, String name) {
        //#if MC >= 1.21.10
        //$$ UserNameToIdResolver cache = server.services().nameToIdCache();
        //$$ Optional<NameAndId> cached = cache.get(name);
        //$$ if (cached.isPresent()) {
        //$$     NameAndId value = cached.get();
        //$$     return Optional.of(new GameProfile(value.id(), value.name()));
        //$$ }
        //$$ if (!CarpetSettings.allowSpawningOfflinePlayers) {
        //$$     return Optional.empty();
        //$$ }
        //$$ NameAndId offline = NameAndId.createOffline(name);
        //$$ cache.add(offline);
        //$$ return Optional.of(new GameProfile(offline.id(), offline.name()));
        //#else
        GameProfileCache cache = server.getProfileCache();
        Optional<GameProfile> profile = cache.get(name);
        if (profile.isPresent() || !CarpetSettings.allowSpawningOfflinePlayers) {
            return profile;
        }
        GameProfile offline = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
        cache.add(offline);
        return Optional.of(offline);
        //#endif
    }

    private static boolean shouldPreload(MinecraftServer server) {
        String mode = FGASettings.fakePlayerProfilePreload;
        if ("false".equals(mode)) {
            return false;
        }
        if ("always".equals(mode)) {
            return true;
        }
        if (!"adaptive".equals(mode)) {
            return false;
        }
        ServerState state = state(server);
        long now = System.nanoTime();
        synchronized (state) {
            if (now < state.activeUntilNanos) {
                state.activeUntilNanos = now + ADAPTIVE_ACTIVE_NANOS;
                state.lastAttemptNanos = now;
                return true;
            }
            boolean secondAttempt = state.lastAttemptNanos != 0L
                    && now - state.lastAttemptNanos <= SECOND_ATTEMPT_WINDOW_NANOS;
            state.lastAttemptNanos = now;
            if (secondAttempt) {
                state.activeUntilNanos = now + ADAPTIVE_ACTIVE_NANOS;
                return true;
            }
            state.activeUntilNanos = 0L;
            return false;
        }
    }

    private static synchronized ServerState state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new ServerState());
    }

    private static void withReplay(Runnable action) {
        boolean previous = REPLAYING.get();
        REPLAYING.set(true);
        try {
            action.run();
        } finally {
            if (previous) {
                REPLAYING.set(true);
            } else {
                REPLAYING.remove();
            }
        }
    }

    private static final class ServerState {
        private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
        private final Map<String, CompletableFuture<Optional<GameProfile>>> inFlight = new ConcurrentHashMap<>();
        private final ExecutorService executor = Executors.newFixedThreadPool(2, daemonThreadFactory());
        private volatile boolean closed;
        private long lastAttemptNanos;
        private long activeUntilNanos;

        private void close() {
            this.closed = true;
            this.inFlight.values().forEach(future -> future.cancel(true));
            this.inFlight.clear();
            this.executor.shutdownNow();
        }

        private static ThreadFactory daemonThreadFactory() {
            return runnable -> {
                Thread thread = new Thread(runnable,
                        "carpet-fga-profile-preload-" + THREAD_NUMBER.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            };
        }
    }
}
//#endif
