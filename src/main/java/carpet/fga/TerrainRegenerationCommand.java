//#if MC >= 1.21 && MC <= 26.2
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
                .then(preview("regenerate", TerrainRegenerationManager.Type.REGENERATE))
                .then(preview("clear", TerrainRegenerationManager.Type.CLEAR))
                .then(Commands.literal("confirm").then(Commands.argument("task", StringArgumentType.word())
                        .suggests((c,b) -> suggestTasks(b, TerrainRegenerationManager.Status.DRAFT))
                        .executes(TerrainRegenerationCommand::confirm)))
                .then(Commands.literal("cancel").then(Commands.argument("task", StringArgumentType.word())
                        .suggests((c,b) -> suggestTasks(b, null)).executes(TerrainRegenerationCommand::cancel)))
                .then(Commands.literal("retry").then(Commands.argument("task", StringArgumentType.word())
                        .suggests((c,b) -> suggestTasks(b, TerrainRegenerationManager.Status.FAILED))
                        .executes(TerrainRegenerationCommand::retry))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> preview(
            String name, TerrainRegenerationManager.Type type) {
        return Commands.literal(name)
                .then(Commands.literal("box").then(boxArguments(type, false)))
                .then(Commands.literal("radius").then(radiusArguments(type, false)))
                .then(Commands.literal("dimension").then(Commands.argument("dimension", DimensionArgument.dimension())
                        .then(Commands.literal("box").then(boxArguments(type, true)))
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
        return coordinate("x", true)
                .then(coordinate("z", false)
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0))
                                .executes(c -> {
                                    int x=IntegerArgumentType.getInteger(c,"x"), z=IntegerArgumentType.getInteger(c,"z"), r=IntegerArgumentType.getInteger(c,"radius");
                                    return draft(c,type,dimension,x-r,z-r,x+r,z+r);
                                })));
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
                    .append(Component.literal(typeLabel(task.type()) + "  维度 / Dimension: " + task.dimension() + "\n").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("区块 / Chunks: [" + task.minChunkX() + ", " + task.minChunkZ() + "] -> ["
                            + task.maxChunkX() + ", " + task.maxChunkZ() + "]  共 " + task.chunks() + " 个 / total\n").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("实际方块范围 / Effective blocks: [" + task.minBlockX() + ", " + task.minBlockZ()
                            + "] -> [" + task.maxBlockX() + ", " + task.maxBlockZ() + "]\n").withStyle(ChatFormatting.GRAY));
            if (task.type() == TerrainRegenerationManager.Type.CLEAR) {
                out.append(Component.literal("清空完整区块，并清除水平外沿 8 格流体 / Clears whole chunks and fluids eight blocks outside the horizontal border\n")
                        .withStyle(ChatFormatting.RED));
            } else {
                out.append(Component.literal("删除旧区块并在下次重启按正常地形重新生成 / Deletes old chunks and regenerates normal terrain on next restart\n")
                        .withStyle(ChatFormatting.RED));
            }
            action(out, "[点击确认并加入重启队列] / [CLICK TO CONFIRM]", command,
                    "点击后立即确认任务，服务器下次重启执行 / Click to confirm now; runs on next server restart");
            context.getSource().sendSuccess(() -> out,false);
            return 1;
        } catch(Exception e){return fail(context,e);}
    }

    private static int confirm(CommandContext<CommandSourceStack> context) {
        try {
            var task=TerrainRegenerationManager.confirm(UUID.fromString(StringArgumentType.getString(context,"task")));
            context.getSource().sendSuccess(() -> Component.literal("Queued for next restart / 已加入下次重启队列\n"+describe(task)).withStyle(ChatFormatting.GREEN),true);
            return 1;
        } catch(Exception e){return fail(context,e);}
    }

    private static int cancel(CommandContext<CommandSourceStack> context) {
        try {
            UUID id=UUID.fromString(StringArgumentType.getString(context,"task"));
            if(!TerrainRegenerationManager.cancel(id)){context.getSource().sendFailure(Component.literal("Task not found or already executed / 任务不存在或已执行"));return 0;}
            context.getSource().sendSuccess(() -> Component.literal("Task cancelled / 已取消任务").withStyle(ChatFormatting.GREEN),true);
            return 1;
        } catch(Exception e){return fail(context,e);}
    }

    private static int retry(CommandContext<CommandSourceStack> context) {
        try {
            var task = TerrainRegenerationManager.retry(UUID.fromString(StringArgumentType.getString(context,"task")));
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
                case CONFIRMED -> action(out, "[点击取消待执行任务] / [CLICK TO CANCEL]",
                        "/regenerateTerrain cancel " + t.id(),
                        "点击后立即从重启队列取消 / Click to remove this task from the restart queue");
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
        MutableComponent out=Component.literal("地形重生成与清空 / Terrain regeneration and clearing\n").withStyle(ChatFormatting.GOLD);
        line(out,"/regenerateTerrain regenerate box ","框选正常地形重生成，坐标按完整区块向外取整 / normal terrain regeneration box");
        line(out,"/regenerateTerrain clear radius ","按半径清空为空气，并移除水平外沿 8 格流体 / clear to air and remove an 8-block fluid border");
        line(out,"/regenerateTerrain regenerate dimension minecraft:the_nether box ","指定维度，控制台必须使用此形式 / select dimension; required from console");
        line(out,"/regenerateTerrain list","查看草稿、待执行、完成和失败任务 / list drafts, queued, complete, and failed tasks");
        line(out,"/regenerateTerrain retry ","使用已有备份重试失败任务 / retry a failed task with its existing backup");
        out.append(Component.literal("坐标参数可按 Tab 补全自身位置或视线指向方块，预览后点击确认，下次重启才会修改世界\n"
                + "Coordinate arguments suggest your position and targeted block; preview, click confirm, then restart to apply")
                .withStyle(ChatFormatting.YELLOW));
        context.getSource().sendSuccess(() -> out,false); return 1;
    }

    private static String describe(TerrainRegenerationManager.Task t){return t.type()+" "+t.dimension()+" chunks ["+t.minChunkX()+","+t.minChunkZ()+"]..["+t.maxChunkX()+","+t.maxChunkZ()+"] blocks ["+t.minBlockX()+","+t.minBlockZ()+"]..["+t.maxBlockX()+","+t.maxBlockZ()+"] count="+t.chunks()+" status="+t.status();}
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
