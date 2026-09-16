package carpet.fga;

//#if MC >= 1.21 && MC <= 26.3
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
        names.addAll(original);
        var customPresets = FGASettings.fakePlayerNamePresetValues();
        if (!customPresets.isEmpty() || !"false".equalsIgnoreCase(FGASettings.fakePlayerNamePresets)) {
            // Carpet's built-in suggestions are only fake-player presets. Keep online
            // players intact, then replace the built-in names with the configured list.
            if (source.getServer().getPlayerList().getPlayerByName("Steve") == null) names.remove("Steve");
            if (source.getServer().getPlayerList().getPlayerByName("Alex") == null) names.remove("Alex");
            names.addAll(customPresets);
        }
        boolean customConfigured = !"false".equalsIgnoreCase(FGASettings.fakePlayerNamePresets);
        if (actor == null || "false".equals(FGASettings.playerPossession) && !PlayerPossessionManager.isParticipant(actor)) {
            return names;
        }
        // Keep the current possession target filtering, while retaining configured
        // fake-player presets so /player <preset> remains discoverable.
        var possessionNames = new LinkedHashSet<String>();
        if (customConfigured) possessionNames.addAll(customPresets);
        for (ServerPlayer target : source.getServer().getPlayerList().getPlayers()) {
            if (PlayerPossessionManager.isSessionTarget(actor, target)
                    || actor != target && !PlayerPossessionManager.isParticipant(target) && PlayerPossessionManager.canStart(actor, target)) {
                possessionNames.add(target.getScoreboardName());
            }
        }
        return possessionNames;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("possess")
                .requires(source -> source.getPlayer() != null && (PlayerPossessionManager.isParticipant(source.getPlayer())
                        || PlayerPossessionManager.allows(FGASettings.playerPossession,
                            PlayerPossessionManager.isOp(source.getServer(), source.getPlayer().getGameProfile()), true)))
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
