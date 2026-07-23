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
                        .then(collection(VillagerPerformanceConfig.Target.GIFT, VillagerPerformanceConfig.Kind.NAME))
                        .then(collection(VillagerPerformanceConfig.Target.GIFT, VillagerPerformanceConfig.Kind.BLOCK))));
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
        try { VillagerPerformanceConfig.setTradeMode(VillagerPerformanceConfig.parseMode(value)); return ok(c,"trade mode = "+value); }
        catch (IOException e) { return error(c,e.getMessage()); }
    }
    private static int setGift(CommandContext<CommandSourceStack> c, boolean value) {
        try { VillagerPerformanceConfig.setGiftEnabled(value); return ok(c,"gift = "+value); }
        catch (IOException e) { return error(c,e.getMessage()); }
    }
    private static int change(CommandContext<CommandSourceStack> c, VillagerPerformanceConfig.Target target,
                              VillagerPerformanceConfig.Kind kind, boolean add) {
        String value=StringArgumentType.getString(c,"value");
        try {
            boolean changed=add?VillagerPerformanceConfig.add(target,kind,value):VillagerPerformanceConfig.remove(target,kind,value);
            if(!changed)return error(c,(add?"already exists: ":"not found: ")+value);
            return ok(c,(add?"added ":"removed ")+value);
        } catch (RuntimeException|IOException e) { return error(c,e.getMessage()); }
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        VillagerPerformanceConfig.State s=VillagerPerformanceConfig.snapshot(); MutableComponent m=Component.literal("");
        m.append(label("Villager performance\n")).append(line("permission",FGASettings.villagerPerformanceOptimization));
        m.append(line("trade mode",s.tradeMode().serialized())).append(line("trade names",s.tradeNames().size()+""))
                .append(line("trade blocks",s.tradeBlocks().size()+""));
        m.append(line("hero gift",s.giftEnabled()+"")).append(line("gift names",s.giftNames().size()+""))
                .append(line("gift blocks",s.giftBlocks().size()+""));
        c.getSource().sendSuccess(() -> m,false); return 1;
    }

    private static int help(CommandContext<CommandSourceStack> c) {
        MutableComponent m=label("Villager performance help\n");
        m.append(helpLine("/villagerPerformance status","show current configuration"));
        m.append(helpLine("/villagerPerformance trade false|ai|static","set trade optimization mode"));
        m.append(helpLine("/villagerPerformance trade name add ","add a trade custom-name condition"));
        m.append(helpLine("/villagerPerformance trade block add minecraft:","add a trade block condition"));
        m.append(helpLine("/villagerPerformance gift false|true","toggle gifts to Hero of the Village players"));
        m.append(helpLine("/villagerPerformance gift name add ","add a hero-gift custom-name condition"));
        m.append(helpLine("/villagerPerformance gift block add minecraft:","add a hero-gift block condition"));
        m.append(helpLine("/villagerPerformance trade name list","list trade names; block also supported"));
        m.append(helpLine("/villagerPerformance gift name list","list gift names; block also supported"));
        c.getSource().sendSuccess(() -> m,false); return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c, VillagerPerformanceConfig.Target target,
                            VillagerPerformanceConfig.Kind kind, int page) {
        List<String> values = kind==VillagerPerformanceConfig.Kind.NAME
                ? new ArrayList<>(VillagerPerformanceConfig.names(target))
                : VillagerPerformanceConfig.blocks(target).stream().map(ResourceLocation::toString).toList();
        values.sort(String::compareTo); int pages=Math.max(1,(values.size()+PAGE_SIZE-1)/PAGE_SIZE);
        if(page>pages)return error(c,"page out of range: "+page+"/"+pages);
        MutableComponent m=label(target.name().toLowerCase(Locale.ROOT)+" "+kind.name().toLowerCase(Locale.ROOT)+" "+page+"/"+pages+"\n");
        String root="/villagerPerformance "+target.name().toLowerCase(Locale.ROOT)+" "+kind.name().toLowerCase(Locale.ROOT);
        for(int i=(page-1)*PAGE_SIZE;i<Math.min(values.size(),page*PAGE_SIZE);i++){
            String v=values.get(i); m.append(Component.literal(v+" ").withStyle(ChatFormatting.GRAY));
            m.append(Component.literal("[-]").withStyle(style(ChatFormatting.RED,ClickEvent.Action.RUN_COMMAND,root+" remove "+v,"click to remove"))).append("\n");
        }
        if(page>1)m.append(click("[<]",root+" list "+(page-1))).append(" ");
        if(page<pages)m.append(click("[>]",root+" list "+(page+1)));
        c.getSource().sendSuccess(() -> m,false);return 1;
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> c, SuggestionsBuilder b,
                                                           VillagerPerformanceConfig.Target target,
                                                           VillagerPerformanceConfig.Kind kind, boolean existing) {
        if(existing){Collection<String> values=kind==VillagerPerformanceConfig.Kind.NAME?VillagerPerformanceConfig.names(target):VillagerPerformanceConfig.blocks(target).stream().map(ResourceLocation::toString).toList();return SharedSuggestionProvider.suggest(values,b);}
        if(kind==VillagerPerformanceConfig.Kind.BLOCK)return SharedSuggestionProvider.suggestResource(net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet(),b);
        return b.buildFuture();
    }
    private static MutableComponent helpLine(String command,String comment){return Component.literal(command).withStyle(style(ChatFormatting.GRAY,ClickEvent.Action.SUGGEST_COMMAND,command,"click to insert command")).append(Component.literal("  # "+comment+"\n").withStyle(ChatFormatting.GOLD));}
    private static MutableComponent line(String k,String v){return Component.literal(k+": ").withStyle(ChatFormatting.GOLD).append(Component.literal(v+"\n").withStyle(ChatFormatting.GRAY));}
    private static MutableComponent label(String text){return Component.literal(text).withStyle(ChatFormatting.GOLD);}
    private static MutableComponent click(String text,String command){return Component.literal(text).withStyle(style(ChatFormatting.GRAY,ClickEvent.Action.RUN_COMMAND,command,"change page"));}
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
