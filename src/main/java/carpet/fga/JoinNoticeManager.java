//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders and sends the configured notice only to the player joining.
 *
 * Smoke test: enable the rule, set a welcome containing a player placeholder,
 * RGB color and newline, configure and enable the opening date, then join with a
 * real client. Verify the displayed messages, join a second time and verify
 * persisted values, disable the rule and verify silence. Check that Carpet fake
 * players receive no join notice. Use a disposable test world for malformed JSON.
 */
public final class JoinNoticeManager {
    private static final Pattern COLOR = Pattern.compile("(?i)&#([0-9a-f]{6})|&r");

    private JoinNoticeManager() {
    }

    public static void onJoin(ServerPlayer player) {
        if (!FGASettings.customJoinNotice || JoinNoticeConfig.isLoadFailed()
                || player instanceof carpet.patches.EntityPlayerMPFake) return;
        sendPreview(player);
    }

    public static void sendPreview(ServerPlayer player) {
        JoinNoticeConfig.State config = JoinNoticeConfig.snapshot();
        if (!config.welcome().isEmpty()) {
            player.sendSystemMessage(render(config.welcome(), player.getName().getString()));
        }
        if (config.dateEnabled() && config.serverDate() != null) {
            player.sendSystemMessage(Component.literal(dateLine(config.serverDate(), LocalDate.now(ZoneId.systemDefault()))));
        }
    }

    static String dateLine(LocalDate serverDate, LocalDate today) {
        long days = Math.max(0, ChronoUnit.DAYS.between(serverDate, today));
        return "开服日期：" + serverDate + " 已开服：" + days + " 天";
    }

    static Component render(String template, String playerName) {
        String value = template.replace("\\n", "\n").replace("{player}", playerName);
        MutableComponent result = Component.empty();
        Matcher matcher = COLOR.matcher(value);
        int start = 0;
        TextColor color = null;
        while (matcher.find()) {
            append(result, value.substring(start, matcher.start()), color);
            color = matcher.group(1) == null ? null : TextColor.fromRgb(Integer.parseInt(matcher.group(1), 16));
            start = matcher.end();
        }
        append(result, value.substring(start), color);
        return result;
    }

    private static void append(MutableComponent result, String text, TextColor color) {
        if (text.isEmpty()) return;
        MutableComponent part = Component.literal(text);
        if (color != null) part.withStyle(style -> style.withColor(color));
        result.append(part);
    }
}
//#endif
