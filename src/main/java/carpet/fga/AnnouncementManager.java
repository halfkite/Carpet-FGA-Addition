//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sends join announcements and detects entry into configured cuboid regions.
 * Smoke flow: enable serverAnnouncements; create a permanent notice using /n,
 * verify automatic ID assignment, publisher and minute timestamp, then verify
 * the text is sent to a real player on join but never to a Carpet fake player.
 * Set 1h and verify the expiry is displayed and then skipped after expiry;
 * test enable/disable, hide/show, content edit, header edit and file reload.
 * Configure a region trigger spanning a test platform in a disposable world,
 * verify one send on entry, no repeats while standing, no send in another
 * dimension, and another send after leaving and re-entering. Check restart
 * persistence, malformed JSON preservation and that rule-off suppresses all
 * messages. Do not use a production world for these checks.
 */
public final class AnnouncementManager {
    private static final DateTimeFormatter PUBLISHED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final Map<UUID, Set<Integer>> INSIDE_REGIONS = new HashMap<>();
    private static volatile AnnouncementConfig.State cachedState;
    private static volatile List<AnnouncementConfig.Announcement> cachedRegions = List.of();

    private AnnouncementManager() {
    }

    public static void onJoin(ServerPlayer player) {
        if (!isRealPlayer(player) || !FGASettings.serverAnnouncements || AnnouncementConfig.isLoadFailed()) return;
        activeRegions(System.currentTimeMillis());
        Set<Integer> inside = new HashSet<>();
        for (AnnouncementConfig.Announcement announcement : activeAnnouncements()) {
            AnnouncementConfig.Trigger trigger = announcement.trigger();
            if (trigger == null) {
                send(player, announcement);
            } else if (contains(player, trigger)) {
                inside.add(announcement.id());
                send(player, announcement);
            }
        }
        INSIDE_REGIONS.put(player.getUUID(), inside);
    }

    public static void tick(MinecraftServer server) {
        if (!FGASettings.serverAnnouncements || AnnouncementConfig.isLoadFailed()) {
            INSIDE_REGIONS.clear();
            return;
        }

        List<AnnouncementConfig.Announcement> regions = activeRegions(System.currentTimeMillis());
        if (regions.isEmpty()) {
            INSIDE_REGIONS.clear();
            return;
        }

        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isRealPlayer(player)) continue;
            UUID playerId = player.getUUID();
            online.add(playerId);
            Set<Integer> previous = INSIDE_REGIONS.getOrDefault(playerId, Set.of());
            Set<Integer> current = new HashSet<>();
            for (AnnouncementConfig.Announcement announcement : regions) {
                if (!contains(player, announcement.trigger())) continue;
                current.add(announcement.id());
                if (!previous.contains(announcement.id())) send(player, announcement);
            }
            INSIDE_REGIONS.put(playerId, current);
        }

        Iterator<UUID> iterator = INSIDE_REGIONS.keySet().iterator();
        while (iterator.hasNext()) {
            if (!online.contains(iterator.next())) iterator.remove();
        }
    }

    static boolean isActive(AnnouncementConfig.Announcement announcement, long nowMillis) {
        return announcement.enabled() && !announcement.hidden()
                && (announcement.expiresAtMillis() == null || nowMillis < announcement.expiresAtMillis());
    }

    static String render(AnnouncementConfig.State state, AnnouncementConfig.Announcement announcement) {
        return state.header() + "\n"
                + PUBLISHED_AT.format(Instant.ofEpochMilli(announcement.publishedAtMillis())) + "\n"
                + "发布者：" + announcement.publisher() + "\n"
                + announcement.content().replace("/n", "\n").replace("\\n", "\n");
    }

    private static List<AnnouncementConfig.Announcement> activeAnnouncements() {
        long now = System.currentTimeMillis();
        List<AnnouncementConfig.Announcement> result = new ArrayList<>();
        for (AnnouncementConfig.Announcement announcement : AnnouncementConfig.snapshot().announcements().values()) {
            if (isActive(announcement, now)) result.add(announcement);
        }
        return result;
    }

    private static List<AnnouncementConfig.Announcement> activeRegions(long now) {
        AnnouncementConfig.State state = AnnouncementConfig.snapshot();
        if (cachedState != state) {
            synchronized (AnnouncementManager.class) {
                if (cachedState != state) {
                    List<AnnouncementConfig.Announcement> next = new ArrayList<>();
                    for (AnnouncementConfig.Announcement announcement : state.announcements().values()) {
                        if (announcement.trigger() != null) next.add(announcement);
                    }
                    cachedRegions = List.copyOf(next);
                    cachedState = state;
                    INSIDE_REGIONS.clear();
                }
            }
        }
        List<AnnouncementConfig.Announcement> result = new ArrayList<>();
        for (AnnouncementConfig.Announcement announcement : cachedRegions) {
            if (isActive(announcement, now)) result.add(announcement);
        }
        return result;
    }

    private static boolean isRealPlayer(ServerPlayer player) {
        return !(player instanceof carpet.patches.EntityPlayerMPFake);
    }

    private static boolean contains(ServerPlayer player, AnnouncementConfig.Trigger trigger) {
        if (!player.level().dimension().location().toString().equals(trigger.dimension())) return false;
        var position = player.blockPosition();
        return position.getX() >= trigger.minX() && position.getX() <= trigger.maxX()
                && position.getY() >= trigger.minY() && position.getY() <= trigger.maxY()
                && position.getZ() >= trigger.minZ() && position.getZ() <= trigger.maxZ();
    }

    private static void send(ServerPlayer player, AnnouncementConfig.Announcement announcement) {
        player.sendSystemMessage(Component.literal(render(AnnouncementConfig.snapshot(), announcement)));
    }
}
//#endif
