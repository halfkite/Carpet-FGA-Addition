//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;

/** /fga joinNotice configuration and preview. */
public final class JoinNoticeCommand {
    private JoinNoticeCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .executes(JoinNoticeCommand::status)
                .then(Commands.literal("help").executes(JoinNoticeCommand::help))
                .then(Commands.literal("status").executes(JoinNoticeCommand::status))
                .then(Commands.literal("preview").executes(JoinNoticeCommand::preview))
                .then(Commands.literal("welcome").requires(JoinNoticeCommand::canEdit)
                        .then(Commands.literal("set")
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(JoinNoticeCommand::setWelcome)))
                        .then(Commands.literal("clear").executes(context ->
                                mutate(context, () -> JoinNoticeConfig.setWelcome(""), "欢迎语已清空"))))
                .then(Commands.literal("date").requires(JoinNoticeCommand::canEdit)
                        .then(Commands.literal("set")
                                .then(Commands.argument("date", StringArgumentType.word())
                                        .suggests(JoinNoticeCommand::suggestDates)
                                        .executes(JoinNoticeCommand::setDate)))
                        .then(Commands.literal("enabled")
                                .then(Commands.literal("true").executes(context ->
                                        mutate(context, () -> JoinNoticeConfig.setDateEnabled(true), "开服日期提示已开启")))
                                .then(Commands.literal("false").executes(context ->
                                        mutate(context, () -> JoinNoticeConfig.setDateEnabled(false), "开服日期提示已关闭"))))
                        .then(Commands.literal("clear").executes(context ->
                                mutate(context, JoinNoticeConfig::clearServerDate, "开服日期已清除"))));
    }

    private static boolean canEdit(CommandSourceStack source) {
        return FGACompat.hasPermission(source, 2);
    }

    private static CompletableFuture<Suggestions> suggestDates(CommandContext<CommandSourceStack> context,
                                                                 SuggestionsBuilder builder) {
        builder.suggest(LocalDate.now().toString());
        LocalDate configured = JoinNoticeConfig.snapshot().serverDate();
        if (configured != null) builder.suggest(configured.toString());
        return builder.buildFuture();
    }

    private static int setWelcome(CommandContext<CommandSourceStack> context) {
        String text = StringArgumentType.getString(context, "text");
        return mutate(context, () -> JoinNoticeConfig.setWelcome(text), "欢迎语已更新");
    }

    private static int setDate(CommandContext<CommandSourceStack> context) {
        String text = StringArgumentType.getString(context, "date");
        try {
            LocalDate date = LocalDate.parse(text);
            return mutate(context, () -> JoinNoticeConfig.setServerDate(date), "开服日期已设为 " + date);
        } catch (DateTimeParseException exception) {
            context.getSource().sendFailure(Component.literal("日期格式应为 YYYY-MM-DD，例如 2026-09-26"));
            return 0;
        }
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        JoinNoticeConfig.State config = JoinNoticeConfig.snapshot();
        String date = config.serverDate() == null ? "未设置" : config.serverDate().toString();
        String welcome = config.welcome().isEmpty() ? "未设置" : config.welcome();
        FGACompat.sendSuccess(context.getSource(), Component.literal(
                "进服提示：" + (FGASettings.customJoinNotice ? "开启" : "关闭")
                        + "\n欢迎语：" + welcome
                        + "\n开服日期：" + date
                        + "\n日期提示：" + (config.dateEnabled() ? "开启" : "关闭")
                        + (JoinNoticeConfig.isLoadFailed() ? "\n配置文件损坏，请查看服务端日志" : "")), false);
        return 1;
    }

    private static int preview(CommandContext<CommandSourceStack> context) {
        if (JoinNoticeConfig.isLoadFailed()) {
            context.getSource().sendFailure(Component.literal("进服提示配置文件损坏，请查看服务端日志"));
            return 0;
        }
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("预览需要玩家执行 / Preview requires a player"));
            return 0;
        }
        JoinNoticeManager.sendPreview(player);
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        MutableComponent message = Component.literal("进服提示帮助：点击命令可填入聊天栏\n");
        appendCommand(message, "/carpet customJoinNotice true", "开启总开关");
        appendCommand(message, "/carpet customJoinNotice false", "关闭总开关");
        appendCommand(message, "/fga joinNotice status", "查看当前配置");
        appendCommand(message, "/fga joinNotice preview", "预览自己将看到的提示");
        appendCommand(message, "/fga joinNotice welcome set ", "设置欢迎语：{player}、&#RRGGBB、&r、\\n");
        appendCommand(message, "/fga joinNotice welcome clear", "清空欢迎语");
        appendCommand(message, "/fga joinNotice date set ", "设置开服日期，日期可按 Tab 补全");
        appendCommand(message, "/fga joinNotice date enabled true", "开启日期提示");
        appendCommand(message, "/fga joinNotice date enabled false", "关闭日期提示");
        appendCommand(message, "/fga joinNotice date clear", "清除日期并关闭日期提示");
        FGACompat.sendSuccess(context.getSource(), message, false);
        return 1;
    }

    private static void appendCommand(MutableComponent out, String command, String description) {
        out.append(Component.literal(command).withStyle(style -> style.withClickEvent(FgaClickEvents.suggestCommand(command))))
                .append(Component.literal("  — " + description + "\n"));
    }

    private static int mutate(CommandContext<CommandSourceStack> context, Edit action, String success) {
        try {
            action.run();
            FGACompat.sendSuccess(context.getSource(), Component.literal(success), false);
            return 1;
        } catch (IOException | IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    @FunctionalInterface
    private interface Edit {
        void run() throws IOException;
    }
}
//#endif
