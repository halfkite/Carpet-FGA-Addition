//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Refreshes a held map by streaming it: the chunks a sample needs are handed to the vanilla chunk
 * pipeline through region tickets and only rendered once they are already loaded, so the server
 * thread never waits for chunk generation or disk reads, and each tick only spends its budget on a
 * few of the 16 column passes a sample needs. All state is main-thread only.
 */
public final class MapLoadManager {
    /**
     * Vanilla MapItem.update walks the columns whose x matches the holding player's step, one of 16
     * residue classes per call, so one sample window is only complete after 16 passes. They are
     * spread over ticks instead of being run back to back, which keeps a tick's cost to a fraction
     * of one sample.
     */
    private static final int UPDATE_PASSES = 16;
    /**
     * Ticket radius 0 lands on ChunkLevel.byStatus(FULL) == 33: the chunk is loaded, but it is
     * neither block- nor entity-ticking, so nothing spawns or ticks because of a map load.
     */
    private static final int TICKET_RADIUS = 0;
    /** Safety margin in map pixels when deciding which chunks a sample can touch. */
    private static final int WINDOW_MARGIN = 1;
    /** Whole-tick budget shared by all tasks, and the floor one task is handed from it. */
    private static final long GLOBAL_TICK_BUDGET_NANOS = 3_000_000L;
    private static final long MIN_TASK_BUDGET_NANOS = 500_000L;
    /** Sanity bound on passes per task per tick; the time budget normally stops the loop first. */
    private static final int MAX_PASSES_PER_TICK = 64;
    /** Samples whose chunks are requested before the cursor reaches them. */
    private static final int PREFETCH_LOOKAHEAD_SAMPLES = 4;
    /**
     * Ticket throttle, counted per task so concurrent loads cannot starve each other: new tickets
     * per tick, and tickets held at once. What actually shows up as stutter is how many chunks are
     * in flight at once, so the in-flight cap is the knob: smooth holds 384 (about one sample
     * window, close to what a walking player keeps loaded), fast holds 1024. The per-tick rate only
     * decides how fast the cap is filled, so both modes use the same generous rate and an already
     * generated map is limited by the chunk pipeline itself instead of by us. The cap stays above
     * the widest sample footprint (289 chunks, the 17x17 window every map scale produces), otherwise
     * a task could never finish the sample it is on.
     */
    private static final int SMOOTH_NEW_TICKETS_PER_TICK = 128;
    private static final int SMOOTH_IN_FLIGHT_CHUNKS = 384;
    private static final int FAST_NEW_TICKETS_PER_TICK = 256;
    private static final int FAST_IN_FLIGHT_CHUNKS = 1024;
    private static final int WAITING_REPORT_INTERVAL_TICKS = 10;
    /** Long loads also report in chat, so a slow chunk generation phase cannot look like a hang. */
    private static final int CHAT_REPORT_INTERVAL_TICKS = 600;
    /**
     * Safety valve: if one sample cannot get its chunks for this long, skip it and carry on. A load
     * must always be able to finish; a chunk that refuses to load costs one sample instead of hanging
     * the whole task, and the skipped count is reported when the load ends.
     */
    private static final int STALL_SKIP_TICKS = 600;
    /** Progress messages are throttled to this many ticks so a fast load cannot spam the action bar. */
    private static final int PROGRESS_REPORT_INTERVAL_TICKS = 5;
    private static final String MESSAGE_PREFIX = "carpet.fga.map_load.";
    //#if MC >= 1.21.5
    //#if MC >= 1.21.10
    //$$ private static final TicketType LOAD_TICKET =
    //$$         new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);
    //#else
    //$$ private static final TicketType LOAD_TICKET =
    //$$         new TicketType(TicketType.NO_TIMEOUT, false, TicketType.TicketUse.LOADING);
    //#else
    //#endif
    //#else
    private static final TicketType<ChunkPos> LOAD_TICKET =
            TicketType.create("carpet_fga_map_load", (first, second) -> Long.compare(first.toLong(), second.toLong()));
    //#endif
    /**
     * Player-facing text is resolved here instead of on the client: this is a server side feature,
     * so a client without FGA has none of our translation keys and would show the raw key. The
     * language follows the JVM locale, the same way the server console picks one.
     */
    private static final Map<String, String> MESSAGES = loadMessages();
    private static final Map<UUID, Task> TASKS = new HashMap<>();
    /**
     * Tasks that are done but still hold tickets. Tickets are handed back at a trickle instead of all
     * at once: a mass release drops the level of thousands of chunks in the same tick, and pulling a
     * ticket from a chunk that is still generating can leave it stuck in the unload queue forever
     * (it then burns CPU every tick without finishing).
     */
    private static final List<Task> DRAINING = new ArrayList<>();
    private static final int RELEASE_PER_TICK = 32;
    /**
     * A chunk that has not reached FULL within this long after we asked for it keeps blocking the
     * in-flight budget, so its ticket is handed back as well. Without this a chunk whose generation
     * never completes would fill the cap and the whole task would sit still forever.
     */
    private static final long TICKET_GIVE_UP_NANOS = 10_000_000_000L;

