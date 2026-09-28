//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Adds /tp <position> <dimension> to vanilla's self-teleport coordinate branch. */
public final class TeleportDimensionSuffixCommand {
    private static final SimpleCommandExceptionType OUT_OF_BOUNDS = new SimpleCommandExceptionType(
            Component.translatable("carpet-fga-addition.command.spectatorFreeTeleport.outOfBounds"));

    private TeleportDimensionSuffixCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> teleport = dispatcher.getRoot().getChild("teleport");
        CommandNode<CommandSourceStack> location = teleport == null ? null : teleport.getChild("location");
        if (location == null || location.getCommand() == null) {
            throw new IllegalStateException("Vanilla /teleport <location> is unavailable");
        }
        addDimensionBranch(location);

        // Some Minecraft versions implement /tp as a redirect, while others
        // register a separate command tree. Preserve either vanilla shape.
        CommandNode<CommandSourceStack> tp = dispatcher.getRoot().getChild("tp");
        CommandNode<CommandSourceStack> tpLocation = tp == null ? null : tp.getChild("location");
        if (tpLocation != null && tpLocation != location) addDimensionBranch(tpLocation);
    }

    private static void addDimensionBranch(CommandNode<CommandSourceStack> location) {
        if (location.getChild("dimension") != null) return;
        location.addChild(Commands.argument("dimension", DimensionArgument.dimension())
                .executes(TeleportDimensionSuffixCommand::teleportSelf)
                .build());
    }

    private static int teleportSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel destination = DimensionArgument.getDimension(context, "dimension");
        Vec3 position = Vec3Argument.getVec3(context, "location");
        if (!Level.isInSpawnableBounds(BlockPos.containing(position))
                || !destination.getWorldBorder().isWithinBounds(position)) {
            throw OUT_OF_BOUNDS.create();
        }
        // Resolve relative coordinates against the original source before crossing dimensions.
        // A relative movement flag on a cross-dimension teleport uses the destination's origin.
        Entity entity = source.getEntityOrException();
        //#if MC == 1.21.1
        boolean teleported = entity.teleportTo(destination, position.x, position.y, position.z,
                java.util.Set.of(), Mth.wrapDegrees(entity.getYRot()), Mth.wrapDegrees(entity.getXRot()));
        //#else
        //$$ boolean teleported = entity.teleportTo(destination, position.x, position.y, position.z,
        //$$         java.util.Set.of(), Mth.wrapDegrees(entity.getYRot()), Mth.wrapDegrees(entity.getXRot()), false);
        //#endif
        if (teleported) {
            if (!(entity instanceof LivingEntity living && living.isFallFlying())) {
                entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0, 0.0, 1.0));
                entity.setOnGround(true);
            }
            if (entity instanceof PathfinderMob mob) mob.getNavigation().stop();
        }
        source.sendSuccess(() -> Component.translatable("commands.teleport.success.location.single",
                entity.getDisplayName(), format(position.x), format(position.y), format(position.z)), true);
        return 1;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%f", value);
    }
}
//#endif
