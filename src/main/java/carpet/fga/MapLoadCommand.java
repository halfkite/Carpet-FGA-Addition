//#if MC == 1.21.1
package carpet.fga;

import carpet.utils.CommandHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;

/** Immediately refreshes the filled map held by the executing player, and controls running loads. */
public final class MapLoadCommand {
    private MapLoadCommand() {
    }

    private enum Action {
        PAUSE, RESUME, STOP
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("mapLoad"));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .requires(source -> CommandHelper.canUseCommand(source, FGASettings.mapLoadCommandPermission))
                .executes(MapLoadCommand::usage)
                .then(Commands.literal("start")
                        .executes(MapLoadCommand::usage)
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests(MapLoadCommand::suggestModes)
                                .executes(context -> execute(context,
                                        MapLoadManager.Mode.parse(StringArgumentType.getString(context, "mode"))))))
                .then(Commands.literal("list").executes(MapLoadCommand::list))
                .then(control("pause", Action.PAUSE))
                .then(control("resume", Action.RESUME))
                .then(control("stop", Action.STOP));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> control(String name, Action action) {
        return Commands.literal(name)
                .executes(context -> control(context, action, null))
                .then(Commands.argument("player", EntityArgument.player())
                        .suggests((context, builder) -> {
                            for (String candidate : MapLoadManager.activeNames()) builder.suggest(candidate);
                            return builder.buildFuture();
                        })
                        .executes(context -> control(context, action, EntityArgument.getPlayer(context, "player"))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestModes(
            CommandContext<CommandSourceStack> context, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (String mode : MapLoadManager.Mode.ids()) builder.suggest(mode);
        return builder.buildFuture();
    }

    /** Loading always needs an explicit mode, so the bare form only explains how to call it. */
    private static int usage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendFailure(MapLoadManager.text("carpet.fga.map_load.usage",
                String.join(" / ", MapLoadManager.Mode.ids())));
        return 0;
    }

    private static int execute(CommandContext<CommandSourceStack> context, MapLoadManager.Mode mode) {
        try {
            if (mode == null) {
                context.getSource().sendFailure(MapLoadManager.text("carpet.fga.map_load.mode_invalid",
                        String.join(" / ", MapLoadManager.Mode.ids())));
                return 0;
            }
            ServerPlayer player = context.getSource().getPlayerOrException();
            ItemStack mapStack = heldMap(player);
            if (mapStack.isEmpty()) {
                context.getSource().sendFailure(MapLoadManager.text("carpet.fga.map_load.no_map"));
                return 0;
            }

            MapId mapId = mapStack.get(DataComponents.MAP_ID);
            if (mapId == null) {
                context.getSource().sendFailure(MapLoadManager.text("carpet.fga.map_load.invalid"));
                return 0;
            }
            MapItemSavedData data = MapItem.getSavedData(mapId, player.serverLevel());
            if (data == null) {
                context.getSource().sendFailure(MapLoadManager.text("carpet.fga.map_load.invalid"));
                return 0;
            }
            if (data.locked) {
                context.getSource().sendFailure(MapLoadManager.text("carpet.fga.map_load.locked", mapId.id()));
                return 0;
            }
            if (data.dimension != player.level().dimension()) {
                context.getSource().sendFailure(MapLoadManager.text("carpet.fga.map_load.dimension", mapId.id()));
                return 0;
            }

            MapItem mapItem = (MapItem) mapStack.getItem();
            if (!MapLoadManager.start(player, mapItem, mapId, data, mode)) {
                context.getSource().sendFailure(MapLoadManager.text("carpet.fga.map_load.busy"));
                return 0;
            }
            int scale = 1 << data.scale;
            int minX = data.centerX - 64 * scale;
            int minZ = data.centerZ - 64 * scale;
            int maxX = data.centerX + 64 * scale - 1;
            int maxZ = data.centerZ + 64 * scale - 1;
            int chunks = MapLoadManager.chunkEstimate(data);
            context.getSource().sendSuccess(() -> MapLoadManager.text(
                    "carpet.fga.map_load.started", minX, minZ, maxX, maxZ, chunks, mode.id()), false);
            return 1;
        } catch (Exception exception) {
            context.getSource().sendFailure(MapLoadManager.text("carpet.fga.map_load.failed",
                    exception.getMessage() == null ? exception.toString() : exception.getMessage()));
            return 0;
        }
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        List<Component> lines = MapLoadManager.listLines();
        if (lines.isEmpty()) {
            context.getSource().sendSuccess(() -> MapLoadManager.text("carpet.fga.map_load.list.empty"), false);
            return 1;
        }
        context.getSource().sendSuccess(() -> MapLoadManager.text("carpet.fga.map_load.list.header", lines.size()), false);
        for (Component line : lines) context.getSource().sendSystemMessage(line);
        return lines.size();
    }

    private static int control(CommandContext<CommandSourceStack> context, Action action, ServerPlayer target)
            throws CommandSyntaxException {
        ServerPlayer self = context.getSource().getPlayer();
        if (target == null && self == null) {
            context.getSource().sendFailure(MapLoadManager.text("carpet.fga.map_load.no_target"));
            return 0;
        }
        ServerPlayer owner = target == null ? self : target;
        if (target != null && target != self && !context.getSource().hasPermission(2)) {
            context.getSource().sendFailure(MapLoadManager.text("carpet.fga.map_load.control_denied"));
            return 0;
        }

        boolean changed = switch (action) {
            case PAUSE -> MapLoadManager.pause(owner);
            case RESUME -> MapLoadManager.resume(owner);
            case STOP -> MapLoadManager.stop(owner);
        };
        String name = owner.getGameProfile().getName();
        if (!changed) {
            context.getSource().sendFailure(MapLoadManager.text("carpet.fga.map_load.no_task", name));
            return 0;
        }

        String key = switch (action) {
            case PAUSE -> "carpet.fga.map_load.pause_done";
            case RESUME -> "carpet.fga.map_load.resume_done";
            case STOP -> "carpet.fga.map_load.stop_done";
        };
        context.getSource().sendSuccess(() -> MapLoadManager.text(key, name), false);
        return 1;
    }

    private static ItemStack heldMap(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof MapItem && main.has(DataComponents.MAP_ID)) return main;
        ItemStack offhand = player.getOffhandItem();
        return offhand.getItem() instanceof MapItem && offhand.has(DataComponents.MAP_ID) ? offhand : ItemStack.EMPTY;
    }
}
//#endif
