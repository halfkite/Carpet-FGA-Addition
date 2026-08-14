//#if MC == 1.21.1
package carpet.fga;

import carpet.utils.CommandHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerLoadDistanceCommand {
    private PlayerLoadDistanceCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("playerLoadDistance"));
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name).requires(PlayerLoadDistanceCommand::canUse)
                .executes(PlayerLoadDistanceCommand::help)
                .then(Commands.literal("help").executes(PlayerLoadDistanceCommand::help))
                .then(Commands.literal("status").then(target().executes(PlayerLoadDistanceCommand::statusSafe)))
                .then(Commands.literal("set").then(target().then(distance())))
                .then(Commands.literal("reset").then(target()
                        .executes(c -> execute(c, false, false))
                        .then(Commands.literal("persistent").executes(c -> execute(c, false, true)))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> target() {
        return Commands.argument("player", StringArgumentType.word())
                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                        context.getSource().getServer().getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().getName()), builder));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> distance() {
        String[] suggestions = new String[35];
        suggestions[0] = "-1";
        suggestions[1] = "0";
        for (int i = 1; i <= 32; i++) suggestions[i + 1] = Integer.toString(i);
        suggestions[34] = "none";
        return Commands.argument("distance", StringArgumentType.word())
                .suggests((c,b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(suggestions,b))
                .executes(c -> execute(c, true, false))
                .then(Commands.literal("persistent").executes(c -> execute(c, true, true)));
    }

    private static boolean canUse(CommandSourceStack source) {
        String rule = FGASettings.playerLoadDistance;
        return !"false".equals(rule) && CommandHelper.canUseCommand(source, rule);
    }

    private static ServerPlayer target(CommandContext<CommandSourceStack> context) throws Exception {
        String name = StringArgumentType.getString(context, "player");
        ServerPlayer player = context.getSource().getServer().getPlayerList().getPlayers().stream()
                .filter(candidate -> candidate.getGameProfile().getName().equals(name)).findFirst().orElse(null);
        if (player == null) throw new IllegalArgumentException("player must be online / 玩家必须在线");
        return player;
    }

    private static int execute(CommandContext<CommandSourceStack> context, boolean set, boolean persistent) {
        try {
            ServerPlayer player = target(context);
            if (player != context.getSource().getEntity() && !FGACompat.hasPermission(context.getSource(), 2)) throw new IllegalArgumentException("only OP may modify another player / 只有OP可以修改其他玩家");
            if (persistent && !FGACompat.hasPermission(context.getSource(), 2)) throw new IllegalArgumentException("only OP may persist settings / 只有OP可以持久化设置");
            if (set) PlayerLoadDistanceManager.set(player, PlayerLoadDistanceManager.parseDistance(StringArgumentType.getString(context, "distance")), persistent);
            else PlayerLoadDistanceManager.reset(player, persistent);
            FGACompat.sendSuccess(context.getSource(), Component.literal("玩家加载距离已更新 / Player load distance updated").withStyle(ChatFormatting.GREEN), false);
            return statusSafe(context);
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage() == null ? exception.toString() : exception.getMessage()));
            return 0;
        }
    }

    private static int statusSafe(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = target(context);
            FGACompat.sendSuccess(context.getSource(), Component.literal("Player / 玩家=" + player.getGameProfile().getName() + "; configured=" + PlayerLoadDistanceManager.formatDistance(PlayerLoadDistanceManager.configured(player)) + "; effective=" + PlayerLoadDistanceManager.describeDistance(PlayerLoadDistanceManager.effective(player))).withStyle(ChatFormatting.GRAY), false);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage() == null ? exception.toString() : exception.getMessage()));
            return 0;
        }
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        MutableComponent out = Component.literal("Player Load Distance / 玩家加载距离\n").withStyle(ChatFormatting.GOLD);
        line(out, "/playerLoadDistance status <player>", "查看状态 / show status");
        line(out, "/playerLoadDistance set <player> <distance>", "临时设置 / temporary setting");
        line(out, "/playerLoadDistance set <player> <distance> persistent", "持久设置 / persistent setting (OP)");
        line(out, "/playerLoadDistance reset <player>", "恢复持久或默认 / restore persistent or default");
        line(out, "/playerLoadDistance reset <player> persistent", "删除持久设置 / remove persistent setting (OP)");
        out.append(Component.literal("距离：-1中心弱加载，0中心强加载并保留3x3弱加载，1-32为区块半径，none为无加载 / -1 weak center, 0 strong center plus 3x3 weak, 1-32 radius, none no player loading\n").withStyle(ChatFormatting.GOLD));
        FGACompat.sendSuccess(context.getSource(), out, false);
        return 1;
    }

    private static void line(MutableComponent out, String command, String note) {
        out.append(Component.literal(command).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withClickEvent(FgaClickEvents.suggestCommand(command))))
                .append(Component.literal("  # " + note + "\n").withStyle(ChatFormatting.GOLD));
    }
}
//#endif
