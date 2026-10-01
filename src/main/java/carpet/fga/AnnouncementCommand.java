//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * Interactive per-world server announcement management.
 * Client smoke: click every /fga help command and confirm it is inserted into
 * chat without execution; click one list row and confirm its detail opens;
 * verify the detail's delete/toggle buttons act once and the edit buttons only
 * fill chat. Press Tab after /fga joinNotice date set and confirm the current
 * and stored ISO dates are offered. Perform destructive button checks only in
 * a disposable test world.
 */
public final class AnnouncementCommand {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private AnnouncementCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .executes(AnnouncementCommand::list)
                .then(Commands.literal("help").executes(AnnouncementCommand::help))
                .then(Commands.literal("status").executes(AnnouncementCommand::status))
                .then(Commands.literal("list").executes(AnnouncementCommand::list))
                .then(Commands.literal("info").then(idArgument("id").executes(AnnouncementCommand::info)))
                .then(Commands.literal("reload").requires(AnnouncementCommand::canEdit)
                        .executes(AnnouncementCommand::reload))
                .then(Commands.literal("header").requires(AnnouncementCommand::canEdit)
                        .then(Commands.literal("set").then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(AnnouncementCommand::setHeader)))
                        .then(Commands.literal("clear").executes(AnnouncementCommand::clearHeader)))
                .then(Commands.literal("create").requires(AnnouncementCommand::canEdit)
                        .then(Commands.argument("content", StringArgumentType.greedyString())
                                .executes(AnnouncementCommand::createAuto))
                        .then(Commands.literal("id").then(idArgument("id")
                                .then(Commands.argument("content", StringArgumentType.greedyString())
                                        .executes(AnnouncementCommand::createWithId)))))
                .then(Commands.literal("delete").requires(AnnouncementCommand::canEdit)
                        .then(idArgument("id").executes(AnnouncementCommand::delete)))
                .then(Commands.literal("enable").requires(AnnouncementCommand::canEdit)
                        .then(idArgument("id").executes(context -> setEnabled(context, true))))
                .then(Commands.literal("disable").requires(AnnouncementCommand::canEdit)
                        .then(idArgument("id").executes(context -> setEnabled(context, false))))
                .then(Commands.literal("hide").requires(AnnouncementCommand::canEdit)
                        .then(idArgument("id").executes(context -> setHidden(context, true))))
                .then(Commands.literal("show").requires(AnnouncementCommand::canEdit)
                        .then(idArgument("id").executes(context -> setHidden(context, false))))
                .then(Commands.literal("content").requires(AnnouncementCommand::canEdit)
                        .then(Commands.literal("set").then(idArgument("id")
                                .then(Commands.argument("content", StringArgumentType.greedyString())
                                        .executes(AnnouncementCommand::setContent)))))
                .then(Commands.literal("expiry").requires(AnnouncementCommand::canEdit)
                        .then(Commands.literal("set").then(idArgument("id")
                                .then(Commands.argument("duration", StringArgumentType.word())
                                        .suggests(AnnouncementCommand::suggestDurations)
                                        .executes(AnnouncementCommand::setExpiry)))))
                .then(Commands.literal("trigger").requires(AnnouncementCommand::canEdit)
                        .then(Commands.literal("join").then(idArgument("id").executes(AnnouncementCommand::setJoinTrigger)))
                        .then(Commands.literal("region").then(idArgument("id")
                                .then(Commands.argument("dimension", ResourceLocationArgument.id())
                                        .suggests(AnnouncementCommand::suggestDimensions)
                                        .then(integerArgument("x1").then(integerArgument("y1").then(integerArgument("z1")
                                                .then(integerArgument("x2").then(integerArgument("y2").then(integerArgument("z2")
                                                        .executes(AnnouncementCommand::setRegionTrigger)))))))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> idArgument(String name) {
        return Commands.argument(name, IntegerArgumentType.integer(1)).suggests(AnnouncementCommand::suggestIds);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> integerArgument(String name) {
        return Commands.argument(name, IntegerArgumentType.integer());
    }

    private static boolean canEdit(CommandSourceStack source) {
        return FGACompat.hasPermission(source, 2);
    }

    private static CompletableFuture<Suggestions> suggestIds(CommandContext<CommandSourceStack> context,
                                                               SuggestionsBuilder builder) {
        AnnouncementConfig.snapshot().announcements().keySet().forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestDurations(CommandContext<CommandSourceStack> context,
                                                                     SuggestionsBuilder builder) {
        builder.suggest("forever").suggest("1h").suggest("12h").suggest("1d").suggest("7d");
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestDimensions(CommandContext<CommandSourceStack> context,
                                                                      SuggestionsBuilder builder) {
        context.getSource().getServer().getAllLevels().forEach(level ->
                builder.suggest(level.dimension().location().toString()));
        return builder.buildFuture();
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        MutableComponent out = Component.literal("服务器公告帮助：点击命令可填入聊天栏；公告仅服务端发送\n");
        add(out, "/carpet serverAnnouncements true", "开启公告触发");
        add(out, "/carpet serverAnnouncements false", "关闭公告触发");
        add(out, "/fga announcement status", "查看规则、题头和配置状态");
        add(out, "/fga announcement list", "逐行查看公告；点击编号查看详情");
        add(out, "/fga announcement create ", "新建永久公告并自动编号（内容用 /n 换行）");
        add(out, "/fga announcement create id ", "新建公告并指定编号");
        add(out, "/fga announcement header set ", "自定义题头，默认“服务器公告如下”");
        add(out, "/fga announcement header clear", "恢复默认题头");
        add(out, "/fga announcement expiry set ", "编号后输入 forever、数字h 或 数字d");
        add(out, "/fga announcement trigger join ", "设置为玩家进服时触发");
        add(out, "/fga announcement trigger region ", "设置为进入维度坐标长方体范围时触发");
        add(out, "/fga announcement reload", "重新读取存档公告 JSON 配置");
        FGACompat.sendSuccess(context.getSource(), out, false);
        return 1;
    }

    private static void add(MutableComponent out, String command, String description) {
        out.append(Component.literal(command).withStyle(style -> style.withClickEvent(FgaClickEvents.suggestCommand(command))))
                .append(Component.literal("  — " + description + "\n"));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        String state = AnnouncementConfig.isLoadFailed() ? "配置损坏，写入已锁定"
                : "已加载 " + AnnouncementConfig.snapshot().announcements().size() + " 条公告";
        FGACompat.sendSuccess(context.getSource(), Component.literal("公告总开关："
                + (FGASettings.serverAnnouncements ? "开启" : "关闭") + "\n题头："
                + AnnouncementConfig.snapshot().header() + "\n" + state), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        AnnouncementConfig.State state = AnnouncementConfig.snapshot();
        MutableComponent out = Component.literal("服务器公告（" + state.announcements().size() + "）\n")
                .withStyle(ChatFormatting.GOLD);
        if (AnnouncementConfig.isLoadFailed()) {
            out.append(Component.literal("配置文件损坏，当前展示为空且拒绝覆盖\n").withStyle(ChatFormatting.RED));
        }
        long now = System.currentTimeMillis();
        for (AnnouncementConfig.Announcement announcement : state.announcements().values()) {
            String preview = announcement.content().replace("/n", " ").replace("\\n", " ");
            if (preview.length() > 72) preview = preview.substring(0, 69) + "...";
            String status = statusLabel(announcement, now);
            String command = "/fga announcement info " + announcement.id();
            out.append(Component.literal("#" + announcement.id() + " [" + status + "] " + preview + "\n")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withClickEvent(FgaClickEvents.runCommand(command))));
        }
        FGACompat.sendSuccess(context.getSource(), out, false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        int id = IntegerArgumentType.getInteger(context, "id");
        AnnouncementConfig.Announcement announcement = AnnouncementConfig.snapshot().announcements().get(id);
        if (announcement == null) return fail(context, "找不到公告编号：" + id);
        AnnouncementConfig.State state = AnnouncementConfig.snapshot();
        long now = System.currentTimeMillis();
        MutableComponent out = Component.literal("公告 #" + id + "\n")
                .append(Component.literal("状态：" + statusLabel(announcement, now) + "\n"))
                .append(Component.literal("发布时间：" + DATE_TIME.format(Instant.ofEpochMilli(announcement.publishedAtMillis())) + "\n"))
                .append(Component.literal("发布者：" + announcement.publisher() + "\n"))
                .append(Component.literal("时效：" + expiryLabel(announcement, now) + "\n"))
                .append(Component.literal("触发：" + triggerLabel(announcement) + "\n"))
                .append(Component.literal("内容：\n" + announcement.content().replace("/n", "\n").replace("\\n", "\n") + "\n"));
        if (canEdit(context.getSource())) {
            button(out, "[删除]", "/fga announcement delete " + id, true, ChatFormatting.RED);
            button(out, announcement.enabled() ? "[停用]" : "[启用]",
                    "/fga announcement " + (announcement.enabled() ? "disable " : "enable ") + id,
                    true, ChatFormatting.GOLD);
            button(out, announcement.hidden() ? "[取消隐藏]" : "[隐藏]",
                    "/fga announcement " + (announcement.hidden() ? "show " : "hide ") + id,
                    true, ChatFormatting.YELLOW);
            button(out, "[更改内容]", "/fga announcement content set " + id + " ", false, ChatFormatting.AQUA);
            button(out, "[更改时效]", "/fga announcement expiry set " + id + " ", false, ChatFormatting.AQUA);
            button(out, "[进服触发]", "/fga announcement trigger join " + id, true, ChatFormatting.GREEN);
            button(out, "[更改范围]", "/fga announcement trigger region " + id + " ", false, ChatFormatting.AQUA);
        }
        FGACompat.sendSuccess(context.getSource(), out, false);
        return 1;
    }

    private static void button(MutableComponent out, String label, String command, boolean execute, ChatFormatting color) {
        out.append(Component.literal(label + " ").withStyle(Style.EMPTY.withColor(color).withClickEvent(
                execute ? FgaClickEvents.runCommand(command) : FgaClickEvents.suggestCommand(command))));
    }

    private static String statusLabel(AnnouncementConfig.Announcement announcement, long now) {
        if (announcement.expiresAtMillis() != null && now >= announcement.expiresAtMillis()) return "已过期";
        if (!announcement.enabled()) return "已停用";
        if (announcement.hidden()) return "已隐藏";
        return "启用";
    }

    private static String expiryLabel(AnnouncementConfig.Announcement announcement, long now) {
        if (announcement.expiresAtMillis() == null) return "永久";
        if (now >= announcement.expiresAtMillis()) return "已过期（" + DATE_TIME.format(Instant.ofEpochMilli(announcement.expiresAtMillis())) + "）";
        return "至 " + DATE_TIME.format(Instant.ofEpochMilli(announcement.expiresAtMillis()));
    }

    private static String triggerLabel(AnnouncementConfig.Announcement announcement) {
        AnnouncementConfig.Trigger trigger = announcement.trigger();
        if (trigger == null) return "玩家进服时";
        return trigger.dimension() + " x:" + trigger.minX() + ".." + trigger.maxX()
                + " y:" + trigger.minY() + ".." + trigger.maxY()
                + " z:" + trigger.minZ() + ".." + trigger.maxZ() + "（进入时触发）";
    }

    private static int createAuto(CommandContext<CommandSourceStack> context) {
        String content = StringArgumentType.getString(context, "content");
        try {
            AnnouncementConfig.Announcement created = AnnouncementConfig.create(content, context.getSource().getTextName());
            FGACompat.sendSuccess(context.getSource(), Component.literal("已创建永久公告 #" + created.id()
                    + "，默认玩家进服时触发；使用 /fga announcement info " + created.id() + " 管理"), false);
            return 1;
        } catch (IOException | IllegalArgumentException exception) {
            return fail(context, exception.getMessage());
        }
    }

    private static int createWithId(CommandContext<CommandSourceStack> context) {
        int id = IntegerArgumentType.getInteger(context, "id");
        String content = StringArgumentType.getString(context, "content");
        try {
            AnnouncementConfig.Announcement created = AnnouncementConfig.create(id, content, context.getSource().getTextName());
            FGACompat.sendSuccess(context.getSource(), Component.literal("已创建永久公告 #" + created.id()
                    + "，默认玩家进服时触发；使用 /fga announcement info " + created.id() + " 管理"), false);
            return 1;
        } catch (IOException | IllegalArgumentException exception) {
            return fail(context, exception.getMessage());
        }
    }

    private static int setHeader(CommandContext<CommandSourceStack> context) {
        return mutate(context, () -> AnnouncementConfig.setHeader(StringArgumentType.getString(context, "text")), "公告题头已更新");
    }

    private static int clearHeader(CommandContext<CommandSourceStack> context) {
        return mutate(context, () -> AnnouncementConfig.setHeader(AnnouncementConfig.DEFAULT_HEADER), "公告题头已恢复默认值");
    }

    private static int setContent(CommandContext<CommandSourceStack> context) {
        int id = IntegerArgumentType.getInteger(context, "id");
        String content = StringArgumentType.getString(context, "content");
        return mutate(context, () -> AnnouncementConfig.setContent(id, content, context.getSource().getTextName()), "公告内容已更新");
    }

    private static int setEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        int id = IntegerArgumentType.getInteger(context, "id");
        return mutate(context, () -> AnnouncementConfig.setEnabled(id, enabled), "公告 #" + id + (enabled ? " 已启用" : " 已停用"));
    }

    private static int setHidden(CommandContext<CommandSourceStack> context, boolean hidden) {
        int id = IntegerArgumentType.getInteger(context, "id");
        return mutate(context, () -> AnnouncementConfig.setHidden(id, hidden), "公告 #" + id + (hidden ? " 已隐藏" : " 已取消隐藏"));
    }

    private static int setExpiry(CommandContext<CommandSourceStack> context) {
        int id = IntegerArgumentType.getInteger(context, "id");
        String duration = StringArgumentType.getString(context, "duration");
        return mutate(context, () -> AnnouncementConfig.setExpiry(id, duration),
                "公告 #" + id + " 时效已设为 " + duration);
    }

    private static int delete(CommandContext<CommandSourceStack> context) {
        int id = IntegerArgumentType.getInteger(context, "id");
        return mutate(context, () -> AnnouncementConfig.delete(id), "公告 #" + id + " 已删除");
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        try {
            AnnouncementConfig.reload();
            FGACompat.sendSuccess(context.getSource(), Component.literal("公告配置已重新加载"), false);
            return 1;
        } catch (IOException | IllegalArgumentException exception) {
            return fail(context, exception.getMessage());
        }
    }

    private static int setJoinTrigger(CommandContext<CommandSourceStack> context) {
        int id = IntegerArgumentType.getInteger(context, "id");
        return mutate(context, () -> AnnouncementConfig.setJoinTrigger(id), "公告 #" + id + " 已设为玩家进服时触发");
    }

    private static int setRegionTrigger(CommandContext<CommandSourceStack> context) {
        int id = IntegerArgumentType.getInteger(context, "id");
        String dimension = ResourceLocationArgument.getId(context, "dimension").toString();
        int x1 = IntegerArgumentType.getInteger(context, "x1");
        int y1 = IntegerArgumentType.getInteger(context, "y1");
        int z1 = IntegerArgumentType.getInteger(context, "z1");
        int x2 = IntegerArgumentType.getInteger(context, "x2");
        int y2 = IntegerArgumentType.getInteger(context, "y2");
        int z2 = IntegerArgumentType.getInteger(context, "z2");
        return mutate(context, () -> AnnouncementConfig.setRegionTrigger(id, dimension, x1, y1, z1, x2, y2, z2),
                "公告 #" + id + " 已设为指定维度坐标范围内首次进入时触发");
    }

    private static int fail(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendFailure(Component.literal(message == null ? "操作失败" : message));
        return 0;
    }

    private static int mutate(CommandContext<CommandSourceStack> context, Operation action, String success) {
        if (AnnouncementConfig.isLoadFailed()) return fail(context, "公告配置文件损坏；请修复/备份文件后执行 reload");
        try {
            action.run();
            FGACompat.sendSuccess(context.getSource(), Component.literal(success), false);
            return 1;
        } catch (IOException | IllegalArgumentException exception) {
            return fail(context, exception.getMessage());
        }
    }

    @FunctionalInterface
    private interface Operation {
        void run() throws IOException;
    }
}
//#endif