    private MapLoadManager() {
    }

    public static boolean start(ServerPlayer player, MapItem mapItem, MapId mapId, MapItemSavedData data,
                                Mode mode) {
        if (TASKS.containsKey(player.getUUID())) return false;
        Task task = new Task(player, mapItem, mapId, data, mode);
        TASKS.put(player.getUUID(), task);
        FGACompat.displayClientMessage(player, text("carpet.fga.map_load.progress", 0, mode.id(), 0, task.mapTotal), true);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (TASKS.isEmpty()) return;
        drainReleases();
        if (TASKS.isEmpty()) return;
        long share = Math.max(MIN_TASK_BUDGET_NANOS, GLOBAL_TICK_BUDGET_NANOS / TASKS.size());
        // Request chunks for every task before rendering anything, so a task that renders its share
        // cannot hold up the generation the other tasks are waiting on.
        for (Task task : TASKS.values()) task.prefetch();
        Iterator<Task> iterator = TASKS.values().iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (task.tick(share)) iterator.remove();
        }
    }

    public static void clear() {
        // Server is going down: vanilla saves and unloads everything itself, and handing back every
        // ticket here would only add a mass unload on top of that.
        TASKS.clear();
        DRAINING.clear();
    }

    private static void startDraining(Task task) {
        if (!task.ticketed.isEmpty() && !DRAINING.contains(task)) DRAINING.add(task);
    }

    private static void drainReleases() {
        if (DRAINING.isEmpty()) return;
        Iterator<Task> iterator = DRAINING.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (task.releaseUnneededTickets(null, RELEASE_PER_TICK)) iterator.remove();
        }
    }

    /** @return false when the player has no active load */
    public static boolean pause(ServerPlayer player) {
        Task task = TASKS.get(player.getUUID());
        if (task == null || task.paused) return false;
        task.paused = true;
        startDraining(task);
        FGACompat.displayClientMessage(player, text("carpet.fga.map_load.paused", task.percent()), true);
        return true;
    }

    /** @return false when the player has no paused load */
    public static boolean resume(ServerPlayer player) {
        Task task = TASKS.get(player.getUUID());
        if (task == null || !task.paused) return false;
        task.paused = false;
        DRAINING.remove(task);
        task.countMapChunks();
        FGACompat.displayClientMessage(player, text("carpet.fga.map_load.progress", task.percent(), task.mode.id(),
                task.mapReady, task.mapTotal), true);
        return true;
    }

    /** @return false when the player has no active load */
    public static boolean stop(ServerPlayer player) {
        Task task = TASKS.remove(player.getUUID());
        if (task == null) return false;
        startDraining(task);
        return true;
    }

    public static List<String> activeNames() {
        List<String> names = new ArrayList<>();
        for (Task task : TASKS.values()) names.add(task.playerName());
        return names;
    }

    /** One chat line per active task, with a clickable pause/resume and stop button. */
    public static List<Component> listLines() {
        List<Component> lines = new ArrayList<>();
        int index = 1;
        for (Task task : TASKS.values()) {
            String name = task.playerName();
            task.countMapChunks();
            MutableComponent line = Component.literal(index + ". ").withStyle(ChatFormatting.GRAY)
                    .append(text("carpet.fga.map_load.list.line", name, task.percent(), task.mode.id(),
                            task.mapReady, task.mapTotal, task.statusText()))
                    .append(Component.literal("  "))
                    .append(button(task.paused ? "carpet.fga.map_load.button.resume" : "carpet.fga.map_load.button.pause",
                            task.paused ? "carpet.fga.map_load.hint.resume" : "carpet.fga.map_load.hint.pause",
                            ChatFormatting.YELLOW, "/mapLoad " + (task.paused ? "resume" : "pause") + " " + name))
                    .append(Component.literal(" "))
                    .append(button("carpet.fga.map_load.button.stop", "carpet.fga.map_load.hint.stop",
                            ChatFormatting.RED, "/mapLoad stop " + name));
            lines.add(line);
            index++;
        }
        return lines;
    }

    /** Client language when the client has this mod, server language text otherwise. */
    static Component text(String key, Object... args) {
        return FGAText.text(key, args);
    }

    /** The same translation as plain text, for messages embedded into another literal. */
    static String raw(String key, Object... args) {
        String format = MESSAGES.getOrDefault(key, key);
        return args.length == 0 ? format : String.format(format, args);
    }

    /**
     * Chunks the map area covers, used both by the start message and by the progress display so the
     * two numbers always agree.
     */
    public static int chunkEstimate(MapItemSavedData data) {
        int[] bounds = mapChunkBounds(data);
        return (bounds[1] - bounds[0] + 1) * (bounds[3] - bounds[2] + 1);
    }

    /**
     * Chunk rectangle of the map area itself, using vanilla's own (center / scale) rounding like
     * sampleChunkBounds does.
     */
    private static int[] mapChunkBounds(MapItemSavedData data) {
        int scale = 1 << data.scale;
        int baseX = data.centerX / scale;
        int baseZ = data.centerZ / scale;
        return new int[]{
                ((baseX - 64) * scale) >> 4,
                (((baseX + 63) * scale) + scale - 1) >> 4,
                ((baseZ - 64) * scale) >> 4,
                (((baseZ + 63) * scale) + scale - 1) >> 4};
    }

    /**
     * How hard a load pushes the chunk pipeline, picked per command. Rendering itself always stays
     * inside the tick budget, so this only decides how much chunk generation a load asks for at a
     * time.
     */
    public enum Mode {
        /** Gentle ticket rate, keeps chunk generation close to what a walking player causes. */
        SMOOTH("smooth"),
        /** Request a whole sample window at once, trading a burst of chunk generation for speed. */
        FAST("fast"),
        /** Render the chunks that are loaded and skip the rest: fastest, never generates terrain. */
        LOADED("loaded");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** Mode names as accepted by /mapLoad start, in suggestion order. */
        public static List<String> ids() {
            return List.of(SMOOTH.id, FAST.id, LOADED.id);
        }

        /** @return null when the value is not one of the mode names */
        public static Mode parse(String value) {
            if (value == null) return null;
            String trimmed = value.trim().toLowerCase(Locale.ROOT);
            for (Mode mode : values()) {
                if (mode.id.equals(trimmed)) return mode;
            }
            return null;
        }
    }

    private static int newTicketsPerTick(Mode mode) {
        return mode == Mode.FAST ? FAST_NEW_TICKETS_PER_TICK : SMOOTH_NEW_TICKETS_PER_TICK;
    }

    private static int inFlightLimit(Mode mode) {
        return mode == Mode.FAST ? FAST_IN_FLIGHT_CHUNKS : SMOOTH_IN_FLIGHT_CHUNKS;
    }

    private static Component button(String labelKey, String hintKey, ChatFormatting color, String command) {
        return text(labelKey).copy().withStyle(Style.EMPTY
                .withColor(color)
                .withClickEvent(FgaClickEvents.runCommand(command))
                .withHoverEvent(
                        //#if MC >= 1.21.5
                        //$$ new HoverEvent.ShowText(text(hintKey))
                        //#else
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, text(hintKey))
                        //#endif
                ));
    }

    private static void addLoadTicket(ServerChunkCache cache, ChunkPos pos) {
        //#if MC >= 1.21.5
        //$$ cache.addTicketWithRadius(LOAD_TICKET, pos, TICKET_RADIUS);
        //#else
        cache.addRegionTicket(LOAD_TICKET, pos, TICKET_RADIUS, pos);
        //#endif
    }

    private static void removeLoadTicket(ServerChunkCache cache, ChunkPos pos) {
        //#if MC >= 1.21.5
        //$$ cache.removeTicketWithRadius(LOAD_TICKET, pos, TICKET_RADIUS);
        //#else
        cache.removeRegionTicket(LOAD_TICKET, pos, TICKET_RADIUS, pos);
        //#endif
    }

    private static Map<String, String> loadMessages() {
        Locale locale = Locale.getDefault();
        String file = "en_us.json";
        if ("zh".equals(locale.getLanguage())) {
            String country = locale.getCountry();
            file = "TW".equals(country) || "HK".equals(country) ? "zh_tw.json" : "zh_cn.json";
        }
        Map<String, String> messages = new HashMap<>();
        try (InputStream stream = MapLoadManager.class.getResourceAsStream(
                "/assets/carpet-fga-addition/lang/" + file)) {
            if (stream == null) return messages;
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                if (entry.getKey().startsWith(MESSAGE_PREFIX)) messages.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception exception) {
            // Nothing to do: text() then falls back to the key, which is no worse than a client side
            // translation would be for a client that does not have FGA installed.
        }
        return messages;
    }

    private static final class Task {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final MapItem mapItem;
        private final MapId mapId;
        private final MapItemSavedData data;
        private final List<int[]> samples;
        private final int mapScale;
        /** Vanilla's window radius in map pixels: it walks o in [l-n+1, l+n-1], p in [m-n-1, m+n-1]. */
        private final int windowRadius;
        private final int[] scratch = new int[4];
        /** Tickets this task holds, with the time they were taken, so a stuck chunk can be let go. */
        private final Map<Long, Long> ticketed = new HashMap<>();
        private final Mode mode;
        /** Chunk rectangle of the map area itself, the denominator of the chunk progress display. */
        private final int[] mapBounds;
        private final long startedAtNanos = System.nanoTime();
        /** Tickets this task currently holds, kept under the mode's in-flight limit. */
        private int inFlight;
        private int index;
        /** Column residue class of the sample at index that the next pass has to render. */
        private int pass;
        private int skipped;
        private int ticksSinceReport;
        private int ticksSinceChatReport;
        private int stalledTicks;
        /** Ready chunk count of the sample we are waiting on, to tell "slow" from "stuck". */
        private int lastReadyChunks;
        private int waitingTicks;
        private int mapReady;
        private int mapTotal;
        /** One flag per chunk of the map area, so the counter never goes backwards when a chunk unloads. */
        private final boolean[] mapChunksSeen;
        private boolean waiting;
        private boolean paused;

        private Task(ServerPlayer player, MapItem mapItem, MapId mapId, MapItemSavedData data, Mode mode) {
            this.player = player;
            this.level = player.serverLevel();
            this.mapItem = mapItem;
            this.mapId = mapId;
            this.data = data;
            this.mapScale = 1 << data.scale;
            this.mode = mode;
            this.mapBounds = mapChunkBounds(data);
            this.mapTotal = (mapBounds[1] - mapBounds[0] + 1) * (mapBounds[3] - mapBounds[2] + 1);
            this.mapChunksSeen = new boolean[mapTotal];
            int radius = 128 / mapScale;
            // MapItem.update halves its window in dimensions with a ceiling.
            if (level.dimensionType().hasCeiling()) radius /= 2;
            this.windowRadius = radius;
            // A sample only writes the pixels within radius-2 of its centre, plus a parity gated rim,
            // so neighbouring samples have to be closer than sqrt(2)*(radius-2) for the map to end
            // up without holes. The formula below was checked against a simulation of the vanilla
            // write condition for every map scale (0-4) with and without a ceiling: 0 pixels missed.
            // Stepping a whole radius is only safe from radius 8 up, the nether scale 4 case (radius
            // 4) needs the tighter step or it leaves a checkerboard.
            int sampleStep = Math.max(1, Math.min(radius, Math.round(1.414F * (radius - 2))));
            int halfStep = sampleStep / 2;
            List<int[]> built = new ArrayList<>();
            for (int sampleZ = halfStep; sampleZ < 128; sampleZ += sampleStep) {
                for (int sampleX = halfStep; sampleX < 128; sampleX += sampleStep) {
                    built.add(new int[]{sampleX, sampleZ});
                }
            }
            // Refresh the pixels around the player first: the far side of a big map is the part a
            // chunk limit is allowed to drop, the player sees their own surroundings first, and a
            // load that gets stopped early still leaves the interesting half done.
            double originX = (player.getX() - data.centerX) / (double) mapScale + 64.0D;
            double originZ = (player.getZ() - data.centerZ) / (double) mapScale + 64.0D;
            built.sort((first, second) -> Double.compare(
                    squaredDistance(first, originX, originZ), squaredDistance(second, originX, originZ)));
            this.samples = built;
        }

        private static double squaredDistance(int[] sample, double x, double z) {
            double dx = sample[0] - x;
            double dz = sample[1] - z;
            return dx * dx + dz * dz;
        }

        private String playerName() {
            return player.getGameProfile().getName();
        }

        private int percent() {
            return (int) (((long) index * UPDATE_PASSES + pass) * 100L / ((long) samples.size() * UPDATE_PASSES));
        }

        private String statusText() {
            if (paused) return raw("carpet.fga.map_load.status.paused");
            return raw(waiting ? "carpet.fga.map_load.status.waiting" : "carpet.fga.map_load.status.loading");
        }

        private boolean valid() {
            return !player.isRemoved() && player.serverLevel() == level;
        }

        /** @return true when the task finished or was cancelled */
        private boolean tick(long share) {
            if (!valid()) {
                startDraining(this);
                if (!player.isRemoved()) {
                    FGACompat.displayClientMessage(player, text("carpet.fga.map_load.cancelled"), true);
                }
                return true;
            }
            if (paused) return false;

            long start = System.nanoTime();
            long deadline = start + share;
            // A task whose share was already used up by the earlier tasks still gets one pass done,
            // so no task can be starved, but the overrun stays bounded by a single pass.
            long guarantee = start + 2L * share;
            boolean blocked = false;
            int passes = 0;
            try {
                while (index < samples.size() && passes < MAX_PASSES_PER_TICK) {
                    int[] sample = samples.get(index);
                    sampleChunkBounds(sample, scratch);
                    int readyChunks = countReadyChunks(scratch);
                    int windowChunks = chunkCount(scratch);
                    if (readyChunks < windowChunks) {
                        if (mode == Mode.LOADED) {
                            // This mode never requests anything, so waiting for the rest is pointless.
                            skipped++;
                            index++;
                            pass = 0;
                            continue;
                        }
                        blocked = true;
                        if (readyChunks > lastReadyChunks) {
                            // Chunks are still arriving for this sample; it is slow, not stuck.
                            lastReadyChunks = readyChunks;
                            stalledTicks = 0;
                        }
                        break;
                    }
                    if (System.nanoTime() >= (passes > 0 ? deadline : guarantee)) break;
                    renderPass(sample);
                    passes++;
                    if (++pass >= UPDATE_PASSES) {
                        pass = 0;
                        index++;
                    }
                }
            } catch (RuntimeException exception) {
                player.sendSystemMessage(text("carpet.fga.map_load.failed",
                        exception.getMessage() == null ? exception.toString() : exception.getMessage()));
                startDraining(this);
                return true;
            }

            if (blocked) {
                if (++stalledTicks >= STALL_SKIP_TICKS) {
                    stalledTicks = 0;
                    skipped++;
                    index++;
                    pass = 0;
                }
            } else {
                stalledTicks = 0;
            }
            try {
                report(blocked);
                if (index < samples.size()) return false;
                finish();
            } catch (RuntimeException exception) {
                // Never let message building (or anything else here) escape into the server tick loop.
                player.sendSystemMessage(Component.literal("map load failed: " + exception));
                startDraining(this);
            }
            return true;
        }

        /**
         * Hands the chunks of the next samples to the vanilla chunk pipeline and drops the tickets
         * the cursor has moved past. Rendering never depends on a ticket, only on the chunk being
         * present, so a chunk released too early costs one re-request at worst.
         */
        private void prefetch() {
            if (paused || mode == Mode.LOADED || index >= samples.size() || !valid()) return;
            int lookaheadEnd = Math.min(samples.size(), index + PREFETCH_LOOKAHEAD_SAMPLES);
            // Release against the exact chunk set the next samples need. A bounding box is not good
            // enough: samples are ordered by distance, so a box around a handful of them can span the
            // whole map, which would keep every ticket ever taken, fill the in-flight cap and leave
            // the cursor's own chunks unticketed forever.
            Set<Long> needed = new HashSet<>();
            for (int i = index; i < lookaheadEnd; i++) {
                sampleChunkBounds(samples.get(i), scratch);
                for (int chunkX = scratch[0]; chunkX <= scratch[1]; chunkX++) {
                    for (int chunkZ = scratch[2]; chunkZ <= scratch[3]; chunkZ++) {
                        needed.add(FGACompat.chunkKey(chunkX, chunkZ));
                    }
                }
            }

            ServerChunkCache cache = level.getChunkSource();
            releaseUnneededTickets(needed, RELEASE_PER_TICK);

            int budget = Math.min(newTicketsPerTick(mode), inFlightLimit(mode) - inFlight);
            if (budget <= 0) return;
            // Nearest samples first, so the cursor's own chunks always win when the budget runs out.
            for (int i = index; i < lookaheadEnd && budget > 0; i++) {
                sampleChunkBounds(samples.get(i), scratch);
                for (int chunkX = scratch[0]; chunkX <= scratch[1] && budget > 0; chunkX++) {
                    for (int chunkZ = scratch[2]; chunkZ <= scratch[3] && budget > 0; chunkZ++) {
                        if (cache.getChunkNow(chunkX, chunkZ) != null) continue;
                        long key = FGACompat.chunkKey(chunkX, chunkZ);
                        if (ticketed.containsKey(key)) continue;
                        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                        addLoadTicket(cache, chunkPos);
                        ticketed.put(key, System.nanoTime());
                        inFlight++;
                        budget--;
                    }
                }
            }
        }

        /**
         * Chunk rectangle the vanilla render pass can touch for this sample. The pixel range is
         * clipped the way MapItem.update clips it, and the block coordinates go through vanilla's
         * own (center / scale) rounding so the rectangle cannot miss a chunk it will read.
         */
        private void sampleChunkBounds(int[] sample, int[] out) {
            int pixelMinX = Math.max(0, sample[0] - windowRadius + 1 - WINDOW_MARGIN);
            int pixelMaxX = Math.min(127, sample[0] + windowRadius - 1 + WINDOW_MARGIN);
            int pixelMinZ = Math.max(-1, sample[1] - windowRadius - 1 - WINDOW_MARGIN);
            int pixelMaxZ = Math.min(127, sample[1] + windowRadius - 1 + WINDOW_MARGIN);
            int baseX = data.centerX / mapScale;
            int baseZ = data.centerZ / mapScale;
            out[0] = ((baseX + pixelMinX - 64) * mapScale) >> 4;
            out[1] = (((baseX + pixelMaxX - 64) * mapScale) + mapScale - 1) >> 4;
            out[2] = ((baseZ + pixelMinZ - 64) * mapScale) >> 4;
            out[3] = (((baseZ + pixelMaxZ - 64) * mapScale) + mapScale - 1) >> 4;
        }

        /**
         * How many chunks of the map area have been loaded so far. Cumulative on purpose: a chunk we
         * already counted is not taken back when it unloads, otherwise the counter would jump back and
         * forth while the load is running.
         */
        private void countMapChunks() {
            ServerChunkCache cache = level.getChunkSource();
            int width = mapBounds[1] - mapBounds[0] + 1;
            for (int chunkX = mapBounds[0]; chunkX <= mapBounds[1]; chunkX++) {
                for (int chunkZ = mapBounds[2]; chunkZ <= mapBounds[3]; chunkZ++) {
                    int index = (chunkX - mapBounds[0]) + (chunkZ - mapBounds[2]) * width;
                    if (mapChunksSeen[index]) continue;
                    if (cache.getChunkNow(chunkX, chunkZ) == null) continue;
                    mapChunksSeen[index] = true;
                    mapReady++;
                }
            }
        }

        /** Chunks of this rectangle that are already at FULL, ready to be rendered. */
        private int countReadyChunks(int[] bounds) {
            ServerChunkCache cache = level.getChunkSource();
            int ready = 0;
            for (int chunkX = bounds[0]; chunkX <= bounds[1]; chunkX++) {
                for (int chunkZ = bounds[2]; chunkZ <= bounds[3]; chunkZ++) {
                    if (cache.getChunkNow(chunkX, chunkZ) != null) ready++;
                }
            }
            return ready;
        }

        private static int chunkCount(int[] bounds) {
            return (bounds[1] - bounds[0] + 1) * (bounds[3] - bounds[2] + 1);
        }

        /**
         * Renders one column residue class of the sample window. MapItem.update reads the position
         * off the entity, so the player has to stand on the sample for the duration of the call and
         * be put back afterwards. The player's own held map also advances the step every tick, so
         * pin it to the class this pass owns instead of relying on consecutive calls.
         */
        private void renderPass(int[] sample) {
            data.getHoldingPlayer(player).step = pass - 1;
            double restoreX = player.getX();
            double restoreY = player.getY();
            double restoreZ = player.getZ();
            double worldX = data.centerX + (sample[0] - 64) * (double) mapScale + 0.5D;
            double worldZ = data.centerZ + (sample[1] - 64) * (double) mapScale + 0.5D;
            try {
                player.setPosRaw(worldX, restoreY, worldZ);
                mapItem.update(level, player, data);
            } finally {
                player.setPosRaw(restoreX, restoreY, restoreZ);
            }
        }

        private void report(boolean blocked) {
            waiting = blocked;
            int percent = percent();
            // Skip the first tick: the command already reported the start in chat.
            if (ticksSinceChatReport > 0 && ticksSinceChatReport % CHAT_REPORT_INTERVAL_TICKS == 0) {
                countMapChunks();
                player.sendSystemMessage(text("carpet.fga.map_load.progress_chat", percent, mode.id(),
                        mapReady, mapTotal));
            }
            ticksSinceChatReport++;
            if (blocked) {
                // The chunk counter keeps moving while the percentage does not, which is the whole
                // point of showing it: a big map spends most of its time waiting for generation.
                if (waitingTicks % WAITING_REPORT_INTERVAL_TICKS == 0) {
                    countMapChunks();
                    FGACompat.displayClientMessage(player, text("carpet.fga.map_load.progress", percent, mode.id(),
                            mapReady, mapTotal), true);
                }
                waitingTicks++;
                return;
            }

            waitingTicks = 0;
            if (ticksSinceReport++ % PROGRESS_REPORT_INTERVAL_TICKS != 0) return;
            countMapChunks();
            FGACompat.displayClientMessage(player, text("carpet.fga.map_load.progress", percent, mode.id(), mapReady, mapTotal), true);
        }

        private void finish() {
            startDraining(this);
            Packet<?> packet = data.getUpdatePacket(mapId, player);
            if (packet != null) player.connection.send(packet);
            long seconds = Math.max(1L, (System.nanoTime() - startedAtNanos) / 1_000_000_000L);
            player.sendSystemMessage(seconds < 60L
                    ? text("carpet.fga.map_load.finished", seconds)
                    : text("carpet.fga.map_load.finished_minutes", seconds / 60L, seconds % 60L));
            if (skipped > 0) {
                player.sendSystemMessage(text("carpet.fga.map_load.skipped_unloaded", skipped));
            }
            countMapChunks();
            FGACompat.displayClientMessage(player, text("carpet.fga.map_load.progress", 100, mode.id(), mapReady, mapTotal), true);
        }

        /**
         * Hands back at most {@code limit} tickets for chunks that are not in {@code needed} (all of
         * them when it is null). A chunk that has not reached FULL yet keeps its ticket: taking it away
         * mid generation can leave the chunk stuck in the unload queue forever.
         *
         * @return true when no ticket is left to hand back
         */
        private boolean releaseUnneededTickets(Set<Long> needed, int limit) {
            if (ticketed.isEmpty()) return true;
            ServerChunkCache cache = level.getChunkSource();
            int released = 0;
            long now = System.nanoTime();
            Iterator<Map.Entry<Long, Long>> iterator = ticketed.entrySet().iterator();
            while (iterator.hasNext() && released < limit) {
                Map.Entry<Long, Long> entry = iterator.next();
                long key = entry.getKey();
                if (needed != null && needed.contains(key)) continue;
                int chunkX = ChunkPos.getX(key);
                int chunkZ = ChunkPos.getZ(key);
                boolean loaded = cache.getChunkNow(chunkX, chunkZ) != null;
                if (!loaded && now - entry.getValue() < TICKET_GIVE_UP_NANOS) continue;
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                removeLoadTicket(cache, chunkPos);
                iterator.remove();
                inFlight--;
                released++;
            }
            return ticketed.isEmpty();
        }
    }
}
//#endif
