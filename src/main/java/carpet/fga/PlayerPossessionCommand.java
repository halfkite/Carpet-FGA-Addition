package carpet.fga;

//#if MC == 1.21.1
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import java.util.Collection;
import java.util.LinkedHashSet;
import net.minecraft.server.level.ServerPlayer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;

public final class PlayerPossessionCommand {
    private PlayerPossessionCommand() {}

    public static Collection<String> suggestNames(CommandSourceStack source, Collection<String> original) {
        ServerPlayer actor = source.getPlayer();
        var names = new LinkedHashSet<String>();
        if (actor == null || "false".equals(FGASettings.playerPossession) && !PlayerPossessionManager.isParticipant(actor)) {
            return original;
        } else {
            for (ServerPlayer target : source.getServer().getPlayerList().getPlayers()) {
                if (PlayerPossessionManager.isSessionTarget(actor, target)
                        || actor != target && !PlayerPossessionManager.isParticipant(target) && PlayerPossessionManager.canStart(actor, target)) {
                    names.add(target.getScoreboardName());
                }
            }
        }
        return names;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("possess")
                .requires(source -> source.getPlayer() != null && (PlayerPossessionManager.isParticipant(source.getPlayer())
                        || PlayerPossessionManager.allows(FGASettings.playerPossession,
                            source.getServer().getPlayerList().isOp(source.getPlayer().getGameProfile()), true)))
                .executes(PlayerPossessionCommand::start)
                .then(Commands.literal("stop").executes(PlayerPossessionCommand::stop));
    }

    private static int start(CommandContext<CommandSourceStack> context) {
        ServerPlayer actor = context.getSource().getPlayer();
        if (actor == null) return fail(context, "denied");
        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(getString(context, "player"));
        if (target == null) return fail(context, "missing");
        String error = PlayerPossessionManager.start(actor, target);
        return error == null ? 1 : fail(context, error);
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        ServerPlayer actor = context.getSource().getPlayer();
        return actor != null && PlayerPossessionManager.stop(actor, getString(context, "player"))
                ? 1 : fail(context, "no_session");
    }

    private static int fail(CommandContext<CommandSourceStack> context, String key) {
        context.getSource().sendFailure(PlayerPossessionManager.text(context.getSource().getPlayer(), key));
        return 0;
    }
}
//#endif
