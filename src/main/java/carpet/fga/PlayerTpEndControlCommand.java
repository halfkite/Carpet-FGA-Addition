//#if MC >= 1.21 && MC <= 26.2
package carpet.fga;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Command surface for the per-player End portal preferences. */
public final class PlayerTpEndControlCommand {
    private PlayerTpEndControlCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("playertpend"));
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name).requires(source -> PlayerTpEndControlManager.isControlEnabled())
                .executes(PlayerTpEndControlCommand::help)
                .then(Commands.literal("help").executes(PlayerTpEndControlCommand::help))
                .then(Commands.literal("status")
                        .executes(context -> status(context, self(context)))
                        .then(target().executes(context -> status(context, target(context)))))
                .then(Commands.literal("set")
                        .then(portal().then(value()))
                        .then(target().then(portal().then(value()))))
                .then(Commands.literal("reset")
                        .executes(context -> reset(context, self(context), null))
                        .then(portal().executes(context -> reset(context, self(context), portal(context))))
                        .then(target()
                                .executes(context -> reset(context, target(context), null))
                                .then(portal().executes(context -> reset(context, target(context), portal(context))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> target() {
        return Commands.argument("player", StringArgumentType.word()).suggests((context, builder) ->
                net.minecraft.commands.SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerList().getPlayers()
                        .stream().map(player -> player.getGameProfile().getName()), builder));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> portal() {
        return Commands.argument("portal", StringArgumentType.word()).suggests((context, builder) ->
                net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("enter", "exit", "gateway"), builder));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> value() {
        return Commands.argument("value", StringArgumentType.word()).suggests((context, builder) ->
                        net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("allow", "deny"), builder))
                .executes(PlayerTpEndControlCommand::set);
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) return player;
        throw new IllegalArgumentException("a player source is required / 需要玩家执行者");
    }

    private static ServerPlayer target(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "player");
        return context.getSource().getServer().getPlayerList().getPlayers().stream()
                .filter(player -> player.getGameProfile().getName().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("player must be online / 玩家必须在线"));
    }

    private static PlayerTpEndControlManager.PortalType portal(CommandContext<CommandSourceStack> context) {
        return PlayerTpEndControlManager.PortalType.parse(StringArgumentType.getString(context, "portal"));
    }

    private static void authorize(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer actor)) return;
        if (actor == target || FGACompat.hasPermission(context.getSource(), 2)) return;
        if (target instanceof carpet.patches.EntityPlayerMPFake) return;
        throw new IllegalArgumentException("only OP may modify another real player / 只有 OP 可以修改其他真人玩家");
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = context.getNodes().stream().anyMatch(node -> "player".equals(node.getNode().getName()))
                    ? target(context) : self(context);
            authorize(context, target);
            String value = StringArgumentType.getString(context, "value");
            if (!"allow".equals(value) && !"deny".equals(value)) throw new IllegalArgumentException("value must be allow or deny / 值必须为 allow 或 deny");
            PlayerTpEndControlManager.set(target, portal(context), "allow".equals(value));
            return status(context, target);
        } catch (Exception exception) {
            return failure(context, exception);
        }
    }

    private static int reset(CommandContext<CommandSourceStack> context, ServerPlayer target,
                             PlayerTpEndControlManager.PortalType type) {
        try {
            authorize(context, target);
            PlayerTpEndControlManager.reset(target, type);
            return status(context, target);
        } catch (Exception exception) {
            return failure(context, exception);
        }
    }

    private static int status(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        try {
            authorize(context, target);
            MutableComponent message = Component.literal("End portal control / 末地门传送控制: " + target.getGameProfile().getName())
                    .withStyle(ChatFormatting.GOLD);
            for (PlayerTpEndControlManager.PortalType type : PlayerTpEndControlManager.PortalType.values()) {
                boolean allowed = PlayerTpEndControlManager.preference(target, type);
                boolean configured = PlayerTpEndControlManager.configured(target, type);
                message.append(Component.literal("\n" + type.id() + " = " + (allowed ? "allow" : "deny")
                        + (configured ? "" : " (default)")).withStyle(allowed ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            FGACompat.sendSuccess(context.getSource(), message, false);
            return 1;
        } catch (Exception exception) {
            return failure(context, exception);
        }
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        MutableComponent out = Component.literal("End Portal Control / 末地门传送控制\n").withStyle(ChatFormatting.GOLD);
        line(out, "/playertpend status [player]", "查看三类末地门设置 / show portal settings");
        line(out, "/playertpend set <enter|exit|gateway> <allow|deny>", "设置自己 / set yourself");
        line(out, "/playertpend set <player> <enter|exit|gateway> <allow|deny>", "OP 可改任何真人；非 OP 可改在线假人 / target a player");
        line(out, "/playertpend reset [enter|exit|gateway]", "恢复自己默认允许 / reset yourself");
        line(out, "/playertpend reset <player> [enter|exit|gateway]", "重置在线目标 / reset a target");
        FGACompat.sendSuccess(context.getSource(), out, false);
        return 1;
    }

    private static void line(MutableComponent out, String command, String note) {
        out.append(Component.literal(command).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                        .withClickEvent(FgaClickEvents.suggestCommand(command))))
                .append(Component.literal("  # " + note + "\n").withStyle(ChatFormatting.GOLD));
    }

    private static int failure(CommandContext<CommandSourceStack> context, Exception exception) {
        context.getSource().sendFailure(Component.literal(exception.getMessage() == null ? exception.toString() : exception.getMessage()));
        return 0;
    }
}
//#endif
