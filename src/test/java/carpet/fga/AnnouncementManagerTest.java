//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnnouncementManagerTest {
    @Test
    void durationParserAcceptsHoursDaysAndPermanent() {
        assertEquals(12L * 60 * 60 * 1000, AnnouncementConfig.parseDurationMillis("12h"));
        assertEquals(3L * 24 * 60 * 60 * 1000, AnnouncementConfig.parseDurationMillis("3d"));
        assertThrows(IllegalArgumentException.class, () -> AnnouncementConfig.parseDurationMillis("0h"));
        assertThrows(IllegalArgumentException.class, () -> AnnouncementConfig.parseDurationMillis("12m"));
        assertThrows(IllegalArgumentException.class, () -> AnnouncementConfig.parseDurationMillis("3651d"));
    }

    @Test
    void regionBoundsAreNormalizedAndDimensionIsNamespaced() {
        var trigger = AnnouncementConfig.Trigger.region("minecraft:the_nether", 8, 4, 9, -2, -3, -5);
        assertEquals("minecraft:the_nether", trigger.dimension());
        assertEquals(-2, trigger.minX());
        assertEquals(-3, trigger.minY());
        assertEquals(-5, trigger.minZ());
        assertEquals(8, trigger.maxX());
        assertEquals(4, trigger.maxY());
        assertEquals(9, trigger.maxZ());
        assertThrows(IllegalArgumentException.class,
                () -> AnnouncementConfig.Trigger.region("Bad Dimension", 0, 0, 0, 1, 1, 1));
    }

    @Test
    void announcementBlockHasHeaderMinutePublisherAndSlashNLineBreaks() {
        var state = new AnnouncementConfig.State("服务器公告如下", Map.of());
        var announcement = new AnnouncementConfig.Announcement(7, "第一行/n第二行", 1790447100000L,
                "Admin", null, true, false, null);
        String expectedTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(announcement.publishedAtMillis()));
        assertEquals("服务器公告如下\n" + expectedTime + "\n发布者：Admin\n第一行\n第二行",
                AnnouncementManager.render(state, announcement));
    }

    @Test
    void onlyEnabledVisibleAndUnexpiredAnnouncementsAreActive() {
        long now = 1000;
        var active = new AnnouncementConfig.Announcement(1, "x", 0, "Admin", 1001L, true, false, null);
        var disabled = new AnnouncementConfig.Announcement(2, "x", 0, "Admin", null, false, false, null);
        var hidden = new AnnouncementConfig.Announcement(3, "x", 0, "Admin", null, true, true, null);
        var expired = new AnnouncementConfig.Announcement(4, "x", 0, "Admin", 1000L, true, false, null);
        assertTrue(AnnouncementManager.isActive(active, now));
        assertFalse(AnnouncementManager.isActive(disabled, now));
        assertFalse(AnnouncementManager.isActive(hidden, now));
        assertFalse(AnnouncementManager.isActive(expired, now));
    }
}
//#endif
