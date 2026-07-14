package carpet.fga;

import carpet.CarpetSettings;
import carpet.fakes.ServerPlayerInterface;
import carpet.utils.CommandHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class RangePlayerCommand {
    private RangePlayerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("player")
                .requires(source -> CommandHelper.canUseCommand(source, CarpetSettings.commandPlayer))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.literal("stop").executes(RangePlayerCommand::stop))
                        .then(rangeAction("use", RangeActionManager.Mode.USE))
                        .then(rangeAction("attack", RangeActionManager.Mode.ATTACK))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> rangeAction(
            String name, RangeActionManager.Mode mode) {
        return Commands.literal(name)
                .then(range(mode, false))
                .then(Commands.literal("continuous").then(range(mode, true)));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> range(
            RangeActionManager.Mode mode, boolean continuous) {
        return Commands.literal("range")
                        .then(Commands.literal("help").executes(context -> showHelp(context, mode, continuous)))
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.literal("to")
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(context -> start(context, mode, continuous, Options.defaults(defaultReach(context))))
                                                .then(Commands.argument("options", StringArgumentType.greedyString())
                                                        .suggests(RangePlayerCommand::suggestOptions)
                                                        .executes(context -> start(context, mode, continuous,
                                                                parseOptions(context, defaultReach(context))))))));
    }

    private static double defaultReach(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        return player == null ? 4.5 : player.blockInteractionRange();
    }

    private static int start(CommandContext<CommandSourceStack> context, RangeActionManager.Mode mode,
                             boolean continuous, Options options) throws CommandSyntaxException {
        if (!FGASettings.fakePlayerRangeControl) {
            throw commandError("假人范围控制规则当前未启用，请先执行 /carpet fakePlayerRangeControl true");
        }
        ServerPlayer player = getPlayer(context);
        if (player == null) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("只能控制在线玩家"));
            return 0;
        }
        ServerPlayer sender = context.getSource().getPlayer();
        if (sender != null && !context.getSource().getServer().getPlayerList().isOp(
                //#if MC >= 1.21.9
                //$$ sender.nameAndId()
                //#else
                sender.getGameProfile()
                //#endif
        )
                && sender != player && !(player instanceof carpet.patches.EntityPlayerMPFake)) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("非管理员不能控制其他真实玩家"));
            return 0;
        }
        BlockPos from = BlockPosArgument.getLoadedBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getLoadedBlockPos(context, "to");
        return RangeActionManager.start(context.getSource(), player, mode, from, to, continuous,
                options.pathfinding, options.reach, options.airPlace, options.ignoreObstruction,
                options.placeBlock, options.interactBlock, options.interactSpeed) ? 1 : 0;
    }

    private static Options parseOptions(CommandContext<CommandSourceStack> context, double defaultReach)
            throws CommandSyntaxException {
        String[] tokens = StringArgumentType.getString(context, "options").trim().split("\\s+");
        boolean pathfinding = false;
        boolean airPlace = false;
        boolean ignoreObstruction = false;
        boolean placeBlock = false;
        boolean interactBlock = false;
        boolean useModeSelected = false;
        int interactSpeed = 2;
        double reach = defaultReach;
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < tokens.length; index++) {
            String option = tokens[index].toLowerCase(Locale.ROOT);
            if (!seen.add(option)) {
                throw commandError("参数不能重复：" + tokens[index]);
            }
            switch (option) {
                case "pathfinding" -> pathfinding = true;
                case "airplace" -> airPlace = true;
                case "ignoreobstruction" -> ignoreObstruction = true;
                case "placeblock" -> {
                    placeBlock = true;
                    useModeSelected = true;
                }
                case "interactblock" -> {
                    interactBlock = true;
                    useModeSelected = true;
                }
                case "interactspeed" -> {
                    if (++index >= tokens.length) {
                        throw commandError("interactSpeed 后必须填写次数");
                    }
                    try {
                        interactSpeed = Integer.parseInt(tokens[index]);
                    } catch (NumberFormatException exception) {
                        throw commandError("无效的右键速度：" + tokens[index]);
                    }
                    if (interactSpeed < 1 || interactSpeed > 64) {
                        throw commandError("右键速度必须在 1 到 64 之间");
                    }
                }
                case "reach" -> {
                    if (++index >= tokens.length) {
                        throw commandError("reach 后必须填写手长数值");
                    }
                    try {
                        reach = Double.parseDouble(tokens[index]);
                    } catch (NumberFormatException exception) {
                        throw commandError("无效的手长数值：" + tokens[index]);
                    }
                    if (reach < 0.1 || reach > RangeActionManager.MAX_REACH) {
                        throw commandError("手长必须在 0.1 到 " + RangeActionManager.MAX_REACH + " 之间");
                    }
                }
                default -> throw commandError("未知参数：" + tokens[index]);
            }
        }
        if (!useModeSelected) {
            placeBlock = true;
        }
        return new Options(pathfinding, reach, airPlace, ignoreObstruction, placeBlock, interactBlock,
                interactSpeed);
    }

    private static CompletableFuture<Suggestions> suggestOptions(CommandContext<CommandSourceStack> context,
                                                                   SuggestionsBuilder builder) {
        String input = StringArgumentType.getString(context, "options").toLowerCase(Locale.ROOT);
        String[] tokens = input.trim().isEmpty() ? new String[0] : input.trim().split("\\s+");
        Set<String> used = new HashSet<>(Set.of());
        for (String token : tokens) {
            used.add(token);
        }
        String current = input.endsWith(" ") ? "" : (tokens.length == 0 ? "" : tokens[tokens.length - 1]);
        String commandInput = builder.getInput();
        int replacementStart = commandInput.length() - current.length();
        while (replacementStart > 0 && commandInput.charAt(replacementStart - 1) == ' ') {
            replacementStart--;
        }
        SuggestionsBuilder offset = builder.createOffset(replacementStart);
        String suggestionPrefix = " ";
        if (tokens.length > 0 && "reach".equals(tokens[tokens.length - 1]) && input.endsWith(" ")) {
            offset.suggest(suggestionPrefix + "4.5").suggest(suggestionPrefix + "16");
            return offset.buildFuture();
        }
        if (tokens.length > 0 && "interactspeed".equals(tokens[tokens.length - 1]) && input.endsWith(" ")) {
            offset.suggest(suggestionPrefix + "1").suggest(suggestionPrefix + "2").suggest(suggestionPrefix + "4");
            return offset.buildFuture();
        }
        if (!used.contains("pathfinding")) offset.suggest(suggestionPrefix + "pathfinding");
        if (!used.contains("reach")) offset.suggest(suggestionPrefix + "reach");
        if (!used.contains("airplace")) offset.suggest(suggestionPrefix + "airPlace");
        if (!used.contains("ignoreobstruction")) offset.suggest(suggestionPrefix + "ignoreObstruction");
        if (!used.contains("placeblock")) offset.suggest(suggestionPrefix + "placeBlock");
        if (!used.contains("interactblock")) offset.suggest(suggestionPrefix + "interactBlock");
        if (!used.contains("interactspeed")) offset.suggest(suggestionPrefix + "interactSpeed");
        return offset.buildFuture();
    }

    private static CommandSyntaxException commandError(String message) {
        return new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal(message)).create();
    }

    private static int showHelp(CommandContext<CommandSourceStack> context, RangeActionManager.Mode mode,
                                boolean continuous) {
        String action = mode == RangeActionManager.Mode.USE ? "use" : "attack";
        String continuousPart = continuous ? " continuous" : "";
        context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "§6区域操作帮助\n"
                        + "§f/player <假人> " + action + continuousPart + " range <起点> to <终点> [参数...]\n\n"
                        + "§epathfinding§f  自动走向未完成目标\n"
                        + "§ereach <数值>§f  设置交互距离，最大 " + RangeActionManager.MAX_REACH + "\n"
                        + "§eairPlace§f  允许没有支撑时凭空放置\n"
                        + "§eignoreObstruction§f  忽略视线中的方块阻挡\n"
                        + "§eplaceBlock§f  放置方块，默认副手优先\n"
                        + "§einteractBlock§f  使用主手物品对已有方块右键\n"
                        + "§einteractSpeed <次数>§f  每个位置每游戏刻右键次数，默认 2，最大 64\n\n"
                        + "§7placeBlock 与 interactBlock 可单独或同时使用。\n"
                        + "§7两者都不填写时，默认为 placeBlock。"), false);
        return 1;
    }

    private record Options(boolean pathfinding, double reach, boolean airPlace, boolean ignoreObstruction,
                           boolean placeBlock, boolean interactBlock, int interactSpeed) {
        private static Options defaults(double reach) {
            return new Options(false, reach, false, false, true, false, 2);
        }
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        if (player == null) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("只能控制在线玩家"));
            return 0;
        }
        boolean stopped = RangeActionManager.stop(player);
        ((ServerPlayerInterface) player).getActionPack().stopAll();
        if (stopped) {
            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("已停止假人的区域操作"), false);
        }
        return 1;
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
        return context.getSource().getServer().getPlayerList()
                .getPlayerByName(StringArgumentType.getString(context, "player"));
    }
}
