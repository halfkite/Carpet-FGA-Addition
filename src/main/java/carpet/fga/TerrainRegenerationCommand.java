//#if MC == 1.20.1 || MC >= 1.21 && MC <= 26.3
package carpet.fga;

import carpet.utils.CommandHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TerrainRegenerationCommand {
    private TerrainRegenerationCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("regenerateTerrain")
                .requires(source -> CommandHelper.canUseCommand(source, FGASettings.terrainRegenerationCommandPermission))
                .executes(TerrainRegenerationCommand::help)
                .then(Commands.literal("help").executes(TerrainRegenerationCommand::help))
                .then(Commands.literal("list").executes(c -> list(c, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(c -> list(c,
                                IntegerArgumentType.getInteger(c, "page")))))
                .then(preview("create", TerrainRegenerationManager.Type.REGENERATE))
                .then(preview("clear", TerrainRegenerationManager.Type.CLEAR))
                .then(Commands.literal("confirm").then(Commands.argument("task", StringArgumentType.word())
                        .suggests((c,b) -> suggestTasks(b, TerrainRegenerationManager.Status.DRAFT))
                        .executes(TerrainRegenerationCommand::confirm)))
                .then(Commands.literal("cancel").then(Commands.argument("task", StringArgumentType.word())
                        .suggests((c,b) -> suggestTasks(b, null)).executes(TerrainRegenerationCommand::cancel)))
                .then(Commands.literal("run").then(Commands.argument("task", StringArgumentType.word())
                        .suggests((c,b) -> suggestTasks(b, TerrainRegenerationManager.Status.CONFIRMED))
                        .executes(TerrainRegenerationCommand::run)))
                .then(Commands.literal("retry").then(Commands.argument("task", StringArgumentType.word())
                        .suggests((c,b) -> suggestTasks(b, TerrainRegenerationManager.Status.FAILED))
                        .executes(TerrainRegenerationCommand::retry))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> preview(
            String name, TerrainRegenerationManager.Type type) {
        return Commands.literal(name)
                .then(Commands.literal("from").then(boxArguments(type, false)))
                .then(Commands.literal("radius").then(radiusArguments(type, false)))
                .then(Commands.literal("dimension").then(Commands.argument("dimension", DimensionArgument.dimension())
                        .then(Commands.literal("from").then(boxArguments(type, true)))
                        .then(Commands.literal("radius").then(radiusArguments(type, true)))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> boxArguments(
            TerrainRegenerationManager.Type type, boolean dimension) {
        return coordinate("x1", true)
                .then(coordinate("z1", false)
                        .then(coordinate("x2", true)
                                .then(coordinate("z2", false)
                                        .executes(c -> draft(c, type, dimension,
                                                IntegerArgumentType.getInteger(c,"x1"), IntegerArgumentType.getInteger(c,"z1"),
                                                IntegerArgumentType.getInteger(c,"x2"), IntegerArgumentType.getInteger(c,"z2"))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> radiusArguments(
            TerrainRegenerationManager.Type type, boolean dimension) {
        return Commands.argument("range", IntegerArgumentType.integer(0, 64))
                .executes(c -> draftRadius(c, type, dimension, IntegerArgumentType.getInteger(c, "range")));
    }

    /** Radius counts chunks, centred on the chunk the player stands in. */
    private static int draftRadius(CommandContext<CommandSourceStack> context,
                                   TerrainRegenerationManager.Type type, boolean dimension, int range) {
        try {
            ServerPlayer player = context.getSource().getPlayer();
            if (player == null) {
                throw new IllegalArgumentException(
                        "Console must use the from form with coordinates / 控制台请使用 from 形式并给出坐标");
            }
            int chunkX = player.blockPosition().getX() >> 4;
            int chunkZ = player.blockPosition().getZ() >> 4;
            return draft(context, type, dimension,
                    (chunkX - range) << 4, (chunkZ - range) << 4,
                    ((chunkX + range) << 4) + 15, ((chunkZ + range) << 4) + 15);
        } catch (Exception e) {
            return fail(context, e);
        }
    }

    private static int draft(CommandContext<CommandSourceStack> context, TerrainRegenerationManager.Type type,
                             boolean dimension, int x1, int z1, int x2, int z2) {
        try {
            ResourceLocation id;
            if (dimension) {
                id = DimensionArgument.getDimension(context,"dimension").dimension().location();
            } else {
                if (context.getSource().getLevel() == null) {
                    throw new IllegalArgumentException("Console must specify dimension / 控制台必须指定维度");
                }
                id = context.getSource().getLevel().dimension().location();
            }
            String creator = context.getSource().getTextName();
            var task = TerrainRegenerationManager.draft(type,id,x1,z1,x2,z2,creator);
            String command = "/regenerateTerrain confirm " + task.id();
            MutableComponent out = Component.literal("地形任务预览 / Terrain task preview\n").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(typeLabel(task.type()) + " " + shortDimension(task.dimension())
                            + " 区块 [" + task.minChunkX() + "," + task.minChunkZ() + "]..["
                            + task.maxChunkX() + "," + task.maxChunkZ() + "] 共 " + task.chunks() + " 个\n")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("实际方块（方块坐标）[" + task.minBlockX() + "," + task.minBlockZ() + "]..["
                            + task.maxBlockX() + "," + task.maxBlockZ() + "]\n").withStyle(ChatFormatting.GRAY));
            // Chunks outside the world border are never rendered by the client, so say so up front.
            var sourceLevel = context.getSource().getLevel();
            if (sourceLevel != null) {
                var border = sourceLevel.getWorldBorder();
                if (task.minBlockX() < border.getMinX() || task.maxBlockX() > border.getMaxX()
                        || task.minBlockZ() < border.getMinZ() || task.maxBlockZ() > border.getMaxZ()) {
                    out.append(note("carpet.fga.terrain_regeneration.preview.outside_border",
                            ChatFormatting.RED)).append("\n");
                }
            }
            out.append(note(task.type() == TerrainRegenerationManager.Type.CLEAR
                    ? "carpet.fga.terrain_regeneration.preview.clear"
                    : "carpet.fga.terrain_regeneration.preview.regenerate", ChatFormatting.RED)).append("\n");
            action(out, "[点击确认并立即执行] / [CLICK TO CONFIRM AND RUN]", command,
                    "点击后确认并立即执行 / Click to confirm and run right away");
            context.getSource().sendSuccess(() -> out,false);
            return 1;
        } catch(Exception e){return fail(context,e);}
    }

    private static int confirm(CommandContext<CommandSourceStack> context) {
        try {
            UUID id = taskId(context);
            TerrainRegenerationManager.confirm(id);
            var task = TerrainRegenerationManager.run(id);
            context.getSource().sendSuccess(() -> Component.literal("Confirmed, running now / 已确认并开始执行\n"+describe(task)).withStyle(ChatFormatting.GREEN),true);
            return 1;
        } catch(Exception e){return fail(context,e);}
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        try {
            UUID id=taskId(context);
            var task = TerrainRegenerationManager.run(id);
            context.getSource().sendSuccess(() -> Component.literal("Running now / 开始执行\n"+describe(task)).withStyle(ChatFormatting.GREEN),true);
            return 1;
        } catch(Exception e){return fail(context,e);}
    }

    /** First eight characters of a task id, enough to recognise and to type. */
    private static String shortId(UUID id) { return id.toString().substring(0, 8); }

    /** Accepts a full task id or any unambiguous prefix, so the short id from the list works. */
    private static UUID taskId(CommandContext<CommandSourceStack> context) {
        String raw = StringArgumentType.getString(context, "task").trim().toLowerCase(java.util.Locale.ROOT);
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
        }
        List<UUID> matches = new java.util.ArrayList<>();
        for (TerrainRegenerationManager.Task task : TerrainRegenerationManager.tasks()) {
            if (task.id().toString().startsWith(raw)) matches.add(task.id());
        }
        if (matches.size() == 1) return matches.get(0);
        if (matches.isEmpty()) throw new IllegalArgumentException("task not found / 任务不存在");
        throw new IllegalArgumentException("task id is ambiguous / 任务编号不唯一");
    }

    private static int cancel(CommandContext<CommandSourceStack> context) {
        try {
            UUID id=taskId(context);
            if(!TerrainRegenerationManager.cancel(id)){context.getSource().sendFailure(Component.literal("Task not found or already executed / 任务不存在或已执行"));return 0;}
            context.getSource().sendSuccess(() -> Component.literal("Task cancelled / 已取消任务").withStyle(ChatFormatting.GREEN),true);
            return 1;
        } catch(Exception e){return fail(context,e);}
    }

    private static int retry(CommandContext<CommandSourceStack> context) {
        try {
            var task = TerrainRegenerationManager.retry(taskId(context));
            context.getSource().sendSuccess(() -> Component.literal("Task queued for retry / 任务已加入重试队列\n"+describe(task)).withStyle(ChatFormatting.GREEN),true);
            return 1;
        } catch(Exception e){return fail(context,e);}
    }

    private static int list(CommandContext<CommandSourceStack> context,int page) {
        List<TerrainRegenerationManager.Task> tasks=TerrainRegenerationManager.tasks(); int per=8,pages=Math.max(1,(tasks.size()+per-1)/per);
        if(page>pages) page=pages; MutableComponent out=Component.literal("Terrain tasks / 地形任务 "+page+"/"+pages+"\n").withStyle(ChatFormatting.GOLD);
        int from=(page-1)*per,to=Math.min(tasks.size(),from+per);
        for(int i=from;i<to;i++){
            var t=tasks.get(i);
            out.append(Component.literal(describe(t) + "\n").withStyle(ChatFormatting.GRAY));
            switch (t.status()) {
                case DRAFT -> action(out, "[点击确认并加入重启队列] / [CLICK TO CONFIRM]",
                        "/regenerateTerrain confirm " + t.id(),
                        "点击后立即确认任务，服务器下次重启执行 / Click to confirm now; runs on next server restart");
                case CONFIRMED -> {
                    action(out, "[点击立即执行] / [CLICK TO RUN NOW]",
                            "/regenerateTerrain run " + t.id(),
                            "立即在线执行，不需要重启服务器 / Apply right now, no restart needed");
                    action(out, "[点击取消] / [CLICK TO CANCEL]",
                            "/regenerateTerrain cancel " + t.id(),
                            "点击后取消该任务 / Click to cancel this task");
                }
                case RUNNING -> {
                    int[] progress = TerrainRegenerationManager.liveProgress(t.id());
                    out.append(Component.literal(progress == null ? "" :
                            "progress " + progress[0] + "/" + progress[1] + " chunks, in flight " + progress[2]
                                    + ", still loaded " + progress[3] + "\n").withStyle(ChatFormatting.YELLOW));
                    action(out, "[点击取消执行中的任务] / [CLICK TO CANCEL]",
                            "/regenerateTerrain cancel " + t.id(),
                            "点击后停止本次在线执行 / Click to stop this live run");
                }
                case FAILED -> action(out, "[点击重新加入重试队列] / [CLICK TO RETRY]",
                        "/regenerateTerrain retry " + t.id(),
                        "点击后使用已有备份在下次重启重试 / Click to retry from the existing backup on next restart");
                default -> {
                }
            }
        }
        context.getSource().sendSuccess(() -> out,false); return tasks.size();
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        MutableComponent out = Component.empty();
        out.append(note("carpet.fga.terrain_regeneration.help.title", ChatFormatting.GOLD)).append("\n\n");
        helpLine(out, "/regenerateTerrain create from <x1> <z1> <x2> <z2>", "help.regenerate_box");
        helpLine(out, "/regenerateTerrain create radius <range>", "help.regenerate_radius");
        helpLine(out, "/regenerateTerrain clear from <x1> <z1> <x2> <z2>", "help.clear_box");
        helpLine(out, "/regenerateTerrain clear radius <range>", "help.clear_radius");
        out.append("\n");
        helpLine(out, "/regenerateTerrain list [page]", "help.list");
        helpLine(out, "/regenerateTerrain confirm <task>", "help.confirm");
        helpLine(out, "/regenerateTerrain cancel <task>", "help.cancel");
        out.append("\n").append(note("carpet.fga.terrain_regeneration.help.note", ChatFormatting.YELLOW));
        context.getSource().sendSuccess(() -> out, false);
        return 1;
    }

    /** Command syntax stays literal (it is the same in every language); the note is translated client side. */
    private static void helpLine(MutableComponent out, String command, String noteKey) {
        // Clicking fills in only the fixed part: the <...> placeholders would otherwise be typed into
        // the chat bar and have to be deleted again.
        int placeholder = command.indexOf('<');
        String suggest = placeholder < 0 ? command : command.substring(0, placeholder);
        out.append(Component.literal(command).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                .withClickEvent(FgaClickEvents.suggestCommand(suggest))));
        out.append(note("carpet.fga.terrain_regeneration." + noteKey, ChatFormatting.GOLD)).append("\n");
    }

    private static MutableComponent note(String key, ChatFormatting color) {
        return Component.literal("  ").append(FGAText.text(key)).withStyle(color);
    }

    /** minecraft:overworld -> overworld, so the task lines stay short. */
    private static String shortDimension(String dimension) {
        int colon = dimension.indexOf(':');
        return colon < 0 ? dimension : dimension.substring(colon + 1);
    }

    private static String describe(TerrainRegenerationManager.Task t){return shortId(t.id())+" "+t.type()+" "+shortDimension(t.dimension())+" chunks ["+t.minChunkX()+","+t.minChunkZ()+"]..["+t.maxChunkX()+","+t.maxChunkZ()+"] x"+t.chunks()+" "+t.status();}
    private static void line(MutableComponent out,String command,String note){out.append(Component.literal(command).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withClickEvent(FgaClickEvents.suggestCommand(command)))).append(Component.literal("  # "+note+"\n").withStyle(ChatFormatting.GOLD));}
    private static void action(MutableComponent out, String label, String command, String hover) {
        out.append(Component.literal(label).withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                .withClickEvent(FgaClickEvents.runCommand(command))
                .withHoverEvent(
                        //#if MC >= 1.21.5
                        //$$ new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(hover))
                        //#else
                        new net.minecraft.network.chat.HoverEvent(
                                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal(hover))
                        //#endif
                ))).append("\n");
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> coordinate(String name, boolean xAxis) {
        return Commands.argument(name, IntegerArgumentType.integer()).suggests((context, builder) -> suggestCoordinates(context.getSource(), builder, xAxis));
    }
    private static CompletableFuture<Suggestions> suggestCoordinates(CommandSourceStack source, SuggestionsBuilder builder, boolean xAxis) {
        ServerPlayer player = source.getEntity() instanceof ServerPlayer value ? value : null;
        if (player == null) return builder.buildFuture();
        int self = xAxis ? player.blockPosition().getX() : player.blockPosition().getZ();
        builder.suggest(self, Component.literal(xAxis ? "自身 X / your X" : "自身 Z / your Z"));
        HitResult hit = player.pick(64.0D, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            int target = xAxis ? blockHit.getBlockPos().getX() : blockHit.getBlockPos().getZ();
            if (target != self) builder.suggest(target, Component.literal(xAxis ? "指向方块 X / targeted block X" : "指向方块 Z / targeted block Z"));
        }
        return builder.buildFuture();
    }
    private static String typeLabel(TerrainRegenerationManager.Type type) {
        return type == TerrainRegenerationManager.Type.CLEAR ? "清空为空气 / CLEAR TO AIR" : "正常地形重生成 / REGENERATE TERRAIN";
    }
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTasks(com.mojang.brigadier.suggestion.SuggestionsBuilder b,TerrainRegenerationManager.Status status){for(var t:TerrainRegenerationManager.tasks())if(status==null||t.status()==status)b.suggest(t.id().toString());return b.buildFuture();}
    private static int fail(CommandContext<CommandSourceStack> c,Exception e){c.getSource().sendFailure(Component.literal(e.getMessage()==null?e.toString():e.getMessage()));return 0;}
}
//#endif
