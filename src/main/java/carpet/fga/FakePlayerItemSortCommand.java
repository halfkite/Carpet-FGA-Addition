//#if MC >= 1.20.1 && MC <= 26.3
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
            .then(Commands.literal("setting").then(Commands.argument("key",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(settingKeys(),b)).then(Commands.argument("value",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(settingValues(StringArgumentType.getString(c,"key")),b)).executes(FakePlayerItemSortCommand::setting))))
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
        //#if MC == 1.21.1 || MC == 26.3
        root.then(Commands.literal("workers").then(Commands.argument("initial",IntegerArgumentType.integer(1)).then(Commands.argument("cached",IntegerArgumentType.integer(1)).executes(FakePlayerItemSortCommand::workers))))
                .then(Commands.literal("dashboard").then(Commands.literal("status").executes(FakePlayerItemSortCommand::dashboard)).then(Commands.literal("port").then(Commands.argument("port",IntegerArgumentType.integer(1024,65535)).executes(FakePlayerItemSortCommand::port))));
        //#endif
        return root;
    }
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> playerSort(){
        com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bot_sort").executes(c->sort(c,false)).then(Commands.literal("continuous").executes(c->sort(c,true))).then(Commands.literal("stop").executes(FakePlayerItemSortCommand::stop));
        //#if MC == 1.21.1 || MC == 26.3
        root.then(Commands.literal("restart").then(Commands.literal("all").executes(FakePlayerItemSortCommand::prepareRebuildAll).then(Commands.literal("confirm").executes(FakePlayerItemSortCommand::confirmRebuildAll))).then(Commands.argument("item",StringArgumentType.greedyString()).executes(FakePlayerItemSortCommand::rebuildOne)));
        //#endif
        return root;
    }
    private static final String TEXT_PREFIX = "carpet.fga.fake_player_item_sort.";

    private static ServerPlayer target(CommandContext<CommandSourceStack> c){return c.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(c,"player"));}
    // Config changes, worker/dashboard control and sorting jobs touch world config files and
    // offline playerdata, so they stay behind the commandPlayer gate AND require OP permission.
    private static boolean opOnly(CommandContext<CommandSourceStack> c){return FGACompat.hasPermission(c.getSource(),2);}
    private static MutableComponent text(String key,Object...args){return FGAText.text(TEXT_PREFIX+key,args);}
    private static int sort(CommandContext<CommandSourceStack> c,boolean continuous){if(!opOnly(c))return fail(c,"permission_required");ServerPlayer p=target(c);if(p==null)return fail(c,"target_offline");if(!(p instanceof carpet.patches.EntityPlayerMPFake))return fail(c,"fake_player_required");if(isPossessionParticipant(p))return fail(c,"target_busy");StringBuilder error=new StringBuilder();ServerPlayer initiator=c.getSource().getPlayer();if(!FakePlayerItemSortManager.start(p,continuous,initiator==null?null:initiator.getUUID(),error))return fail(c,sortFailureKey(error.toString()));return ok(c,continuous?"continuous_started":"started");}
    private static boolean isPossessionParticipant(ServerPlayer player){
        //#if MC >= 1.21 && MC <= 26.3
        return PlayerPossessionManager.isParticipant(player);
        //#else
        //$$ return false;
        //#endif
    }
    private static String sortFailureKey(String error){return switch(error){case "enable /carpet fakePlayerItemSort true"->"sort_rule_disabled";case "set /carpet fakePlayerNameLength 64 or higher"->"sort_name_length";case "set /carpet fakePlayerProfilePreload always or adaptive"->"sort_profile_preload";case "set /carpet fgaUnicodeArgumentsSupport true for Chinese/custom target names"->"sort_unicode_required";default->"operation_failed";};}
    private static int stop(CommandContext<CommandSourceStack> c){ServerPlayer p=target(c);if(p==null)return fail(c,"target_offline");return FakePlayerItemSortManager.stop(p)?ok(c,"stopped"):fail(c,"no_active_job");}
    private static int rebuildOne(CommandContext<CommandSourceStack> c){if(!opOnly(c))return fail(c,"permission_required");ServerPlayer p=target(c);if(!(p instanceof carpet.patches.EntityPlayerMPFake))return fail(c,"fake_player_required");StringBuilder error=new StringBuilder();ServerPlayer actor=c.getSource().getPlayer();int queued=FakePlayerItemSortManager.queueRebuild(p,StringArgumentType.getString(c,"item"),actor==null?null:actor.getUUID(),error);return queued>0?ok(c,"rebuild_queued"):rebuildFailure(c,error.toString());}
    private static int prepareRebuildAll(CommandContext<CommandSourceStack> c){ServerPlayer p=target(c);if(!(p instanceof carpet.patches.EntityPlayerMPFake))return fail(c,"fake_player_required");if(!FakePlayerItemSortManager.canRebuildAll(FGACompat.hasPermission(c.getSource(),2)))return fail(c,"rebuild_all_disabled");String key=actorKey(c);PENDING_REBUILDS.put(key,new PendingRebuild(p.getUUID(),System.currentTimeMillis()+REBUILD_CONFIRM_MS));String command="/player "+StringArgumentType.getString(c,"player")+" bot_sort restart all confirm";MutableComponent message=text("rebuild_all_prompt").withStyle(ChatFormatting.YELLOW).append(Component.literal(" ")).append(text("confirm_button").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withClickEvent(FgaClickEvents.runCommand(command))));return sendSuccess(c,message);}
    private static int confirmRebuildAll(CommandContext<CommandSourceStack> c){ServerPlayer p=target(c);if(!(p instanceof carpet.patches.EntityPlayerMPFake))return fail(c,"fake_player_required");if(!FakePlayerItemSortManager.canRebuildAll(FGACompat.hasPermission(c.getSource(),2)))return fail(c,"rebuild_all_disabled");PendingRebuild pending=PENDING_REBUILDS.remove(actorKey(c));if(pending==null||!pending.target().equals(p.getUUID())||pending.expiresAt()<System.currentTimeMillis())return fail(c,"rebuild_expired");StringBuilder error=new StringBuilder();ServerPlayer actor=c.getSource().getPlayer();int queued=FakePlayerItemSortManager.queueRebuildAll(p,actor==null?null:actor.getUUID(),error);return queued>0?ok(c,"rebuild_all_queued",queued):rebuildFailure(c,error.toString());}
    private static int rebuildFailure(CommandContext<CommandSourceStack> c,String error){if(error.equals("set inventoryRebuild to true or opall with /fakePlayerItemSort"))return fail(c,"rebuild_disabled");if(error.startsWith("no cached sorter item named "))return fail(c,"rebuild_item_missing",error.substring("no cached sorter item named ".length()));if(error.startsWith("ambiguous item: "))return fail(c,"rebuild_item_ambiguous");return fail(c,"operation_failed");}
    private static String actorKey(CommandContext<CommandSourceStack> c){ServerPlayer actor=c.getSource().getPlayer();return actor==null?"console":actor.getUUID().toString();}
    private static int whitelist(CommandContext<CommandSourceStack> c,boolean add){if(!opOnly(c))return fail(c,"permission_required");try{boolean changed=add?FakePlayerItemSortConfig.addWhitelist(StringArgumentType.getString(c,"player")):FakePlayerItemSortConfig.removeWhitelist(StringArgumentType.getString(c,"player"));return changed?ok(c,add?"whitelist_added":"whitelist_removed"):fail(c,add?"already_whitelisted":"not_whitelisted");}catch(IOException|IllegalArgumentException e){return fail(c,"invalid_value");}}
    private static int whitelistList(CommandContext<CommandSourceStack> c,int page){return list(c,"whitelist_title",new ArrayList<>(FakePlayerItemSortConfig.snapshot().whitelist()),page,"/fakePlayerItemSort whitelist remove ");}
    private static int format(CommandContext<CommandSourceStack> c,boolean prefix){if(!opOnly(c))return fail(c,"permission_required");try{FakePlayerItemSortConfig.setFormat(prefix,StringArgumentType.getString(c,"text"));return ok(c,prefix?"prefix_updated":"suffix_updated");}catch(IOException|IllegalArgumentException e){return fail(c,"invalid_value");}}
    private static int nameSet(CommandContext<CommandSourceStack> c){if(!opOnly(c))return fail(c,"permission_required");ResourceLocation id=ResourceLocationArgument.getId(c,"item");if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))return fail(c,"unknown_item",id);try{FakePlayerItemSortConfig.setName(id.toString(),StringArgumentType.getString(c,"text"));return ok(c,"name_updated");}catch(IOException|IllegalArgumentException e){return fail(c,"invalid_value");}}
    private static int nameRemove(CommandContext<CommandSourceStack> c){if(!opOnly(c))return fail(c,"permission_required");try{return FakePlayerItemSortConfig.removeName(ResourceLocationArgument.getId(c,"item").toString())?ok(c,"name_removed"):fail(c,"name_not_found");}catch(IOException|IllegalArgumentException e){return fail(c,"invalid_value");}}
    private static int nameList(CommandContext<CommandSourceStack> c,int page){return list(c,"names_title",new ArrayList<>(FakePlayerItemSortConfig.snapshot().names().entrySet().stream().map(e->e.getKey()+" = "+e.getValue()).toList()),page,"/fakePlayerItemSort name remove ");}
    private static int reload(CommandContext<CommandSourceStack> c){if(!opOnly(c))return fail(c,"permission_required");try{FakePlayerItemSortConfig.reload();FakePlayerItemSortManager.recreateWorkers();return ok(c,"configuration_reloaded");}catch(IOException e){return fail(c,"operation_failed");}}
    private static int workers(CommandContext<CommandSourceStack> c){if(!opOnly(c))return fail(c,"permission_required");int initial=IntegerArgumentType.getInteger(c,"initial"),cached=IntegerArgumentType.getInteger(c,"cached");try{FakePlayerItemSortConfig.setWorkers(initial,cached);FakePlayerItemSortManager.recreateWorkers();return ok(c,"workers_updated",initial,cached);}catch(Exception e){return fail(c,"invalid_value");}}
    private static int dashboard(CommandContext<CommandSourceStack> c){String dashboard=FakePlayerItemSortDashboard.status();MutableComponent message=dashboard.equals("stopped")?text("dashboard_stopped"):text("dashboard_running",dashboard);message.append(Component.literal("\n")).append(FakePlayerItemSortManager.statusText());return sendSuccess(c,message);}
    private static int port(CommandContext<CommandSourceStack> c){if(!opOnly(c))return fail(c,"permission_required");int port=IntegerArgumentType.getInteger(c,"port");try{FakePlayerItemSortConfig.setDashboardPort(port);return ok(c,"dashboard_port_saved",port);}catch(IOException e){return fail(c,"operation_failed");}}
    private static int mode(CommandContext<CommandSourceStack> c,String value){if(!opOnly(c))return fail(c,"permission_required");try{FakePlayerItemSortConfig.setMode(value);return ok(c,"mode_set",value);}catch(IOException|IllegalArgumentException e){return fail(c,"invalid_value");}}
    private static int setting(CommandContext<CommandSourceStack> c){if(!opOnly(c))return fail(c,"permission_required");String key=StringArgumentType.getString(c,"key");if(!settingKeys().contains(key))return fail(c,"setting_unsupported",key);String value=StringArgumentType.getString(c,"value");try{FakePlayerItemSortConfig.setOption(key,value);if("cpuThreads".equals(key))FakePlayerItemSortManager.recreateWorkers();return ok(c,"setting_set",key,value);}catch(IOException|IllegalArgumentException e){return fail(c,"invalid_value");}}
    static List<String> settingKeys(){
        //#if MC == 1.21.1 || MC == 26.3
        return List.of("whitelistMode","quickShulker","targetLanguage","shulkerRestock","cleanOpenedTarget","inventoryRebuild","dashboard","cpuThreads","speed");
        //#else
        //$$ return List.of("whitelistMode","quickShulker","targetLanguage","cleanOpenedTarget");
        //#endif
    }
    static List<String> settingValues(String key){
        return switch(key){
            case "whitelistMode"->List.of("false","vanillaWhitelist","modWhitelist");
            case "quickShulker","cleanOpenedTarget","shulkerRestock","dashboard"->List.of("false","true");
            case "targetLanguage"->List.of("english","chinese","custom");
            case "inventoryRebuild"->List.of("false","true","opall");
            case "cpuThreads"->List.of("0","1","2");
            case "speed"->List.of("4","8","16");
            default->List.of();
        };
    }
    private static int help(CommandContext<CommandSourceStack> c){
        MutableComponent out=text("help_title").withStyle(ChatFormatting.GOLD).append(Component.literal("\n"));
        helpLine(out,"/fakePlayerItemSort status","help_status");
        helpLine(out,"/fakePlayerItemSort mode summon|quickopen","help_mode");
        helpLine(out,"/fakePlayerItemSort setting <name> <value>","help_setting");
        helpLine(out,"/fakePlayerItemSort whitelist add|remove <player>","help_whitelist");
        helpLine(out,"/fakePlayerItemSort whitelist list [page]","help_whitelist_list");
        helpLine(out,"/fakePlayerItemSort format prefix|suffix <text>","help_format");
        helpLine(out,"/fakePlayerItemSort name set|remove|list|reload ...","help_names");
        helpLine(out,"/player <fake> bot_sort [continuous|stop]","help_sort");
        //#if MC == 1.21.1 || MC == 26.3
        helpLine(out,"/fakePlayerItemSort workers <initial> <cached>","help_workers");
        helpLine(out,"/fakePlayerItemSort dashboard status|port <port>","help_dashboard");
        helpLine(out,"/player <fake> bot_sort restart <item|all>","help_rebuild");
        //#endif
        return sendSuccess(c,out);
    }
    private static void helpLine(MutableComponent out,String command,String descriptionKey){out.append(Component.literal(command+"\n").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withClickEvent(FgaClickEvents.suggestCommand(command))));out.append(text(descriptionKey).withStyle(ChatFormatting.GOLD).append(Component.literal("\n")));}
    private static int status(CommandContext<CommandSourceStack> c){FakePlayerItemSortConfig.State s=FakePlayerItemSortConfig.snapshot();MutableComponent out=text("status",FGASettings.fakePlayerItemSort,s.mode(),s.whitelistMode(),s.whitelist().size(),s.targetLanguage(),s.quickShulker(),s.shulkerRestock(),s.cleanOpenedTarget(),s.inventoryRebuild(),s.dashboard(),s.cpuThreads(),s.speed(),s.names().size()).append(Component.literal("\n")).append(FakePlayerItemSortManager.statusText());return sendSuccess(c,out);}
    private static int list(CommandContext<CommandSourceStack> c,String titleKey,List<String> values,int page,String remove){Collections.sort(values);int pages=Math.max(1,(values.size()+PAGE-1)/PAGE);if(page>pages)return fail(c,"page_out_of_range");MutableComponent out=text(titleKey,page,pages).withStyle(ChatFormatting.GOLD).append(Component.literal("\n"));for(int i=(page-1)*PAGE;i<Math.min(values.size(),page*PAGE);i++){String v=values.get(i);out.append(Component.literal(v+" ").withStyle(ChatFormatting.GRAY)).append(Component.literal("[-]").withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withClickEvent(FgaClickEvents.runCommand(remove+v)))).append("\n");}return sendSuccess(c,out);}
    private static int ok(CommandContext<CommandSourceStack> c,String key,Object...args){return sendSuccess(c,text(key,args).withStyle(ChatFormatting.GREEN));}
    private static int fail(CommandContext<CommandSourceStack> c,String key,Object...args){return sendFailure(c,text(key,args));}
    private static int sendSuccess(CommandContext<CommandSourceStack> c,Component component){c.getSource().sendSuccess(()->component,false);return 1;}
    private static int sendFailure(CommandContext<CommandSourceStack> c,Component component){c.getSource().sendFailure(component);return 0;}
    private record PendingRebuild(UUID target,long expiresAt) {}
}
//#endif
