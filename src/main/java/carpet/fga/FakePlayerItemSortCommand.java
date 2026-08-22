//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga;

import carpet.CarpetSettings;
import carpet.utils.CommandHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.*;

public final class FakePlayerItemSortCommand {
    private FakePlayerItemSortCommand() {}
    private static final int PAGE=10;
    private static final long REBUILD_CONFIRM_MS = 30_000L;
    private static final Map<String, PendingRebuild> PENDING_REBUILDS = new HashMap<>();
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){dispatcher.register(root("fakePlayerItemSort"));}
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name){
        com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).requires(s->CommandHelper.canUseCommand(s,CarpetSettings.commandPlayer))
            .executes(FakePlayerItemSortCommand::status).then(Commands.literal("help").executes(FakePlayerItemSortCommand::help)).then(Commands.literal("status").executes(FakePlayerItemSortCommand::status))
            .then(Commands.literal("mode").then(Commands.literal("summon").executes(c->mode(c,"summon"))).then(Commands.literal("quickopen").executes(c->mode(c,"quickopen"))))
            .then(Commands.literal("setting").then(Commands.argument("key",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(settingKeys(),b)).then(Commands.argument("value",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(settingValues(),b)).executes(FakePlayerItemSortCommand::setting))))
            .then(Commands.literal("whitelist").then(Commands.literal("add").then(Commands.argument("player",StringArgumentType.word()).executes(c->whitelist(c,true))))
                    .then(Commands.literal("remove").then(Commands.argument("player",StringArgumentType.word()).executes(c->whitelist(c,false))))
                    .then(Commands.literal("list").executes(c->whitelistList(c,1)).then(Commands.argument("page",IntegerArgumentType.integer(1)).executes(c->whitelistList(c,IntegerArgumentType.getInteger(c,"page"))))))
            .then(Commands.literal("format").then(Commands.literal("prefix").then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->format(c,true))))
                    .then(Commands.literal("suffix").then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->format(c,false))))
                    .then(Commands.literal("status").executes(FakePlayerItemSortCommand::status)))
            .then(Commands.literal("name").then(Commands.literal("set").then(Commands.argument("item",ResourceLocationArgument.id()).then(Commands.argument("text",StringArgumentType.greedyString()).executes(FakePlayerItemSortCommand::nameSet))))
                    .then(Commands.literal("remove").then(Commands.argument("item",ResourceLocationArgument.id()).executes(FakePlayerItemSortCommand::nameRemove)))
                    .then(Commands.literal("list").executes(c->nameList(c,1)).then(Commands.argument("page",IntegerArgumentType.integer(1)).executes(c->nameList(c,IntegerArgumentType.getInteger(c,"page")))))
                    .then(Commands.literal("reload").executes(FakePlayerItemSortCommand::reload)));
        //#if MC == 1.21.1
        root.then(Commands.literal("workers").then(Commands.argument("initial",IntegerArgumentType.integer(1)).then(Commands.argument("cached",IntegerArgumentType.integer(1)).executes(FakePlayerItemSortCommand::workers))))
                .then(Commands.literal("dashboard").then(Commands.literal("status").executes(FakePlayerItemSortCommand::dashboard)).then(Commands.literal("port").then(Commands.argument("port",IntegerArgumentType.integer(1024,65535)).executes(FakePlayerItemSortCommand::port))));
        //#endif
        return root;
    }
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> playerSort(){
        com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bot_sort").executes(c->sort(c,false)).then(Commands.literal("continuous").executes(c->sort(c,true))).then(Commands.literal("stop").executes(FakePlayerItemSortCommand::stop));
        //#if MC == 1.21.1
        root.then(Commands.literal("restart").then(Commands.literal("all").executes(FakePlayerItemSortCommand::prepareRebuildAll).then(Commands.literal("confirm").executes(FakePlayerItemSortCommand::confirmRebuildAll))).then(Commands.argument("item",StringArgumentType.greedyString()).executes(FakePlayerItemSortCommand::rebuildOne)));
        //#endif
        return root;
    }
    private static ServerPlayer target(CommandContext<CommandSourceStack> c){return c.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(c,"player"));}
    private static int sort(CommandContext<CommandSourceStack> c,boolean continuous){ServerPlayer p=target(c);if(p==null){c.getSource().sendFailure(Component.literal("target fake player is not online"));return 0;}if(!(p instanceof carpet.patches.EntityPlayerMPFake)){c.getSource().sendFailure(Component.literal("target must be a fake player"));return 0;}StringBuilder error=new StringBuilder();ServerPlayer initiator=c.getSource().getPlayer();if(!FakePlayerItemSortManager.start(p,continuous,initiator==null?null:initiator.getUUID(),error)){c.getSource().sendFailure(Component.literal(error.toString()));return 0;}ok(c,continuous?"continuous item sorting started":"item sorting started");return 1;}
    private static int stop(CommandContext<CommandSourceStack> c){ServerPlayer p=target(c);if(p==null)return fail(c,"target fake player is not online");return FakePlayerItemSortManager.stop(p)?ok(c,"item sorting stopped"):fail(c,"no active sorting job");}
    private static int rebuildOne(CommandContext<CommandSourceStack> c){ServerPlayer p=target(c);if(!(p instanceof carpet.patches.EntityPlayerMPFake))return fail(c,"target must be an online fake player");StringBuilder error=new StringBuilder();ServerPlayer actor=c.getSource().getPlayer();int queued=FakePlayerItemSortManager.queueRebuild(p,StringArgumentType.getString(c,"item"),actor==null?null:actor.getUUID(),error);return queued>0?ok(c,"sorter rebuild queued"):fail(c,error.toString());}
    private static int prepareRebuildAll(CommandContext<CommandSourceStack> c){ServerPlayer p=target(c);if(!(p instanceof carpet.patches.EntityPlayerMPFake))return fail(c,"target must be an online fake player");if(!FakePlayerItemSortManager.canRebuildAll(FGACompat.hasPermission(c.getSource(),2)))return fail(c,"restart all is disabled or requires OP permission");String key=actorKey(c);PENDING_REBUILDS.put(key,new PendingRebuild(p.getUUID(),System.currentTimeMillis()+REBUILD_CONFIRM_MS));String command="/player "+StringArgumentType.getString(c,"player")+" bot_sort restart all confirm";MutableComponent message=Component.literal(chinese()?"将重构全部已缓存分类库存，任务会限速执行。":"Rebuild every cached sorter inventory. Tasks run at a limited rate.").withStyle(ChatFormatting.YELLOW).append(Component.literal(" ")).append(Component.literal(chinese()?"[确认执行]":"[CONFIRM]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withClickEvent(FgaClickEvents.runCommand(command))));c.getSource().sendSuccess(()->message,false);return 1;}
    private static int confirmRebuildAll(CommandContext<CommandSourceStack> c){ServerPlayer p=target(c);if(!(p instanceof carpet.patches.EntityPlayerMPFake))return fail(c,"target must be an online fake player");if(!FakePlayerItemSortManager.canRebuildAll(FGACompat.hasPermission(c.getSource(),2)))return fail(c,"restart all is disabled or requires OP permission");PendingRebuild pending=PENDING_REBUILDS.remove(actorKey(c));if(pending==null||!pending.target().equals(p.getUUID())||pending.expiresAt()<System.currentTimeMillis())return fail(c,chinese()?"重构确认已过期。":"rebuild confirmation expired");StringBuilder error=new StringBuilder();ServerPlayer actor=c.getSource().getPlayer();int queued=FakePlayerItemSortManager.queueRebuildAll(p,actor==null?null:actor.getUUID(),error);return queued>0?ok(c,chinese()?"已将 "+queued+" 个分类路由加入限速重构队列。":"queued rebuild for "+queued+" sorter routes"):fail(c,error.toString());}
    private static String actorKey(CommandContext<CommandSourceStack> c){ServerPlayer actor=c.getSource().getPlayer();return actor==null?"console":actor.getUUID().toString();}
    private static boolean chinese(){return "chinese".equals(FakePlayerItemSortConfig.snapshot().targetLanguage());}
    private static int whitelist(CommandContext<CommandSourceStack> c,boolean add){try{boolean changed=add?FakePlayerItemSortConfig.addWhitelist(StringArgumentType.getString(c,"player")):FakePlayerItemSortConfig.removeWhitelist(StringArgumentType.getString(c,"player"));return changed?ok(c,add?"whitelist entry added":"whitelist entry removed"):fail(c,add?"already in whitelist":"not in whitelist");}catch(IOException e){return fail(c,e.getMessage());}}
    private static int whitelistList(CommandContext<CommandSourceStack> c,int page){return list(c,"sort whitelist",new ArrayList<>(FakePlayerItemSortConfig.snapshot().whitelist()),page,"/fakePlayerItemSort whitelist remove ");}
    private static int format(CommandContext<CommandSourceStack> c,boolean prefix){try{FakePlayerItemSortConfig.setFormat(prefix,StringArgumentType.getString(c,"text"));return ok(c,(prefix?"prefix":"suffix")+" updated");}catch(IOException e){return fail(c,e.getMessage());}}
    private static int nameSet(CommandContext<CommandSourceStack> c){ResourceLocation id=ResourceLocationArgument.getId(c,"item");if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))return fail(c,"unknown item: "+id);try{FakePlayerItemSortConfig.setName(id.toString(),StringArgumentType.getString(c,"text"));return ok(c,"custom name updated");}catch(IOException e){return fail(c,e.getMessage());}}
    private static int nameRemove(CommandContext<CommandSourceStack> c){try{return FakePlayerItemSortConfig.removeName(ResourceLocationArgument.getId(c,"item").toString())?ok(c,"custom name removed"):fail(c,"custom name not found");}catch(IOException e){return fail(c,e.getMessage());}}
    private static int nameList(CommandContext<CommandSourceStack> c,int page){return list(c,"custom item names",new ArrayList<>(FakePlayerItemSortConfig.snapshot().names().entrySet().stream().map(e->e.getKey()+" = "+e.getValue()).toList()),page,"/fakePlayerItemSort name remove ");}
    private static int reload(CommandContext<CommandSourceStack> c){try{FakePlayerItemSortConfig.reload();FakePlayerItemSortManager.recreateWorkers();return ok(c,"sort configuration reloaded");}catch(IOException e){return fail(c,e.getMessage());}}
    private static int workers(CommandContext<CommandSourceStack> c){try{FakePlayerItemSortConfig.setWorkers(IntegerArgumentType.getInteger(c,"initial"),IntegerArgumentType.getInteger(c,"cached"));FakePlayerItemSortManager.recreateWorkers();return ok(c,"worker limits updated");}catch(Exception e){return fail(c,e.getMessage());}}
    private static int dashboard(CommandContext<CommandSourceStack> c){return ok(c,FakePlayerItemSortDashboard.status()+"; "+FakePlayerItemSortManager.status());}
    private static int port(CommandContext<CommandSourceStack> c){try{FakePlayerItemSortConfig.setDashboardPort(IntegerArgumentType.getInteger(c,"port"));return ok(c,"dashboard port saved; restart the server to bind it");}catch(IOException e){return fail(c,e.getMessage());}}
    private static int mode(CommandContext<CommandSourceStack> c,String value){try{FakePlayerItemSortConfig.setMode(value);return ok(c,"sorter mode set to "+value);}catch(IOException e){return fail(c,e.getMessage());}}
    private static int setting(CommandContext<CommandSourceStack> c){try{String key=StringArgumentType.getString(c,"key");if(!settingKeys().contains(key))return fail(c,"sorter setting is not supported in this Minecraft version: "+key);String value=StringArgumentType.getString(c,"value");FakePlayerItemSortConfig.setOption(key,value);if("cpuThreads".equals(key))FakePlayerItemSortManager.recreateWorkers();return ok(c,"sorter setting "+key+" set to "+value);}catch(IOException e){return fail(c,e.getMessage());}}
    private static List<String> settingKeys(){
        //#if MC == 1.21.1
        return List.of("whitelistMode","quickShulker","targetLanguage","shulkerRestock","cleanOpenedTarget","inventoryRebuild","dashboard","cpuThreads","speed");
        //#else
        //$$ return List.of("whitelistMode","quickShulker","targetLanguage","cleanOpenedTarget");
        //#endif
    }
    private static List<String> settingValues(){
        //#if MC == 1.21.1
        return List.of("false","true","english","chinese","custom","vanillaWhitelist","modWhitelist","opall","0","1","2","4","8","16");
        //#else
        //$$ return List.of("false","true","english","chinese","custom","vanillaWhitelist","modWhitelist");
        //#endif
    }
    private static int help(CommandContext<CommandSourceStack> c){
        MutableComponent out=Component.literal("Fake Player Item Sort / 假人物品分类\n").withStyle(ChatFormatting.GOLD);
        out.append(Component.literal("/fakePlayerItemSort status\n").withStyle(ChatFormatting.GRAY));
        out.append(Component.literal("  # show the merged rule and JSON settings / 查看总开关和配置\n").withStyle(ChatFormatting.GOLD));
        out.append(Component.literal("/fakePlayerItemSort mode summon|quickopen\n").withStyle(ChatFormatting.GRAY));
        out.append(Component.literal("  # choose online or offline inventory access / 选择在线或离线背包访问\n").withStyle(ChatFormatting.GOLD));
        out.append(Component.literal("/fakePlayerItemSort setting <name> <value>\n").withStyle(ChatFormatting.GRAY));
        out.append(Component.literal("  # manage migrated sorter settings / 管理迁移后的分类配置\n").withStyle(ChatFormatting.GOLD));
        c.getSource().sendSuccess(()->out,false); return 1;
    }
    private static int status(CommandContext<CommandSourceStack> c){FakePlayerItemSortConfig.State s=FakePlayerItemSortConfig.snapshot();return ok(c,"enabled="+FGASettings.fakePlayerItemSort+", mode="+s.mode()+", whitelistMode="+s.whitelistMode()+", whitelist="+s.whitelist().size()+", language="+s.targetLanguage()+", quickShulker="+s.quickShulker()+", restock="+s.shulkerRestock()+", cleanOpenedTarget="+s.cleanOpenedTarget()+", rebuild="+s.inventoryRebuild()+", dashboard="+s.dashboard()+", cpuThreads="+s.cpuThreads()+", speed="+s.speed()+", names="+s.names().size()+", "+FakePlayerItemSortManager.status());}
    private static int list(CommandContext<CommandSourceStack> c,String title,List<String> values,int page,String remove){Collections.sort(values);int pages=Math.max(1,(values.size()+PAGE-1)/PAGE);if(page>pages)return fail(c,"page out of range");MutableComponent out=Component.literal(title+" "+page+"/"+pages+"\n").withStyle(ChatFormatting.GOLD);for(int i=(page-1)*PAGE;i<Math.min(values.size(),page*PAGE);i++){String v=values.get(i);out.append(Component.literal(v+" ").withStyle(ChatFormatting.GRAY)).append(Component.literal("[-]").withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withClickEvent(FgaClickEvents.runCommand(remove+v)))).append("\n");}c.getSource().sendSuccess(()->out,false);return 1;}
    private static int ok(CommandContext<CommandSourceStack> c,String m){c.getSource().sendSuccess(()->Component.literal(m).withStyle(ChatFormatting.GREEN),false);return 1;}private static int fail(CommandContext<CommandSourceStack> c,String m){c.getSource().sendFailure(Component.literal(m));return 0;}
    private record PendingRebuild(UUID target,long expiresAt) {}
}
//#endif
