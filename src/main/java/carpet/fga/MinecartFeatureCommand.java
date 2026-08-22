//#if MC == 1.20.1 || MC == 1.21.1
package carpet.fga;

import carpet.utils.CommandHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class MinecartFeatureCommand {
    private MinecartFeatureCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("minecart"));
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .requires(source -> CommandHelper.canUseCommand(source, FGASettings.minecartFeatureCommandPermission))
                .executes(MinecartFeatureCommand::help)
                .then(Commands.literal("help").executes(MinecartFeatureCommand::help))
                .then(Commands.literal("status").executes(MinecartFeatureCommand::status))
                .then(Commands.literal("firework")
                        .then(Commands.literal("set")
                                .then(Commands.argument("maxSpeed", DoubleArgumentType.doubleArg(0.1D, 4.0D))
                                        .then(Commands.argument("durationPerFlight", IntegerArgumentType.integer(1, 24000))
                                                .then(Commands.argument("deceleration", DoubleArgumentType.doubleArg(0.001D, 1.0D))
                                                        .executes(MinecartFeatureCommand::setFirework)))))
                        .then(Commands.literal("reset").executes(MinecartFeatureCommand::resetFirework)))
                .then(Commands.literal("chain")
                        .then(Commands.literal("set")
                                .then(Commands.argument("maxDistance", DoubleArgumentType.doubleArg(1.0D, 8.0D))
                                        .executes(MinecartFeatureCommand::setChain)))
                        .then(Commands.literal("reset").executes(MinecartFeatureCommand::resetChain)));
    }

    private static int setFirework(CommandContext<CommandSourceStack> context) {
        try {
            MinecartFeatureConfig.setFirework(
                    DoubleArgumentType.getDouble(context, "maxSpeed"),
                    IntegerArgumentType.getInteger(context, "durationPerFlight"),
                    DoubleArgumentType.getDouble(context, "deceleration"));
            return status(context);
        } catch (Exception exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int resetFirework(CommandContext<CommandSourceStack> context) {
        try {
            MinecartFeatureConfig.resetFirework();
            return status(context);
        } catch (Exception exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int setChain(CommandContext<CommandSourceStack> context) {
        try {
            MinecartFeatureConfig.setChainDistance(DoubleArgumentType.getDouble(context, "maxDistance"));
            return status(context);
        } catch (Exception exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int resetChain(CommandContext<CommandSourceStack> context) {
        try {
            MinecartFeatureConfig.resetChain();
            return status(context);
        } catch (Exception exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        MinecartFeatureConfig.State state = MinecartFeatureConfig.snapshot();
        String text = "firework=" + FGASettings.fireworkMinecartBoost
                + " maxSpeed=" + format(state.maxSpeed())
                + " durationPerFlight=" + state.durationPerFlight() + "gt"
                + " deceleration=" + format(state.deceleration())
                + " activeBoosts=" + MinecartFeatureManager.activeBoostCount()
                + " fullBoostRemaining=" + MinecartFeatureManager.longestFullBoostTicks() + "gt"
                + " lastBoostDuration=" + MinecartFeatureManager.lastBoostDuration() + "gt"
                + "; chain=" + FGASettings.chainMinecartBinding
                + " maxDistance=" + format(state.chainDistance())
                + "; links=" + MinecartFeatureManager.linkCount()
                + " paidLinks=" + MinecartFeatureManager.refundableLinkCount()
                + (MinecartFeatureConfig.isLoadFailed() ? "; configuration invalid / 配置文件损坏" : "");
        context.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        MutableComponent message = Component.literal("Minecart Features / 矿车功能\n").withStyle(ChatFormatting.GOLD);
        line(message, "/minecart status", "查看烟花加速、锁链列车与参数 / show feature status and settings");
        line(message, "/minecart firework set 1.2 10 0.02", "设置最高速度、每级持续gt和减速度 / configure firework boost");
        line(message, "/minecart firework reset", "恢复烟花默认参数 / reset firework defaults");
        line(message, "/minecart chain set 1.0", "设置锁链最大中心距离 / configure maximum chain distance");
        line(message, "/minecart chain reset", "恢复锁链默认距离 / reset chain defaults");
        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static void line(MutableComponent message, String command, String note) {
        message.append(Component.literal(command).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                        .withClickEvent(FgaClickEvents.suggestCommand(command))))
                .append(Component.literal("  # " + note + "\n").withStyle(ChatFormatting.GOLD));
    }

    private static int failure(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendFailure(Component.literal(message == null ? "unknown error" : message));
        return 0;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value))
                : String.format(java.util.Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
//#endif
