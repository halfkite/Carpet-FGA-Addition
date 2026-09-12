package carpet.fga;

//#if MC >= 1.21 && MC <= 26.2
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;

/** Read-only inspection of the active possession pairs. */
public final class PlayerPossessionStatusCommand {
    private PlayerPossessionStatusCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("controlPlayer")
                .requires(source -> PlayerPossessionManager.isActive())
                .executes(PlayerPossessionStatusCommand::list)
                .then(Commands.literal("list").executes(PlayerPossessionStatusCommand::list))
                .then(Commands.literal("@").executes(PlayerPossessionStatusCommand::self))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(SUGGEST_PLAYERS)
                        .executes(PlayerPossessionStatusCommand::selected)));
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PLAYERS = (context, builder) -> {
        builder.suggest("@");
        MinecraftServer server = context.getSource().getServer();
        Set<String> names = new LinkedHashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            names.add(player.getScoreboardName());
            names.add(PlayerPossessionManager.profileName(player.getGameProfile()));
            names.add(PlayerPossessionManager.originalName(player.getUUID(), server));
        }
        for (String name : names) if (!name.isEmpty()) builder.suggest(name);
        return builder.buildFuture();
    };

    private static int list(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        var pairs = PlayerPossessionManager.swapPairs();
        if (pairs.isEmpty()) {
            send(context, "list_empty");
            return 1;
        }
        MutableComponent output = PlayerPossessionManager.text(context.getSource().getPlayer(), "list_header").copy()
                .withStyle(ChatFormatting.GOLD);
        for (Map.Entry<UUID, UUID> pair : pairs) {
            output.append(Component.literal("\n  "))
                    .append(selectableName(PlayerPossessionManager.originalName(pair.getKey(), server)))
                    .append(Component.literal(" -> "))
                    .append(selectableName(PlayerPossessionManager.originalName(pair.getValue(), server)));
        }
        context.getSource().sendSuccess(() -> output, false);
        return 1;
    }

    private static int self(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return fail(context, "player_only");
        return show(context, player);
    }

    private static int selected(CommandContext<CommandSourceStack> context) {
        String requested = getString(context, "player");
        if ("@".equals(requested)) return self(context);
        ServerPlayer player = findPlayer(context.getSource().getServer(), requested);
        return player == null ? fail(context, "query_missing") : show(context, player);
    }

    private static int show(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        UUID partnerId = PlayerPossessionManager.swapPartner(player.getUUID());
        if (partnerId == null) {
            context.getSource().sendSuccess(() -> PlayerPossessionManager.text(player, "status_none"), false);
            return 1;
        }
        MinecraftServer server = context.getSource().getServer();
        UUID controllerId = PlayerPossessionManager.isController(player) ? player.getUUID() : partnerId;
        UUID targetId = controllerId.equals(player.getUUID()) ? partnerId : player.getUUID();
        Component result = PlayerPossessionManager.text(player, "status_entry",
                PlayerPossessionManager.originalName(controllerId, server),
                PlayerPossessionManager.originalName(targetId, server));
        context.getSource().sendSuccess(() -> result.copy().withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static ServerPlayer findPlayer(MinecraftServer server, String requested) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (requested.equalsIgnoreCase(player.getScoreboardName())
                    || requested.equalsIgnoreCase(PlayerPossessionManager.profileName(player.getGameProfile()))
                    || requested.equalsIgnoreCase(PlayerPossessionManager.originalName(player.getUUID(), server))) {
                return player;
            }
        }
        return null;
    }

    private static MutableComponent selectableName(String name) {
        return Component.literal(name).withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(FgaClickEvents.suggestCommand("/controlPlayer " + name)));
    }

    private static int fail(CommandContext<CommandSourceStack> context, String key) {
        context.getSource().sendFailure(PlayerPossessionManager.text(context.getSource().getPlayer(), key));
        return 0;
    }

    private static void send(CommandContext<CommandSourceStack> context, String key) {
        context.getSource().sendSuccess(
                () -> PlayerPossessionManager.text(context.getSource().getPlayer(), key), false);
    }
}
//#endif
