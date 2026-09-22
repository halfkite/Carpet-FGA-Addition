//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import carpet.CarpetSettings;
import carpet.utils.CommandHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

import java.util.Collection;

/** Commands for resetting a player's food and saturation values. */
public final class PlayerFoodCommand {
    private PlayerFoodCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("food"));
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                //#if MC >= 1.21 && MC <= 26.3
                .requires(PlayerFoodCommand::canUseCommand)
                //#else
                //$$ .requires(source -> CommandHelper.canUseCommand(source, CarpetSettings.commandPlayer))
                //#endif
                .then(Commands.literal("clear")
                        .executes(PlayerFoodCommand::clearSelf)
                        .then(Commands.argument("targets", EntityArgument.players())
                                //#if MC >= 1.21 && MC <= 26.3
                                .requires(PlayerFoodCommand::canUseTargets)
                                //#endif
                                .executes(PlayerFoodCommand::clearTargets)));
    }

    //#if MC >= 1.21 && MC <= 26.3
    private static boolean canUseCommand(CommandSourceStack source) {
        return PlayerFoodCommandPolicy.allowsRoot(
                FGASettings.foodCommandPermission,
                source.getEntity() instanceof ServerPlayer,
                permissionLevel(source));
    }

    private static boolean canUseTargets(CommandSourceStack source) {
        return PlayerFoodCommandPolicy.allowsTargets(
                FGASettings.foodCommandPermission, permissionLevel(source));
    }

    private static int permissionLevel(CommandSourceStack source) {
        for (int level = 4; level >= 0; level--) {
            if (FGACompat.hasPermission(source, level)) return level;
        }
        return -1;
    }
    //#endif

    private static int clearSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        clear(player);
        context.getSource().sendSuccess(() -> Component.translatable(
                "carpet.fga.food.cleared.self"), false);
        return 1;
    }

    private static int clearTargets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer player : players) clear(player);
        int count = players.size();
        context.getSource().sendSuccess(() -> Component.translatable(
                "carpet.fga.food.cleared.targets", count), true);
        return count;
    }

    private static void clear(ServerPlayer player) {
        FoodData food = player.getFoodData();
        food.setFoodLevel(0);
        food.setSaturation(0.0F);
        player.connection.send(new ClientboundSetHealthPacket(
                player.getHealth(), food.getFoodLevel(), food.getSaturationLevel()));
    }
}
//#endif
