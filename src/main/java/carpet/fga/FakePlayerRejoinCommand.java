//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import carpet.CarpetSettings;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
//#if MC >= 1.21.3
//$$ import net.minecraft.world.level.portal.TeleportTransition;
//#endif
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/** Adds coordinate arguments to TIS rejoin without copying its implementation. */
public final class FakePlayerRejoinCommand {
    private static final Map<String, Destination> PENDING = new HashMap<>();
    private static final ThreadLocal<?> TIS_REJOIN = findTisFlag();

    private FakePlayerRejoinCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("rejoin")
                .requires(source -> FabricLoader.getInstance().isModLoaded("carpet-tis-addition"))
                .then(Commands.literal("at")
                        .requires(source -> FGASettings.enhancedFakePlayerRejoin
                                && FabricLoader.getInstance().isModLoaded("carpet-tis-addition"))
                        .then(Commands.argument("position", Vec3Argument.vec3())
                                .executes(context -> rejoinAt(context, false, false))
                                .then(Commands.literal("in")
                                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                .executes(context -> rejoinAt(context, false, true))))
                                .then(Commands.literal("facing")
                                        .then(Commands.argument("direction", RotationArgument.rotation())
                                                .executes(context -> rejoinAt(context, true, false))
                                                .then(Commands.literal("in")
                                                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                                .executes(context -> rejoinAt(context, true, true))))))));
    }

    private static int rejoinAt(CommandContext<CommandSourceStack> context, boolean facing, boolean inDimension)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!FGASettings.enhancedFakePlayerRejoin) {
            context.getSource().sendFailure(Component.literal("TIS rejoin enhancement is unavailable"));
            return 0;
        }
        ServerLevel level = inDimension ? DimensionArgument.getDimension(context, "dimension") : context.getSource().getLevel();
        Vec3 pos = Vec3Argument.getVec3(context, "position");
        if (!Level.isInSpawnableBounds(BlockPos.containing(pos)) || !level.getWorldBorder().isWithinBounds(pos)) {
            context.getSource().sendFailure(Component.literal("Rejoin destination is outside world bounds"));
            return 0;
        }
        Vec2 rotation = facing ? RotationArgument.getRotation(context, "direction").getRotation(context.getSource()) : null;
        String playerName = StringArgumentType.getString(context, "player");
        String key = playerName.toLowerCase(Locale.ROOT);
        MinecraftServer server = context.getSource().getServer();
        clearFinishedPending(server, playerName, key);
        if (PENDING.containsKey(key)) {
            context.getSource().sendFailure(Component.literal("A rejoin is already pending for this player"));
            return 0;
        }
        CommandNode<CommandSourceStack> player = server.getCommands().getDispatcher()
                .getRoot().getChild("player");
        CommandNode<CommandSourceStack> name = player == null ? null : player.getChild("player");
        CommandNode<CommandSourceStack> rejoin = name == null ? null : name.getChild("rejoin");
        Command<CommandSourceStack> tisCommand = rejoin == null ? null : rejoin.getCommand();
        if (tisCommand == null) {
            context.getSource().sendFailure(Component.literal("Carpet TIS /player rejoin is not registered"));
            return 0;
        }
        PENDING.put(key, new Destination(playerName, level, pos, rotation, server.getTickCount() + 200));
        try {
            int result = tisCommand.run(context);
            if (result <= 0) PENDING.remove(key);
            return result;
        } catch (RuntimeException | com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            PENDING.remove(key);
            throw exception;
        }
    }

    /** Completes a coordinate rejoin after the fake joins; modern Carpet also calls this on async failure. */
    public static void finish(MinecraftServer server, String name, Throwable error) {
        Destination destination = PENDING.remove(name.toLowerCase(Locale.ROOT));
        if (destination == null) return;
        if (error != null) {
            CarpetSettings.LOG.warn("Could not rejoin fake player {}", name, error);
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayerByName(name);
        if (!(player instanceof EntityPlayerMPFake)) return;
        Entity root = player.getRootVehicle();
        if (root != player && !root.hasExactlyOnePlayerPassenger()) return;
        // Vanilla carries each passenger's rotation offset through a vehicle teleport.
        // Rotate the root by the same delta so the fake player faces the requested direction.
        float yaw = destination.rotation == null ? root.getYRot()
                : root.getYRot() + destination.rotation.y - player.getYRot();
        float pitch = destination.rotation == null ? root.getXRot()
                : root.getXRot() + destination.rotation.x - player.getXRot();
        boolean moved;
        //#if MC == 1.21.1
        moved = root.teleportTo(destination.level, destination.position.x, destination.position.y,
                destination.position.z, java.util.Set.of(), yaw, pitch);
        //#else
        //$$ moved = root.teleport(new TeleportTransition(destination.level, destination.position,
        //$$         root.getDeltaMovement(), yaw, pitch, TeleportTransition.DO_NOTHING)) != null;
        //#endif
        if (!moved) CarpetSettings.LOG.warn("Could not teleport rejoined fake player {} and its vehicle", name);
    }

    public static void tick(MinecraftServer server) {
        ArrayList<String> completed = new ArrayList<>();
        Iterator<Map.Entry<String, Destination>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Destination destination = iterator.next().getValue();
            if (destination.expiresAtTick < server.getTickCount()) {
                iterator.remove();
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayerByName(destination.playerName);
            if (player instanceof EntityPlayerMPFake) completed.add(destination.playerName);
        }
        // Older Carpet versions do not expose a stable fake-player creation
        // callback. Complete their coordinate rejoin on the first server tick
        // after the player has joined; modern versions finish in the Mixin.
        for (String name : completed) finish(server, name, null);
    }

    public static void clear() {
        PENDING.clear();
    }

    /** Pending coordinate rejoin is a more reliable callback marker than TIS's async ThreadLocal. */
    public static boolean isPending(String name) {
        return FGASettings.enhancedFakePlayerRejoin && PENDING.containsKey(name.toLowerCase(Locale.ROOT));
    }

    private static void clearFinishedPending(MinecraftServer server, String name, String key) {
        Destination destination = PENDING.get(key);
        if (destination == null) return;
        boolean expired = destination.expiresAtTick < server.getTickCount();
        boolean alreadyOnline = server.getPlayerList().getPlayerByName(name) != null;
        boolean noSpawnInProgress = !isSpawnInProgress(name);
        if (expired || alreadyOnline || noSpawnInProgress) PENDING.remove(key, destination);
    }

    private static boolean isSpawnInProgress(String name) {
        try {
            Method method = EntityPlayerMPFake.class.getMethod("isSpawningPlayer", String.class);
            return Boolean.TRUE.equals(method.invoke(null, name));
        } catch (ReflectiveOperationException | SecurityException exception) {
            // Older Carpet APIs do not expose their in-flight spawn map; keep the request
            // pending until the player appears or the bounded timeout expires.
            return true;
        }
    }

    public static boolean isTisRejoin() {
        return FGASettings.enhancedFakePlayerRejoin && TIS_REJOIN != null && Boolean.TRUE.equals(TIS_REJOIN.get());
    }

    private static ThreadLocal<?> findTisFlag() {
        if (!FabricLoader.getInstance().isModLoaded("carpet-tis-addition")) return null;
        try {
            Class<?> helper = Class.forName("carpettisaddition.helpers.carpet.tweaks.command.fakePlayerRejoin.FakePlayerRejoinHelper");
            Field field = helper.getField("isRejoin");
            return (ThreadLocal<?>) field.get(null);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            CarpetSettings.LOG.warn("Carpet TIS rejoin flag is unavailable", exception);
            return null;
        }
    }

    private record Destination(String playerName, ServerLevel level, Vec3 position, Vec2 rotation, int expiresAtTick) {}
}
//#endif
