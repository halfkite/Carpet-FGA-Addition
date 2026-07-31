//#if MC >= 1.21.1
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
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class VillagerPerformanceCommand {
    private static final int PAGE_SIZE = 10;
    private VillagerPerformanceCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("villagerPerformance")
                .requires(source -> CommandHelper.canUseCommand(source, FGASettings.villagerPerformanceOptimization))
                .executes(VillagerPerformanceCommand::help)
                .then(Commands.literal("help").executes(VillagerPerformanceCommand::help))
                .then(Commands.literal("status").executes(VillagerPerformanceCommand::status))
                .then(Commands.literal("trade")
                        .then(Commands.literal("false").executes(c -> setMode(c, "false")))
                        .then(Commands.literal("ai").executes(c -> setMode(c, "ai")))
                        .then(Commands.literal("static").executes(c -> setMode(c, "static")))
                        .then(collection(VillagerPerformanceConfig.Target.TRADE, VillagerPerformanceConfig.Kind.NAME))
                        .then(collection(VillagerPerformanceConfig.Target.TRADE, VillagerPerformanceConfig.Kind.BLOCK)))
                .then(Commands.literal("gift")
                        .then(Commands.literal("false").executes(c -> setGift(c, false)))
                        .then(Commands.literal("true").executes(c -> setGift(c, true)))
                        .then(Commands.literal("list").executes(c -> giftList(c, 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(c -> giftList(c, IntegerArgumentType.getInteger(c, "page")))))
                        .then(collection(VillagerPerformanceConfig.Target.GIFT, VillagerPerformanceConfig.Kind.NAME))
                        .then(collection(VillagerPerformanceConfig.Target.GIFT, VillagerPerformanceConfig.Kind.BLOCK)))
                .then(Commands.literal("wanderingTrader")
                        .then(Commands.literal("false").executes(c -> setWanderingTraderMode(c, "false")))
                        .then(Commands.literal("true").executes(c -> setWanderingTraderMode(c, "true")))
                        .then(Commands.literal("controlled").executes(c -> setWanderingTraderMode(c, "controlled")))
                        .then(wanderingTraderCollection(VillagerPerformanceConfig.Kind.NAME))
                        .then(wanderingTraderCollection(VillagerPerformanceConfig.Kind.BLOCK))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> collection(
            VillagerPerformanceConfig.Target target, VillagerPerformanceConfig.Kind kind) {
        String literal = kind == VillagerPerformanceConfig.Kind.NAME ? "name" : "block";
        return Commands.literal(literal)
                .then(Commands.literal("add").then(Commands.argument("value", StringArgumentType.greedyString())
                        .suggests((c,b) -> suggest(c,b,target,kind,false)).executes(c -> change(c,target,kind,true))))
                .then(Commands.literal("remove").then(Commands.argument("value", StringArgumentType.greedyString())
                        .suggests((c,b) -> suggest(c,b,target,kind,true)).executes(c -> change(c,target,kind,false))))
                .then(Commands.literal("list").executes(c -> list(c,target,kind,1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(c -> list(c,target,kind,IntegerArgumentType.getInteger(c,"page")))));
    }

    private static int setMode(CommandContext<CommandSourceStack> c, String value) {
        try { VillagerPerformanceConfig.setTradeMode(VillagerPerformanceConfig.parseMode(value)); return ok(c,"交易优化模式已设置为："+value); }
        catch (IOException e) { return error(c,e.getMessage()); }
    }
    private static int setGift(CommandContext<CommandSourceStack> c, boolean value) {
        try {
            VillagerPerformanceConfig.setGiftEnabled(value);
            if (!value) return ok(c, "村庄英雄赠礼已关闭。");
            VillagerPerformanceConfig.State state = VillagerPerformanceConfig.snapshot();
            if (state.giftNames().isEmpty() && state.giftBlocks().isEmpty()) {
                return ok(c, "村庄英雄赠礼已开启，但赠礼名单为空；请使用 /villagerPerformance gift name|block add <值> 选择村民。");
            }
            return ok(c, "村庄英雄赠礼已开启。命中名单的村民完全跳过 AI，每 60 tick 检查一次；玩家需进入约 5 格投掷范围，首次可立即赠礼，之后沿用原版 600-6600 tick 冷却。");
        }
        catch (IOException e) { return error(c,e.getMessage()); }
    }
    private static int setWanderingTraderMode(CommandContext<CommandSourceStack> c, String value) {
        FGASettings.wanderingTraderNoDespawn = value;
        return ok(c, "流浪商人不消失模式已设置为 " + value);
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> wanderingTraderCollection(VillagerPerformanceConfig.Kind kind) {
        String literal = kind == VillagerPerformanceConfig.Kind.NAME ? "name" : "block";
        return Commands.literal(literal)
                .then(Commands.literal("add").then(Commands.argument("value", StringArgumentType.greedyString()).executes(c -> changeWanderingTrader(c, kind, true))))
                .then(Commands.literal("remove").then(Commands.argument("value", StringArgumentType.greedyString()).executes(c -> changeWanderingTrader(c, kind, false))))
                .then(Commands.literal("list").executes(c -> listWanderingTrader(c, kind)));
    }
    private static int changeWanderingTrader(CommandContext<CommandSourceStack> c, VillagerPerformanceConfig.Kind kind, boolean add) {
        String value = StringArgumentType.getString(c, "value");
        try {
            boolean changed = add ? VillagerPerformanceConfig.addWanderingTrader(kind, value) : VillagerPerformanceConfig.removeWanderingTrader(kind, value);
            return changed ? ok(c, (add ? "已添加：" : "已移除：") + value) : error(c, (add ? "已存在：" : "未找到：") + value);
        } catch (RuntimeException | IOException exception) { return error(c, exception.getMessage()); }
    }
    private static int listWanderingTrader(CommandContext<CommandSourceStack> c, VillagerPerformanceConfig.Kind kind) {
        Collection<String> values = kind == VillagerPerformanceConfig.Kind.NAME ? VillagerPerformanceConfig.wanderingTraderNames() : VillagerPerformanceConfig.wanderingTraderBlocks().stream().map(ResourceLocation::toString).toList();
        c.getSource().sendSuccess(() -> Component.literal("流浪商人" + (kind == VillagerPerformanceConfig.Kind.NAME ? "名称" : "方块") + "名单：" + String.join(", ", values)).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
    private static int change(CommandContext<CommandSourceStack> c, VillagerPerformanceConfig.Target target,
                              VillagerPerformanceConfig.Kind kind, boolean add) {
        String value=StringArgumentType.getString(c,"value");
        try {
            boolean changed=add?VillagerPerformanceConfig.add(target,kind,value):VillagerPerformanceConfig.remove(target,kind,value);
            if(!changed)return error(c,(add?"已存在：":"未找到：")+value);
            return ok(c,(add?"已添加：":"已移除：")+value);
        } catch (RuntimeException|IOException e) { return error(c,e.getMessage()); }
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        VillagerPerformanceConfig.State s=VillagerPerformanceConfig.snapshot(); MutableComponent m=Component.literal("");
        m.append(label("村民性能优化\n")).append(line("命令权限",FGASettings.villagerPerformanceOptimization));
        m.append(line("交易优化模式",s.tradeMode().serialized())).append(line("交易名称名单",s.tradeNames().size()+""))
                .append(line("交易方块名单",s.tradeBlocks().size()+""));
        m.append(line("村庄英雄赠礼",s.giftEnabled()?"开启":"关闭")).append(line("赠礼名称名单",s.giftNames().size()+""))
                .append(line("赠礼方块名单",s.giftBlocks().size()+""));
        m.append(line("流浪商人不消失",FGASettings.wanderingTraderNoDespawn)).append(line("流浪商人名称名单",s.wanderingTraderNames().size()+""))
                .append(line("流浪商人方块名单",s.wanderingTraderBlocks().size()+""));
        c.getSource().sendSuccess(() -> m,false); return 1;
    }

    private static int help(CommandContext<CommandSourceStack> c) {
        MutableComponent m=label("村民性能优化帮助\n");
        m.append(helpLine("/villagerPerformance status","查看当前配置"));
        m.append(helpLine("/villagerPerformance trade false|ai|static","设置交易优化模式"));
        m.append(helpLine("/villagerPerformance trade name add ","添加交易优化的命名牌名称条件"));
        m.append(helpLine("/villagerPerformance trade block add minecraft:","添加交易优化的脚下方块条件"));
        m.append(helpLine("/villagerPerformance gift false|true","开启或关闭村庄英雄赠礼专用模式；完全跳过 AI，每 60 tick 检查，玩家需靠近约 5 格"));
        m.append(helpLine("/villagerPerformance gift name add ","添加可赠礼村民的命名牌名称条件"));
        m.append(helpLine("/villagerPerformance gift block add minecraft:","添加可赠礼村民的脚下方块条件"));
        m.append(helpLine("/villagerPerformance wanderingTrader false|true|controlled","设置流浪商人不消失模式"));
        m.append(helpLine("/villagerPerformance wanderingTrader name|block add ","添加 controlled 模式的流浪商人名称或脚下方块条件"));
        m.append(helpLine("/villagerPerformance trade name|block list","分页查看交易优化名单"));
        m.append(helpLine("/villagerPerformance gift list","分页查看全部赠礼名单"));
        c.getSource().sendSuccess(() -> m,false); return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c, VillagerPerformanceConfig.Target target,
                            VillagerPerformanceConfig.Kind kind, int page) {
        List<String> values = new ArrayList<>(kind==VillagerPerformanceConfig.Kind.NAME
                ? new ArrayList<>(VillagerPerformanceConfig.names(target))
                : VillagerPerformanceConfig.blocks(target).stream().map(ResourceLocation::toString).toList());
        values.sort(String::compareTo); int pages=Math.max(1,(values.size()+PAGE_SIZE-1)/PAGE_SIZE);
        if(page>pages)return error(c,"页码超出范围："+page+"/"+pages);
        String targetName = target == VillagerPerformanceConfig.Target.TRADE ? "交易优化" : "村庄英雄赠礼";
        String kindName = kind == VillagerPerformanceConfig.Kind.NAME ? "名称" : "方块";
        MutableComponent m=label(targetName+kindName+"名单 "+page+"/"+pages+"\n");
        String root="/villagerPerformance "+target.name().toLowerCase(Locale.ROOT)+" "+kind.name().toLowerCase(Locale.ROOT);
        for(int i=(page-1)*PAGE_SIZE;i<Math.min(values.size(),page*PAGE_SIZE);i++){
            String v=values.get(i); m.append(Component.literal(v+" ").withStyle(ChatFormatting.GRAY));
            m.append(Component.literal("[-]").withStyle(style(ChatFormatting.RED,ClickEvent.Action.RUN_COMMAND,root+" remove "+v,"click to remove"))).append("\n");
        }
        if(page>1)m.append(click("[<]",root+" list "+(page-1))).append(" ");
        if(page<pages)m.append(click("[>]",root+" list "+(page+1)));
        c.getSource().sendSuccess(() -> m,false);return 1;
    }

    private static int giftList(CommandContext<CommandSourceStack> c, int page) {
        List<GiftEntry> entries = new ArrayList<>();
        VillagerPerformanceConfig.names(VillagerPerformanceConfig.Target.GIFT).stream().sorted()
                .forEach(value -> entries.add(new GiftEntry("名称", value, "name")));
        VillagerPerformanceConfig.blocks(VillagerPerformanceConfig.Target.GIFT).stream().map(ResourceLocation::toString).sorted()
                .forEach(value -> entries.add(new GiftEntry("方块", value, "block")));
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) return error(c, "页码超出范围：" + page + "/" + pages);
        MutableComponent m = label("村庄英雄赠礼名单 " + page + "/" + pages + "\n");
        if (entries.isEmpty()) m.append(Component.literal("名单为空；gift true 不会匹配任何村民。\n").withStyle(ChatFormatting.GRAY));
        for (int i = (page - 1) * PAGE_SIZE; i < Math.min(entries.size(), page * PAGE_SIZE); i++) {
            GiftEntry entry = entries.get(i);
            m.append(Component.literal(entry.kind() + "：" + entry.value() + " ").withStyle(ChatFormatting.GRAY));
            String command = "/villagerPerformance gift " + entry.commandKind() + " remove " + entry.value();
            m.append(Component.literal("[-]").withStyle(style(ChatFormatting.RED, ClickEvent.Action.RUN_COMMAND, command, "点击移除"))).append("\n");
        }
        if (page > 1) m.append(click("[<]", "/villagerPerformance gift list " + (page - 1))).append(" ");
        if (page < pages) m.append(click("[>]", "/villagerPerformance gift list " + (page + 1)));
        c.getSource().sendSuccess(() -> m, false);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> c, SuggestionsBuilder b,
                                                           VillagerPerformanceConfig.Target target,
                                                           VillagerPerformanceConfig.Kind kind, boolean existing) {
        if(existing){Collection<String> values=kind==VillagerPerformanceConfig.Kind.NAME?VillagerPerformanceConfig.names(target):VillagerPerformanceConfig.blocks(target).stream().map(ResourceLocation::toString).toList();return SharedSuggestionProvider.suggest(values,b);}
        if(kind==VillagerPerformanceConfig.Kind.BLOCK)return SharedSuggestionProvider.suggestResource(net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet(),b);
        return b.buildFuture();
    }
    private record GiftEntry(String kind, String value, String commandKind) {}

    private static MutableComponent helpLine(String command,String comment){return Component.literal(command).withStyle(style(ChatFormatting.GRAY,ClickEvent.Action.SUGGEST_COMMAND,command,"点击填入指令")).append(Component.literal("  # "+comment+"\n").withStyle(ChatFormatting.GOLD));}
    private static MutableComponent line(String k,String v){return Component.literal(k+": ").withStyle(ChatFormatting.GOLD).append(Component.literal(v+"\n").withStyle(ChatFormatting.GRAY));}
    private static MutableComponent label(String text){return Component.literal(text).withStyle(ChatFormatting.GOLD);}
    private static MutableComponent click(String text,String command){return Component.literal(text).withStyle(style(ChatFormatting.GRAY,ClickEvent.Action.RUN_COMMAND,command,"切换页码"));}
    private static Style style(ChatFormatting color,ClickEvent.Action action,String value,String hover){
        return Style.EMPTY.withColor(color).withClickEvent(
                //#if MC >= 1.21.5
                //$$ action == ClickEvent.Action.RUN_COMMAND ? new ClickEvent.RunCommand(value) : new ClickEvent.SuggestCommand(value)
                //#else
                new ClickEvent(action,value)
                //#endif
        ).withHoverEvent(
                //#if MC >= 1.21.5
                //$$ new HoverEvent.ShowText(Component.literal(hover))
                //#else
                new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal(hover))
                //#endif
        );
    }
    private static int ok(CommandContext<CommandSourceStack> c,String text){c.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GREEN),false);return 1;}
    private static int error(CommandContext<CommandSourceStack> c,String text){c.getSource().sendFailure(Component.literal(text==null?"operation failed":text));return 0;}
}
//#endif
