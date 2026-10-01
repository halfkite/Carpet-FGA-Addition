//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Adds an explicit dimension-and-position self-teleport form for players granted access by the rule. */
public final class SpectatorCrossDimensionTeleportCommand {
    private static final SimpleCommandExceptionType OUT_OF_BOUNDS = new SimpleCommandExceptionType(
            Component.translatable("carpet-fga-addition.command.spectatorFreeTeleport.outOfBounds"));

    private SpectatorCrossDimensionTeleportCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /tp is vanilla's redirect alias for /teleport, so registering on the
        // destination node exposes the same argument branch through both names.
        dispatcher.register(Commands.literal("teleport")
                .then(Commands.literal("in")
                        .requires(SpectatorCrossDimensionTeleportCommand::canUse)
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .then(Commands.argument("position", Vec3Argument.vec3())
                                        .executes(SpectatorCrossDimensionTeleportCommand::teleportSelf)))));
    }

    private static boolean canUse(CommandSourceStack source) {
        return SpectatorFreeTeleport.isEnabled()
                && source.getEntity() instanceof ServerPlayer player
                && (player.isSpectator() || SpectatorFreeTeleport.isFullAccess(source))
                && SpectatorFreeTeleport.canUseTeleportCommand(source);
    }

    private static int teleportSelf(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel destination = DimensionArgument.getDimension(context, "dimension");
        Vec3 position = Vec3Argument.getVec3(context, "position");
        if (!Level.isInSpawnableBounds(BlockPos.containing(position))
                || !destination.getWorldBorder().isWithinBounds(position)) {
            throw OUT_OF_BOUNDS.create();
        }

        //#if MC == 1.21.1
        player.teleportTo(destination, position.x, position.y, position.z, player.getYRot(), player.getXRot());
        //#else
        //$$ player.teleportTo(destination, position.x, position.y, position.z, java.util.Set.of(),
        //$$         player.getYRot(), player.getXRot(), false);
        //#endif

        String dimensionName;
        //#if MC >= 1.21.11
        //$$ dimensionName = destination.dimension().identifier().toString();
        //#else
        dimensionName = destination.dimension().location().toString();
        //#endif
        String coordinates = String.format(Locale.ROOT, "%.2f %.2f %.2f", position.x, position.y, position.z);
        source.sendSuccess(() -> Component.translatable(
                "carpet-fga-addition.command.spectatorFreeTeleport.crossDimensionSuccess",
                dimensionName, coordinates), false);
        return 1;
    }
}
//#endif
