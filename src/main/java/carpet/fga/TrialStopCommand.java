//#if MC >= 1.21 && MC <= 26.2
package carpet.fga;

import carpet.utils.CommandHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class TrialStopCommand {
    private static final int MAX_RADIUS = 30_000_000;

    private TrialStopCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("trialStop"));
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .requires(source -> CommandHelper.canUseCommand(source, FGASettings.trialStopCommandPermission))
                .executes(TrialStopCommand::help)
                .then(Commands.literal("help").executes(TrialStopCommand::help))
                .then(rangeBranch(false))
                .then(Commands.literal("dimension")
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .then(rangeBranch(true))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> rangeBranch(boolean dimension) {
        return Commands.literal("range")
                .then(radiusArguments(dimension))
                .then(Commands.literal("from")
                        .then(position("from")
                                .then(addExecutionOptions(position("to"), false, dimension))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> radiusArguments(boolean dimension) {
        RequiredArgumentBuilder<CommandSourceStack, Integer> radius =
                Commands.argument("radius", IntegerArgumentType.integer(0, MAX_RADIUS))
                        .suggests(TrialStopCommand::suggestRadius);
        return addExecutionOptions(radius, true, dimension);
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T addExecutionOptions(
            T root, boolean radius, boolean dimension) {
        return root.executes(context -> execute(context, radius, TrialSpawnerStopManager.RewardMode.NONE, false, dimension))
                .then(modeBranch("none", TrialSpawnerStopManager.RewardMode.NONE, radius, dimension))
                .then(modeBranch("reward", TrialSpawnerStopManager.RewardMode.REWARD, radius, dimension))
                .then(modeBranch("fast", TrialSpawnerStopManager.RewardMode.FAST, radius, dimension))
                .then(clearBranch(radius, TrialSpawnerStopManager.RewardMode.NONE, dimension));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> modeBranch(
            String name, TrialSpawnerStopManager.RewardMode mode, boolean radius, boolean dimension) {
        return Commands.literal(name)
                .executes(context -> execute(context, radius, mode, false, dimension))
                .then(clearBranch(radius, mode, dimension));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> clearBranch(
            boolean radius, TrialSpawnerStopManager.RewardMode mode, boolean dimension) {
        return Commands.literal("clear")
                .executes(context -> execute(context, radius, mode, true, dimension));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Coordinates> position(String name) {
        return Commands.argument(name, BlockPosArgument.blockPos()).suggests(TrialStopCommand::suggestPositions);
    }

    private static int execute(CommandContext<CommandSourceStack> context, boolean radius,
                               TrialSpawnerStopManager.RewardMode mode, boolean clear, boolean dimension) {
        try {
            ServerLevel level = dimension
                    ? DimensionArgument.getDimension(context, "dimension")
                    : context.getSource().getLevel();
            Predicate<BlockPos> area;
            if (radius) {
                BlockPos center = BlockPos.containing(context.getSource().getPosition());
                long range = IntegerArgumentType.getInteger(context, "radius");
                double squared = (double) range * range;
                area = pos -> {
                    double dx = (double) pos.getX() - center.getX();
                    double dz = (double) pos.getZ() - center.getZ();
                    return dx * dx + dz * dz <= squared;
                };
            } else {
                BlockPos from = BlockPosArgument.getBlockPos(context, "from");
                BlockPos to = BlockPosArgument.getBlockPos(context, "to");
                int minX = Math.min(from.getX(), to.getX());
                int minY = Math.min(from.getY(), to.getY());
                int minZ = Math.min(from.getZ(), to.getZ());
                int maxX = Math.max(from.getX(), to.getX());
                int maxY = Math.max(from.getY(), to.getY());
                int maxZ = Math.max(from.getZ(), to.getZ());
                area = pos -> pos.getX() >= minX && pos.getX() <= maxX
                        && pos.getY() >= minY && pos.getY() <= maxY
                        && pos.getZ() >= minZ && pos.getZ() <= maxZ;
            }
            TrialSpawnerStopManager.Result result = TrialSpawnerStopManager.stop(level, area, mode, clear);
            MutableComponent message = Component.literal("Trial stop / 试炼截停: ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("dimension=" + level.dimension().location()
                            + ", loadedChunks=" + result.scannedChunks()
                            + ", stopped=" + result.stoppedSpawners()
                            + ", removedMobs=" + result.removedMobs()
                            + ", reward=" + mode.name().toLowerCase(Locale.ROOT)
                            + ", clear=" + clear).withStyle(ChatFormatting.GRAY));
            FGACompat.sendSuccess(context.getSource(), message, true);
            return result.stoppedSpawners();
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage() == null
                    ? exception.toString() : exception.getMessage()));
            return 0;
        }
    }

    private static CompletableFuture<Suggestions> suggestPositions(CommandContext<CommandSourceStack> context,
                                                                    SuggestionsBuilder builder) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getEntity() instanceof ServerPlayer value ? value : null;
        if (player != null) {
            BlockPos self = player.blockPosition();
            builder.suggest(self.getX() + " " + self.getY() + " " + self.getZ(),
                    Component.literal("自身坐标 / your position"));
            builder.suggest("~ ~ ~", Component.literal("相对自身 / relative position"));
            HitResult hit = player.pick(64.0D, 0.0F, false);
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos target = block.getBlockPos();
                builder.suggest(target.getX() + " " + target.getY() + " " + target.getZ(),
                        Component.literal("指向方块 / targeted block"));
            }
        }
        return BlockPosArgument.blockPos().listSuggestions(context, builder);
    }

    private static CompletableFuture<Suggestions> suggestRadius(CommandContext<CommandSourceStack> context,
                                                                 SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        suggestRadius(builder, remaining, "16", "小范围 / small range");
        suggestRadius(builder, remaining, "32", "中等范围 / medium range");
        suggestRadius(builder, remaining, "64", "大范围 / large range");
        return builder.buildFuture();
    }

    private static void suggestRadius(SuggestionsBuilder builder, String remaining, String value, String tooltip) {
        if (value.startsWith(remaining)) {
            builder.suggest(value, Component.literal(tooltip));
        }
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        MutableComponent out = Component.literal("试炼刷怪笼截停 / Trial spawner stop\n")
                .withStyle(ChatFormatting.GOLD);
        line(out, "/fga trialStop range <半径> [none|reward|fast] [clear]",
                "以执行位置为中心的水平范围，半径单位为格 / horizontal range centered on the command source");
        line(out, "/fga trialStop range from <起点XYZ> <终点XYZ> [none|reward|fast] [clear]",
                "完整 XYZ 方框 / full XYZ box");
        line(out, "/fga trialStop dimension <维度ID> range <半径|from ...> [none|reward|fast] [clear]",
                "用 dimension 前置选择维度 / select the dimension with the dimension prefix");
        out.append(Component.literal("半径可补全 16、32、64，也可手动输入其他合法数值；省略奖励模式时使用 none，只处理已加载区块\n"
                + "none 为无奖励，reward 按原版节奏喷出后立即刷新，fast 立即喷完，clear 只移除登记怪物\n"
                + "Radius presets are 16, 32, and 64; the default mode is none; reward skips cooldown after vanilla ejection timing; fast ejects immediately")
                .withStyle(ChatFormatting.YELLOW));
        FGACompat.sendSuccess(context.getSource(), out, false);
        return 1;
    }

    private static void line(MutableComponent out, String command, String note) {
        out.append(Component.literal(command).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                        .withClickEvent(FgaClickEvents.suggestCommand(command))))
                .append(Component.literal("  # " + note + "\n").withStyle(ChatFormatting.GOLD));
    }
}
//#endif
