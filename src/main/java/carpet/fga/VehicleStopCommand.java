package carpet.fga;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

public final class VehicleStopCommand {
    private VehicleStopCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root());
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("vehicleStop")
                .requires(source -> source.getEntity() instanceof ServerPlayer || FGACompat.hasPermission(source, 2))
                .executes(VehicleStopCommand::help)
                .then(Commands.literal("help").executes(VehicleStopCommand::help))
                .then(Commands.literal("status").executes(context -> status(context, self(context))))
                .then(selfSet())
                .then(Commands.literal("reset").executes(context -> reset(context, self(context))))
                .then(Commands.literal("player").requires(source -> FGACompat.hasPermission(source, 2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("status")
                                        .executes(context -> status(context, selected(context))))
                                .then(targetSet())
                                .then(Commands.literal("reset")
                                        .executes(context -> reset(context, selected(context))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> selfSet() {
        return Commands.literal("set")
                .then(setting("minecart", VehicleStopConfig.Target.MINECART, false))
                .then(setting("boat", VehicleStopConfig.Target.BOAT, false))
                .then(setting("all", VehicleStopConfig.Target.ALL, false));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> targetSet() {
        return Commands.literal("set")
                .then(setting("minecart", VehicleStopConfig.Target.MINECART, true))
                .then(setting("boat", VehicleStopConfig.Target.BOAT, true))
                .then(setting("all", VehicleStopConfig.Target.ALL, true));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> setting(
            String name, VehicleStopConfig.Target target, boolean selected) {
        return Commands.literal(name)
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> set(context, selected ? selected(context) : self(context), target,
                                BoolArgumentType.getBool(context, "enabled"))));
    }

    private static int set(CommandContext<CommandSourceStack> context, ServerPlayer player,
                           VehicleStopConfig.Target target, boolean enabled) {
        try {
            VehicleStopConfig.set(player.getUUID(), player.getGameProfile().getName(), target, enabled);
            FGACompat.sendSuccess(context.getSource(), FGACompat.literal(
                    "已更新 " + player.getGameProfile().getName() + " 的载具急停设置 / Updated vehicle stop settings for "
                            + player.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), false);
            return status(context, player);
        } catch (Exception exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int reset(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        try {
            VehicleStopConfig.reset(player.getUUID());
            FGACompat.sendSuccess(context.getSource(), FGACompat.literal(
                    "已重置 " + player.getGameProfile().getName() + " 的载具急停设置 / Reset vehicle stop settings for "
                            + player.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), false);
            return status(context, player);
        } catch (Exception exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int status(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        VehicleStopConfig.Entry preference = VehicleStopConfig.preference(player.getUUID());
        boolean minecart = VehicleStopManager.enabled(player, VehicleStopManager.Kind.MINECART);
        boolean boat = VehicleStopManager.enabled(player, VehicleStopManager.Kind.BOAT);
        String text = "玩家 / Player=" + player.getGameProfile().getName()
                + "; 全局模式 / global=" + FGASettings.vehicleStopOnDismount
                + "; 个人设置 / personal minecart=" + preference.minecart() + " boat=" + preference.boat()
                + (VehicleStopConfig.isConfigured(player.getUUID()) ? "" : " (默认 / default)")
                + "; 当前生效 / effective minecart=" + minecart + " boat=" + boat
                + "; 最后动作 / lastAction=" + VehicleStopManager.lastAction()
                + (VehicleStopConfig.isLoadFailed() ? "; 配置文件损坏 / configuration invalid" : "");
        FGACompat.sendSuccess(context.getSource(), FGACompat.literal(text).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        MutableComponent message = FGACompat.literal("Vehicle Stop / 载具急停\n").withStyle(ChatFormatting.GOLD);
        line(message, "/vehicleStop status", "查看自己的设置与当前生效状态 / show your settings and effective state");
        line(message, "/vehicleStop set minecart true", "设置自己的矿车急停 / configure your minecart stop");
        line(message, "/vehicleStop set boat true", "设置自己的船急停 / configure your boat stop");
        line(message, "/vehicleStop set all false", "同时设置两种载具 / configure both vehicle types");
        line(message, "/vehicleStop reset", "删除个人设置并恢复 custom 默认关闭 / reset to custom defaults");
        if (FGACompat.hasPermission(context.getSource(), 2)) {
            line(message, "/vehicleStop player ", "管理在线玩家的设置 / manage an online player");
        }
        FGACompat.sendSuccess(context.getSource(), message, false);
        return 1;
    }

    private static void line(MutableComponent message, String command, String note) {
        message.append(FGACompat.literal(command).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                        .withClickEvent(FgaClickEvents.suggestCommand(command))))
                .append(FGACompat.literal("  # " + note + "\n").withStyle(ChatFormatting.GOLD));
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    private static ServerPlayer selected(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return EntityArgument.getPlayer(context, "player");
    }

    private static int failure(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendFailure(FGACompat.literal(message == null ? "unknown error" : message));
        return 0;
    }
}
