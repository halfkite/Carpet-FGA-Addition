//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JoinNoticeManagerTest {
    @Test
    void openingDayIsDayZeroAndElapsedDaysUseCalendarDates() {
        LocalDate today = LocalDate.of(2026, 9, 26);
        assertEquals("开服日期：2026-09-26 已开服：0 天", JoinNoticeManager.dateLine(today, today));
        assertEquals("开服日期：2026-05-21 已开服：128 天",
                JoinNoticeManager.dateLine(today.minusDays(128), today));
        assertEquals("开服日期：2026-09-27 已开服：0 天",
                JoinNoticeManager.dateLine(today.plusDays(1), today));
    }

    @Test
    void welcomeExpandsPlayerLineBreakAndColorCodes() {
        var message = JoinNoticeManager.render("&#12ABef欢迎 {player}&r\\n再见", "Alex");
        assertEquals("欢迎 Alex\n再见", message.getString());
        assertEquals("#12ABEF", message.getSiblings().get(0).getStyle().getColor().toString());
    }
}
//#endif
