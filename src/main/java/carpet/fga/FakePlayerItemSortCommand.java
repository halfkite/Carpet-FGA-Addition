//#if MC >= 1.20.1 && MC <= 26.3
package carpet.fga;

import carpet.CarpetSettings;
import carpet.utils.CommandHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class FakePlayerItemSortCommand {
    private FakePlayerItemSortCommand() {}
    private static final int PAGE=10;
    private static final long REBUILD_CONFIRM_MS = 30_000L;
    private static final Map<String, PendingRebuild> PENDING_REBUILDS = new HashMap<>();
    private static final Set<UUID> SETUP_NOTIFIED = ConcurrentHashMap.newKeySet();
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){dispatcher.register(root("fakePlayerItemSort"));}
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name){
        var root = Commands.literal(name).requires(FakePlayerItemSortCommand::canUseAny)
                .executes(FakePlayerItemSortCommand::rootAction);
        root.then(Commands.literal("help").requires(s -> allowed(s,"help")).executes(FakePlayerItemSortCommand::help));
        root.then(Commands.literal("status").requires(s -> allowed(s,"status")).executes(FakePlayerItemSortCommand::status));
        var configuration = root;
        //#if MC == 26.3
        if (usesGroupedSettings()) {
            configuration = Commands.literal("set").requires(FakePlayerItemSortCommand::canConfigure)
                    .executes(FakePlayerItemSortCommand::setup);
            configuration.then(Commands.argument("key",StringArgumentType.word()).requires(s -> allowed(s,"settings"))
                    .suggests((c,b)->SharedSuggestionProvider.suggest(advancedSettingKeys(),b))
                    .then(Commands.argument("value",StringArgumentType.word())
                            .suggests((c,b)->SharedSuggestionProvider.suggest(settingValues(StringArgumentType.getString(c,"key")),b))
                            .executes(FakePlayerItemSortCommand::setting)));
        }
        //#endif
        configuration.then(Commands.literal("mode").requires(s -> allowed(s,"mode"))
                .then(Commands.literal("summon").executes(c->mode(c,"summon")))
                .then(Commands.literal("quickopen").executes(c->mode(c,"quickopen"))));
        //#if MC != 26.3
        root.then(Commands.literal("setting").requires(s -> allowed(s,"settings"))
                .then(Commands.argument("key",StringArgumentType.word())
                        .suggests((c,b)->SharedSuggestionProvider.suggest(settingKeys(),b))
                        .then(Commands.argument("value",StringArgumentType.word())
                                .suggests((c,b)->SharedSuggestionProvider.suggest(settingValues(StringArgumentType.getString(c,"key")),b))
                                .executes(FakePlayerItemSortCommand::setting))));
        //#endif
        root.then(Commands.literal("whitelist").requires(s -> allowed(s,"whitelist"))
                .then(Commands.literal("add").then(Commands.argument("player",StringArgumentType.word()).executes(c->whitelist(c,true))))
                .then(Commands.literal("remove").then(Commands.argument("player",StringArgumentType.word()).executes(c->whitelist(c,false))))
                .then(Commands.literal("list").executes(c->whitelistList(c,1))
                        .then(Commands.argument("page",IntegerArgumentType.integer(1)).executes(c->whitelistList(c,IntegerArgumentType.getInteger(c,"page")))))
                .then(Commands.literal("status").executes(FakePlayerItemSortCommand::whitelistStatus))
                //#if MC != 26.3
                .then(Commands.literal("mode")
                        .then(Commands.literal("false").executes(c->settingValue(c,"whitelistMode","false")))
                        .then(Commands.literal("vanillaWhitelist").executes(c->settingValue(c,"whitelistMode","vanillaWhitelist")))
                        .then(Commands.literal("modWhitelist").executes(c->settingValue(c,"whitelistMode","modWhitelist"))))
                //#endif
        );
        configuration.then(Commands.literal("format").requires(s -> allowed(s,"format"))
                .then(Commands.literal("prefix").then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->format(c,true))))
                .then(Commands.literal("suffix").then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->format(c,false))))
                .then(Commands.literal("status").executes(FakePlayerItemSortCommand::status)));
        root.then(Commands.literal("name").requires(s -> allowed(s,"name"))
                .then(Commands.literal("set").then(Commands.argument("item",ResourceLocationArgument.id())
                        .then(Commands.argument("text",StringArgumentType.greedyString()).executes(FakePlayerItemSortCommand::nameSet))))
                .then(Commands.literal("remove").then(Commands.argument("item",ResourceLocationArgument.id()).executes(FakePlayerItemSortCommand::nameRemove)))
                .then(Commands.literal("list").executes(c->nameList(c,1))
                        .then(Commands.argument("page",IntegerArgumentType.integer(1)).executes(c->nameList(c,IntegerArgumentType.getInteger(c,"page")))))
                .then(Commands.literal("reload").executes(FakePlayerItemSortCommand::reload)));
        //#if MC == 1.21.1 || MC == 26.3
        configuration.then(Commands.literal("workers").requires(s -> allowed(s,"workers"))
                .then(Commands.argument("initial",IntegerArgumentType.integer(1))
                        .then(Commands.argument("cached",IntegerArgumentType.integer(1)).executes(FakePlayerItemSortCommand::workers))));
        configuration.then(Commands.literal("dashboard").requires(s ->
                //#if MC == 26.3
                allowedSetting(s,"dashboard")
                //#else
                //$$ allowed(s,"dashboard")
                //#endif
        )
                //#if MC == 26.3
                .then(Commands.literal("false").executes(c->settingValue(c,"dashboard","false")))
                .then(Commands.literal("true").executes(c->settingValue(c,"dashboard","true")))
                .then(Commands.literal("login").executes(c->settingValue(c,"dashboard","login")))
                .then(Commands.literal("password").requires(s -> allowed(s,"dashboard")).then(Commands.argument("password",StringArgumentType.string())
                        .executes(FakePlayerItemSortCommand::dashboardPassword)))
                //#endif
                .then(Commands.literal("status").requires(s -> allowed(s,"dashboard")).executes(FakePlayerItemSortCommand::dashboard))
                .then(Commands.literal("port").requires(s -> allowed(s,"dashboard")).then(Commands.argument("port",IntegerArgumentType.integer(1024,65535)).executes(FakePlayerItemSortCommand::port))));
        //#endif
        //#if MC == 26.3
        root.then(Commands.literal("setup").requires(s -> allowed(s,"setup")).executes(FakePlayerItemSortCommand::setup));
        var language = Commands.literal("language").requires(s -> allowed(s,"settings"))
                .then(Commands.literal("chinese").executes(c->settingValue(c,"targetLanguage","chinese")))
                .then(Commands.literal("english").executes(c->settingValue(c,"targetLanguage","english")))
                .then(Commands.literal("custom").executes(c->settingValue(c,"targetLanguage","custom")));
        root.then(language);
        if (usesGroupedSettings()) {
            configuration.then(language);
            configuration.then(Commands.literal("whitelistMode").requires(s -> allowedSetting(s,"whitelist"))
                    .then(Commands.literal("false").executes(c->settingValue(c,"whitelistMode","false")))
                    .then(Commands.literal("vanillaWhitelist").executes(c->settingValue(c,"whitelistMode","vanillaWhitelist")))
                    .then(Commands.literal("modWhitelist").executes(c->settingValue(c,"whitelistMode","modWhitelist"))));
        }
        configuration.then(Commands.literal("prefix").requires(s -> allowed(s,"prefix"))
                .then(Commands.literal("default").executes(c->setPrefix(c,"bulk_")))
                .then(Commands.literal("off").executes(c->setPrefix(c,"")))
                .then(Commands.literal("custom").then(Commands.argument("text",StringArgumentType.greedyString()).executes(FakePlayerItemSortCommand::setCustomPrefix))));
        configuration.then(Commands.literal("quickShulker").requires(s -> allowedSetting(s,"quickShulker"))
                .then(Commands.literal("true").executes(c->settingValue(c,"quickShulker","true")))
                .then(Commands.literal("false").executes(c->settingValue(c,"quickShulker","false"))));
        configuration.then(Commands.literal("autoCraft").requires(s -> allowedSetting(s,"autoCraft"))
                .then(Commands.literal("true").executes(c->settingValue(c,"shulkerRestock","true")))
                .then(Commands.literal("false").executes(c->settingValue(c,"shulkerRestock","false"))));
        configuration.then(Commands.literal("summonNotices").requires(s -> allowedSetting(s,"summonNotices"))
                .then(Commands.literal("true").executes(c->settingValue(c,"summonNotices","true")))
                .then(Commands.literal("false").executes(c->settingValue(c,"summonNotices","false"))));
        configuration.then(Commands.literal("cleanOpenedTarget").requires(s -> allowedSetting(s,"cleanOpenedTarget"))
                .then(Commands.literal("true").executes(c->settingValue(c,"cleanOpenedTarget","true")))
                .then(Commands.literal("false").executes(c->settingValue(c,"cleanOpenedTarget","false"))));
        configuration.then(Commands.literal("speed").requires(s -> allowedSetting(s,"speed"))
                .then(Commands.argument("ticks",IntegerArgumentType.integer(1,120))
                        .suggests((c,b)->SharedSuggestionProvider.suggest(List.of("4","8","16"),b))
                        .executes(FakePlayerItemSortCommand::setCustomSpeed)));
        configuration.then(Commands.literal("cpu").requires(s -> allowedSetting(s,"cpu"))
                .then(Commands.literal("0").executes(c->settingValue(c,"cpuThreads","0")))
                .then(Commands.literal("1").executes(c->settingValue(c,"cpuThreads","1")))
                .then(Commands.literal("2").executes(c->settingValue(c,"cpuThreads","2")))
                .then(Commands.literal("custom").then(Commands.argument("threads",IntegerArgumentType.integer(1,256))
                        .executes(FakePlayerItemSortCommand::setCustomCpuThreads))));
        root.then(Commands.literal("stock").requires(s -> allowed(s,"stock"))
                .then(Commands.literal("list")
                        .then(Commands.literal("all").executes(FakePlayerItemSortCommand::stockExport))
                        .then(Commands.argument("pattern",StringArgumentType.word())
                                .executes(c->stockList(c,1))
                                .then(Commands.argument("page",IntegerArgumentType.integer(1))
                                        .executes(c->stockList(c,IntegerArgumentType.getInteger(c,"page")))))));
        root.then(Commands.literal("permission").requires(s -> allowed(s,"permission"))
                .then(Commands.argument("command",StringArgumentType.word())
                        .suggests((c,b)->SharedSuggestionProvider.suggest(permissionKeys(),b))
                        .then(Commands.argument("principal",StringArgumentType.word())
                                .suggests((c,b)->SharedSuggestionProvider.suggest(permissionPrincipals(),b))
                                .then(Commands.argument("allowed",BoolArgumentType.bool())
                                        .executes(FakePlayerItemSortCommand::setPermission)))));
        if (usesGroupedSettings()) root.then(configuration);
        //#endif
        return root;
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> playerSort(){
        var root = Commands.literal("bot_sort").requires(s -> allowed(s,"sort"))
                .executes(c->sort(c,false)).then(Commands.literal("continuous").executes(c->sort(c,true)))
                .then(Commands.literal("stop").executes(FakePlayerItemSortCommand::stop));
        //#if MC == 1.21.1 || MC == 26.3
        root.then(Commands.literal("restart").requires(s -> allowed(s,"restart"))
                .then(Commands.literal("all").executes(FakePlayerItemSortCommand::prepareRebuildAll)
                        .then(Commands.literal("confirm").executes(FakePlayerItemSortCommand::confirmRebuildAll)))
                .then(Commands.literal("stop").executes(FakePlayerItemSortCommand::stopRebuild))
                .then(Commands.argument("item",StringArgumentType.greedyString()).executes(FakePlayerItemSortCommand::rebuildOne)));
        //#endif
        return root;
    }
    private static final String TEXT_PREFIX = "carpet.fga.fake_player_item_sort.";

    // The 1.21.1 root compiles this file directly, without preprocessing.
    static boolean usesGroupedSettings() {
        //#if MC == 26.3
        //$$ return true;
        //#else
        return false;
        //#endif
    }

    //#if MC == 26.3
    private static boolean allowedSetting(CommandSourceStack source, String permission) {
        return allowed(source,permission) || usesGroupedSettings() && allowed(source,"settings");
    }
    private static boolean canConfigure(CommandSourceStack source) {
        return List.of("settings", "mode", "prefix", "quickShulker", "autoCraft", "summonNotices",
                "whitelist", "cleanOpenedTarget", "speed", "cpu", "dashboard", "format", "workers")
                .stream().anyMatch(key -> allowed(source,key));
    }
    private static List<String> advancedSettingKeys() {
        return List.of("targetLanguage", "shulkerRestock", "cpuThreads", "inventoryRebuild");
    }
    //#endif

    private static boolean allowed(CommandSourceStack source,String permission){
        //#if MC == 26.3
        return FakePlayerItemSortConfig.canUse(source,permission);
        //#else
        //$$ return CommandHelper.canUseCommand(source,CarpetSettings.commandPlayer);
        //#endif
    }
    private static boolean canUseAny(CommandSourceStack source){
        //#if MC == 26.3
        return FakePlayerItemSortConfig.permissionCommands().stream().filter(key->!"all".equals(key)).anyMatch(key->allowed(source,key));
        //#else
        //$$ return CommandHelper.canUseCommand(source,CarpetSettings.commandPlayer);
        //#endif
    }
    private static boolean canEdit(CommandContext<CommandSourceStack> context,String permission){
        //#if MC == 26.3
        return FakePlayerItemSortConfig.canUse(context.getSource(),permission);
        //#else
        //$$ return FGACompat.hasPermission(context.getSource(),2);
        //#endif
    }
    private static int rootAction(CommandContext<CommandSourceStack> context){
        //#if MC == 26.3
        if (allowed(context.getSource(),"status")) return status(context);
        if (allowed(context.getSource(),"help")) return help(context);
        return setup(context);
        //#else
        //$$ return status(context);
        //#endif
    }
    private static List<String> permissionKeys(){
        return FakePlayerItemSortConfig.permissionCommands().stream().sorted().toList();
    }
    private static List<String> permissionPrincipals(){
        List<String> values=new ArrayList<>(List.of("ops","0","1","2","3","4"));
        if(carpet.CarpetServer.minecraft_server!=null)carpet.CarpetServer.minecraft_server.getPlayerList().getPlayers()
                .forEach(player->values.add(player.getGameProfile().getName()));
        return values;
    }

    private static ServerPlayer target(CommandContext<CommandSourceStack> c){return c.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(c,"player"));}
    private static MutableComponent text(String key,Object...args){return FGAText.text(TEXT_PREFIX+key,args);}
    private static String commandRoot(){
        //#if MC == 26.3
        return "/fga playersort";
        //#else
        //$$ return "/fakePlayerItemSort";
        //#endif
    }

    public static void onRuleEnabled(CommandSourceStack source) {
        if (!FGASettings.isFakePlayerItemSortEnabled() || FakePlayerItemSortConfig.isSetupComplete()) return;
        ServerPlayer player = source.getPlayer();
        if (player != null) sendSetupWelcome(player);
        else source.getServer().getPlayerList().getPlayers().stream()
                .filter(FakePlayerItemSortCommand::canSetup)
                .findFirst().ifPresent(FakePlayerItemSortCommand::sendSetupWelcome);
    }

    public static void onPlayerJoin(ServerPlayer player) {
        if (FGASettings.isFakePlayerItemSortEnabled() && !FakePlayerItemSortConfig.isSetupComplete()
                && canSetup(player)) {
            sendSetupWelcome(player);
        }
    }

    private static boolean canSetup(ServerPlayer player) {
        return FakePlayerItemSortConfig.canUse(
                //#if MC == 1.20.1 || MC == 1.21.1
                player.createCommandSourceStack(),
                //#else
                //$$ player.createCommandSourceStackForNameResolution(player.serverLevel()),
                //#endif
                "setup");
    }

    public static void clearSetupNotifications() { SETUP_NOTIFIED.clear(); }

    private static void sendSetupWelcome(ServerPlayer player) {
        if (!SETUP_NOTIFIED.add(player.getUUID())) return;
        MutableComponent welcome = text("setup_welcome").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("\n"))
                .append(text("setup_click").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                        .withClickEvent(FgaClickEvents.suggestCommand("/fga playersort setup"))))
                .append(Component.literal("\n"))
                .append(setupPanel());
        player.sendSystemMessage(welcome);
    }

    private static int setup(CommandContext<CommandSourceStack> c) {
        return sendSuccess(c, setupPanel());
    }

    private static MutableComponent setupPanel() {
        MutableComponent out = text("setup_title").withStyle(ChatFormatting.GOLD).append(Component.literal("\n"));
        FakePlayerItemSortConfig.State state = FakePlayerItemSortConfig.snapshot();
        boolean languageConfigured = FakePlayerItemSortConfig.isConfigured("language");
        appendSetupRow(out, "setup_language", languageConfigured,
                setupOption("setup_chinese", state.targetLanguage().equals("chinese"), languageConfigured, "/fga playersort set language chinese"),
                setupOption("setup_english", state.targetLanguage().equals("english"), languageConfigured, "/fga playersort set language english"),
                setupOption("setup_custom", state.targetLanguage().equals("custom"), languageConfigured, "/fga playersort set language custom"));
        if (!languageConfigured) return out;

        appendSetupRow(out, "setup_mode", FakePlayerItemSortConfig.isConfigured("mode"),
                setupOption("setup_direct", state.mode().equals("quickopen"), FakePlayerItemSortConfig.isConfigured("mode"), "/fga playersort set mode quickopen"),
                setupOption("setup_summon", state.mode().equals("summon"), FakePlayerItemSortConfig.isConfigured("mode"), "/fga playersort set mode summon"));
        appendSetupRow(out, "setup_summon_notices", FakePlayerItemSortConfig.isConfigured("summonNotices"),
                setupOption("setup_true", state.summonNotices(), FakePlayerItemSortConfig.isConfigured("summonNotices"), "/fga playersort set summonNotices true"),
                setupOption("setup_false", !state.summonNotices(), FakePlayerItemSortConfig.isConfigured("summonNotices"), "/fga playersort set summonNotices false"));
        out.append(text("setup_summon_notices_note").withStyle(ChatFormatting.GRAY).append(Component.literal("\n")));
        boolean prefixConfigured = FakePlayerItemSortConfig.isConfigured("prefix");
        boolean prefixDefault = state.prefix().equals("bulk_");
        boolean prefixOff = state.prefix().isEmpty();
        appendSetupRow(out, "setup_prefix", prefixConfigured,
                setupOption("setup_prefix_default", prefixDefault, prefixConfigured, "/fga playersort set prefix default"),
                setupOption("setup_prefix_custom", !prefixDefault && !prefixOff, prefixConfigured, "/fga playersort set prefix custom "),
                setupOption("setup_off", prefixOff, prefixConfigured, "/fga playersort set prefix off"));
        appendSetupRow(out, "setup_quick_shulker", FakePlayerItemSortConfig.isConfigured("quickShulker"),
                setupOption("setup_true", state.quickShulker(), FakePlayerItemSortConfig.isConfigured("quickShulker"), "/fga playersort set quickShulker true"),
                setupOption("setup_false", !state.quickShulker(), FakePlayerItemSortConfig.isConfigured("quickShulker"), "/fga playersort set quickShulker false"));
        out.append(text(state.quickShulker() ? "setup_quick_shulker_enabled_note" : "setup_quick_shulker_disabled_note")
                .withStyle(ChatFormatting.GRAY).append(Component.literal("\n")));
        appendSetupRow(out, "setup_auto_craft", FakePlayerItemSortConfig.isConfigured("autoCraft"),
                setupOption("setup_true", state.shulkerRestock(), FakePlayerItemSortConfig.isConfigured("autoCraft"), "/fga playersort set autoCraft true"),
                setupOption("setup_false", !state.shulkerRestock(), FakePlayerItemSortConfig.isConfigured("autoCraft"), "/fga playersort set autoCraft false"));
        appendSetupRow(out, "setup_whitelist", FakePlayerItemSortConfig.isConfigured("whitelistMode"),
                setupOption("setup_vanilla_whitelist", state.whitelistMode().equals("vanillaWhitelist"), FakePlayerItemSortConfig.isConfigured("whitelistMode"), "/fga playersort set whitelistMode vanillaWhitelist"),
                setupOption("setup_fga_whitelist", state.whitelistMode().equals("modWhitelist"), FakePlayerItemSortConfig.isConfigured("whitelistMode"), "/fga playersort set whitelistMode modWhitelist"),
                setupOption("setup_off", state.whitelistMode().equals("false"), FakePlayerItemSortConfig.isConfigured("whitelistMode"), "/fga playersort set whitelistMode false"));
        out.append(text("setup_vanilla_whitelist_note").withStyle(ChatFormatting.GRAY).append(Component.literal("\n")));
        out.append(text("setup_fga_whitelist_note").withStyle(ChatFormatting.GRAY).append(Component.literal("\n")));
        appendSetupRow(out, "setup_clean_opened", FakePlayerItemSortConfig.isConfigured("cleanOpenedTarget"),
                setupOption("setup_true", state.cleanOpenedTarget(), FakePlayerItemSortConfig.isConfigured("cleanOpenedTarget"), "/fga playersort set cleanOpenedTarget true"),
                setupOption("setup_false", !state.cleanOpenedTarget(), FakePlayerItemSortConfig.isConfigured("cleanOpenedTarget"), "/fga playersort set cleanOpenedTarget false"));
        appendSetupRow(out, "setup_speed", FakePlayerItemSortConfig.isConfigured("speed"),
                setupOption("setup_speed_4", state.speed().equals("4"), FakePlayerItemSortConfig.isConfigured("speed"), "/fga playersort set speed 4"),
                setupOption("setup_speed_8", state.speed().equals("8"), FakePlayerItemSortConfig.isConfigured("speed"), "/fga playersort set speed 8"),
                setupOption("setup_speed_16", state.speed().equals("16"), FakePlayerItemSortConfig.isConfigured("speed"), "/fga playersort set speed 16"),
                setupOption("setup_custom", !Set.of("4", "8", "16").contains(state.speed()), FakePlayerItemSortConfig.isConfigured("speed"), "/fga playersort set speed "));
        out.append(text("setup_speed_note").withStyle(ChatFormatting.GRAY).append(Component.literal("\n")));
        appendSetupRow(out, "setup_cpu", FakePlayerItemSortConfig.isConfigured("cpu"),
                setupOption("setup_cpu_0", state.cpuThreads().equals("0"), FakePlayerItemSortConfig.isConfigured("cpu"), "/fga playersort set cpu 0"),
                setupOption("setup_cpu_1", state.cpuThreads().equals("1"), FakePlayerItemSortConfig.isConfigured("cpu"), "/fga playersort set cpu 1"),
                setupOption("setup_cpu_2", state.cpuThreads().equals("2"), FakePlayerItemSortConfig.isConfigured("cpu"), "/fga playersort set cpu 2"),
                setupOption("setup_cpu_custom", !Set.of("0", "1", "2").contains(state.cpuThreads()), FakePlayerItemSortConfig.isConfigured("cpu"), "/fga playersort set cpu custom "));
        out.append(text("setup_cpu_note").withStyle(ChatFormatting.GRAY).append(Component.literal("\n")));
        //#if MC == 26.3
        String webMode = FakePlayerItemSortConfig.dashboardMode();
        appendSetupRow(out, "setup_dashboard", FakePlayerItemSortConfig.isConfigured("dashboard"),
                setupOption("setup_false", webMode.equals("false"), FakePlayerItemSortConfig.isConfigured("dashboard"), "/fga playersort set dashboard false"),
                setupOption("setup_true", webMode.equals("true"), FakePlayerItemSortConfig.isConfigured("dashboard"), "/fga playersort set dashboard true"),
                setupOption("setup_dashboard_login", webMode.equals("login"), FakePlayerItemSortConfig.isConfigured("dashboard"), "/fga playersort set dashboard login"));
        out.append(text("setup_dashboard_note").withStyle(ChatFormatting.GRAY).append(Component.literal("\n")));
        //#endif
        out.append(text("setup_progress", SETUP_FIELDS_DONE()).withStyle(ChatFormatting.GOLD));
        return out;
    }

    private static int SETUP_FIELDS_DONE() {
        return (int) FakePlayerItemSortConfig.setupFields().stream().filter(FakePlayerItemSortConfig::isConfigured).count();
    }

    private static void appendSetupRow(MutableComponent out, String key, boolean configured, MutableComponent... options) {
        out.append(text(key).withStyle(configured ? ChatFormatting.WHITE : ChatFormatting.GRAY).append(Component.literal("：")));
        for (MutableComponent option : options) out.append(option).append(Component.literal(" "));
        out.append(Component.literal("\n"));
    }

    private static MutableComponent setupOption(String labelKey, boolean selected, boolean configured, String command) {
        if (!usesGroupedSettings()) {
            command = command.replace("/fga playersort set whitelistMode ", "/fga playersort setting whitelistMode ")
                    .replace("/fga playersort set ", "/fga playersort ");
        }
        ChatFormatting color = selected && configured ? ChatFormatting.AQUA : (configured ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY);
        return text(labelKey).withStyle(Style.EMPTY.withColor(color).withClickEvent(FgaClickEvents.suggestCommand(command))
                .withHoverEvent(
                        //#if MC >= 1.21.5
                        //$$ new HoverEvent.ShowText(Component.literal(command))
                        //#else
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))
                        //#endif
                ));
    }

    private static int sort(CommandContext<CommandSourceStack> c,boolean continuous){if(!canEdit(c,"sort"))return fail(c,"permission_required");ServerPlayer p=target(c);if(p==null)return fail(c,"target_offline");if(!(p instanceof carpet.patches.EntityPlayerMPFake))return fail(c,"fake_player_required");if(isPossessionParticipant(p))return fail(c,"target_busy");StringBuilder error=new StringBuilder();ServerPlayer initiator=c.getSource().getPlayer();if(!FakePlayerItemSortManager.start(p,continuous,initiator==null?null:initiator.getUUID(),error))return fail(c,sortFailureKey(error.toString()));return ok(c,continuous?"continuous_started":"started");}
    private static boolean isPossessionParticipant(ServerPlayer player){
        //#if MC >= 1.21 && MC <= 26.3
        return PlayerPossessionManager.isParticipant(player);
        //#else
        //$$ return false;
        //#endif
    }
    private static String sortFailureKey(String error){return switch(error){case "enable /carpet fakePlayerItemSort true"->"sort_rule_disabled";case "set /carpet fakePlayerNameLength 64 or higher"->"sort_name_length";case "set /carpet fakePlayerProfilePreload always or adaptive"->"sort_profile_preload";case "set /carpet fgaUnicodeArgumentsSupport true for Chinese/custom target names"->"sort_unicode_required";default->"operation_failed";};}
    private static int stop(CommandContext<CommandSourceStack> c){ServerPlayer p=target(c);if(p==null)return fail(c,"target_offline");return FakePlayerItemSortManager.stop(p)?ok(c,"stopped"):fail(c,"no_active_job");}
    private static int rebuildOne(CommandContext<CommandSourceStack> c){if(!canEdit(c,"restart"))return fail(c,"permission_required");ServerPlayer p=target(c);if(!(p instanceof carpet.patches.EntityPlayerMPFake))return fail(c,"fake_player_required");StringBuilder error=new StringBuilder();ServerPlayer actor=c.getSource().getPlayer();int queued=FakePlayerItemSortManager.queueRebuild(p,StringArgumentType.getString(c,"item"),actor==null?null:actor.getUUID(),error);return queued>0?ok(c,"rebuild_queued"):rebuildFailure(c,error.toString());}
    private static int stopRebuild(CommandContext<CommandSourceStack> c){if(!canEdit(c,"restart"))return fail(c,"permission_required");ServerPlayer p=target(c);if(!(p instanceof carpet.patches.EntityPlayerMPFake))return fail(c,"fake_player_required");int removed=FakePlayerItemSortManager.stopRebuild(p);return removed>0?ok(c,"rebuild_stopped",removed):fail(c,"no_rebuild_queued");}
    private static int prepareRebuildAll(CommandContext<CommandSourceStack> c){ServerPlayer p=target(c);if(!(p instanceof carpet.patches.EntityPlayerMPFake))return fail(c,"fake_player_required");if(!FakePlayerItemSortManager.canRebuildAll(FGACompat.hasPermission(c.getSource(),2)))return fail(c,"rebuild_all_disabled");String key=actorKey(c);PENDING_REBUILDS.put(key,new PendingRebuild(p.getUUID(),System.currentTimeMillis()+REBUILD_CONFIRM_MS));String command="/player "+StringArgumentType.getString(c,"player")+" bot_sort restart all confirm";MutableComponent message=text("rebuild_all_prompt").withStyle(ChatFormatting.YELLOW).append(Component.literal(" ")).append(text("confirm_button").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withClickEvent(FgaClickEvents.runCommand(command))));return sendSuccess(c,message);}
    private static int confirmRebuildAll(CommandContext<CommandSourceStack> c){ServerPlayer p=target(c);if(!(p instanceof carpet.patches.EntityPlayerMPFake))return fail(c,"fake_player_required");if(!FakePlayerItemSortManager.canRebuildAll(FGACompat.hasPermission(c.getSource(),2)))return fail(c,"rebuild_all_disabled");PendingRebuild pending=PENDING_REBUILDS.remove(actorKey(c));if(pending==null||!pending.target().equals(p.getUUID())||pending.expiresAt()<System.currentTimeMillis())return fail(c,"rebuild_expired");StringBuilder error=new StringBuilder();ServerPlayer actor=c.getSource().getPlayer();int queued=FakePlayerItemSortManager.queueRebuildAll(p,actor==null?null:actor.getUUID(),error);return queued>0?ok(c,"rebuild_all_queued",queued):rebuildFailure(c,error.toString());}
    private static int rebuildFailure(CommandContext<CommandSourceStack> c,String error){if(error.equals("set inventoryRebuild to true or opall with /fakePlayerItemSort"))return fail(c,"rebuild_disabled");if(error.startsWith("no cached sorter item named "))return fail(c,"rebuild_item_missing",error.substring("no cached sorter item named ".length()));if(error.startsWith("ambiguous item: "))return fail(c,"rebuild_item_ambiguous");return fail(c,"operation_failed");}
    private static String actorKey(CommandContext<CommandSourceStack> c){ServerPlayer actor=c.getSource().getPlayer();return actor==null?"console":actor.getUUID().toString();}
    private static int whitelist(CommandContext<CommandSourceStack> c,boolean add){if(!canEdit(c,"whitelist"))return fail(c,"permission_required");try{boolean changed=add?FakePlayerItemSortConfig.addWhitelist(StringArgumentType.getString(c,"player")):FakePlayerItemSortConfig.removeWhitelist(StringArgumentType.getString(c,"player"));return changed?ok(c,add?"whitelist_added":"whitelist_removed"):fail(c,add?"already_whitelisted":"not_whitelisted");}catch(IOException|IllegalArgumentException e){return fail(c,"invalid_value");}}
    private static int whitelistStatus(CommandContext<CommandSourceStack> c){FakePlayerItemSortConfig.State state=FakePlayerItemSortConfig.snapshot();return sendSuccess(c,text("whitelist_status",state.whitelistMode(),state.whitelist().size()).withStyle(ChatFormatting.GOLD));}
    private static int whitelistList(CommandContext<CommandSourceStack> c,int page){return list(c,"whitelist_title",new ArrayList<>(FakePlayerItemSortConfig.snapshot().whitelist()),page,commandRoot()+" whitelist remove ");}
    private static int format(CommandContext<CommandSourceStack> c,boolean prefix){if(!canEdit(c,"format"))return fail(c,"permission_required");try{FakePlayerItemSortConfig.setFormat(prefix,StringArgumentType.getString(c,"text"));return ok(c,prefix?"prefix_updated":"suffix_updated");}catch(IOException|IllegalArgumentException e){return fail(c,"invalid_value");}}
    private static int nameSet(CommandContext<CommandSourceStack> c){if(!canEdit(c,"name"))return fail(c,"permission_required");ResourceLocation id=ResourceLocationArgument.getId(c,"item");if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))return fail(c,"unknown_item",id);try{FakePlayerItemSortConfig.setName(id.toString(),StringArgumentType.getString(c,"text"));return ok(c,"name_updated");}catch(IOException|IllegalArgumentException e){return fail(c,"invalid_value");}}
    private static int nameRemove(CommandContext<CommandSourceStack> c){if(!canEdit(c,"name"))return fail(c,"permission_required");try{return FakePlayerItemSortConfig.removeName(ResourceLocationArgument.getId(c,"item").toString())?ok(c,"name_removed"):fail(c,"name_not_found");}catch(IOException|IllegalArgumentException e){return fail(c,"invalid_value");}}
    private static int nameList(CommandContext<CommandSourceStack> c,int page){return list(c,"names_title",new ArrayList<>(FakePlayerItemSortConfig.snapshot().names().entrySet().stream().map(e->e.getKey()+" = "+e.getValue()).toList()),page,commandRoot()+" name remove ");}
    private static int reload(CommandContext<CommandSourceStack> c){if(!canEdit(c,"name"))return fail(c,"permission_required");try{FakePlayerItemSortConfig.reload();FakePlayerItemSortManager.recreateWorkers();return ok(c,"configuration_reloaded");}catch(IOException e){return fail(c,"operation_failed");}}
    private static int workers(CommandContext<CommandSourceStack> c){if(!canEdit(c,"workers"))return fail(c,"permission_required");int initial=IntegerArgumentType.getInteger(c,"initial"),cached=IntegerArgumentType.getInteger(c,"cached");try{FakePlayerItemSortConfig.setWorkers(initial,cached);FakePlayerItemSortManager.recreateWorkers();return ok(c,"workers_updated",initial,cached);}catch(Exception e){return fail(c,"invalid_value");}}
    private static int dashboard(CommandContext<CommandSourceStack> c){String dashboard=FakePlayerItemSortDashboard.status();MutableComponent message=dashboard.equals("stopped")?text("dashboard_stopped"):text("dashboard_running",dashboard);message.append(Component.literal("\n")).append(FakePlayerItemSortManager.statusText());return sendSuccess(c,message);}
    //#if MC == 26.3
    private static int dashboardPassword(CommandContext<CommandSourceStack> c) {
        if (!canEdit(c,"dashboard")) return fail(c,"permission_required");
        try {
            FakePlayerItemSortConfig.setDashboardPassword(StringArgumentType.getString(c,"password"));
            return ok(c,"dashboard_password_saved");
        } catch (IOException | IllegalArgumentException exception) { return fail(c,"dashboard_password_invalid"); }
    }
    //#endif
    private static int port(CommandContext<CommandSourceStack> c){if(!canEdit(c,"dashboard"))return fail(c,"permission_required");int port=IntegerArgumentType.getInteger(c,"port");try{FakePlayerItemSortConfig.setDashboardPort(port);return ok(c,"dashboard_port_saved",port);}catch(IOException e){return fail(c,"operation_failed");}}
    private static int mode(CommandContext<CommandSourceStack> c,String value){if(!canEdit(c,"mode"))return fail(c,"permission_required");try{FakePlayerItemSortConfig.setMode(value);ok(c,"mode_set",value);return appendSetupIf26(c);}catch(IOException|IllegalArgumentException e){return fail(c,"invalid_value");}}
    private static int setting(CommandContext<CommandSourceStack> c){
        if(!canEdit(c,"settings"))return fail(c,"permission_required");
        String key=StringArgumentType.getString(c,"key");
        if(!settingKeys().contains(key))return fail(c,"setting_unsupported",key);
        //#if MC == 26.3
        if(usesGroupedSettings() && !advancedSettingKeys().contains(key))return fail(c,"setting_unsupported",key);
        //#endif
        return setOption(c,key,StringArgumentType.getString(c,"value"));
    }

    private static int settingValue(CommandContext<CommandSourceStack> c,String key,String value){return setOption(c,key,value);}
    private static int setOption(CommandContext<CommandSourceStack> c,String key,String value){
        try {
            FakePlayerItemSortConfig.setOption(key,value);
            if("cpuThreads".equals(key)) FakePlayerItemSortManager.recreateWorkers();
            ok(c,"setting_set",key,value);
            return appendSetupIf26(c);
        } catch(IOException|IllegalArgumentException e) {
            //#if MC == 26.3
            if (key.equals("dashboard") && value.equals("login")) return fail(c,"dashboard_password_help");
            //#endif
            return fail(c,"invalid_value");
        }
    }
    private static int setPrefix(CommandContext<CommandSourceStack> c,String value){
        try { FakePlayerItemSortConfig.setPrefix(value); ok(c,"prefix_updated"); return appendSetupIf26(c); }
        catch(IOException|IllegalArgumentException e) { return fail(c,"invalid_value"); }
    }
    private static int setCustomPrefix(CommandContext<CommandSourceStack> c){return setPrefix(c,StringArgumentType.getString(c,"text"));}
    private static int setCustomSpeed(CommandContext<CommandSourceStack> c){return setOption(c,"speed",Integer.toString(IntegerArgumentType.getInteger(c,"ticks")));}
    private static int setCustomCpuThreads(CommandContext<CommandSourceStack> c){return setOption(c,"cpuThreads",Integer.toString(IntegerArgumentType.getInteger(c,"threads")));}
    private static int setPermission(CommandContext<CommandSourceStack> c){
        String command=StringArgumentType.getString(c,"command"), principal=StringArgumentType.getString(c,"principal");
        try {
            FakePlayerItemSortConfig.setPermission(command,principal,BoolArgumentType.getBool(c,"allowed"));
            refreshCommandTrees(c.getSource().getServer());
            return ok(c,"permission_saved",command,principal,BoolArgumentType.getBool(c,"allowed"));
        } catch(IOException|IllegalArgumentException e) { return fail(c,"invalid_value"); }
    }
    private static int appendSetupIf26(CommandContext<CommandSourceStack> c){
        //#if MC == 26.3
        return sendSuccess(c,setupPanel());
        //#else
        //$$ return 1;
        //#endif
    }
    private static void refreshCommandTrees(net.minecraft.server.MinecraftServer server){
        //#if MC == 26.3
        server.getPlayerList().getPlayers().forEach(player->server.getCommands().sendCommands(player));
        //#else
        //$$ CommandHelper.notifyPlayersCommandsChanged(server);
        //#endif
    }

    private static int stockExport(CommandContext<CommandSourceStack> c){
        //#if MC == 26.3
        //$$ try { return sendSuccess(c,stockExportFeedback(FakePlayerItemSortManager.writeStockExport(),CarpetSettings.language)); }
        //#else
        try { return ok(c,"stock_exported",FakePlayerItemSortManager.writeStockExport()); }
        //#endif
        catch(IOException|RuntimeException e) { return fail(c,"operation_failed"); }
    }
    //#if MC == 26.3
    //$$ /** Upgrade regression: encode this feedback as ClientboundSystemChatPacket, then test stock list all in game. */
    //$$ static MutableComponent stockExportFeedback(java.nio.file.Path output, String language) {
    //$$     // Translation arguments must be wire-compatible; a Path formats locally but cannot be encoded.
    //$$     String key = "carpet.fga.fake_player_item_sort.stock_exported";
    //$$     String path = output.toString();
    //$$     return Component.translatableWithFallback(key,FGAText.formatForLanguage(language,key,path),path)
    //$$             .withStyle(ChatFormatting.GREEN);
    //$$ }
    //#endif
    private static int stockList(CommandContext<CommandSourceStack> c,int page){
        Pattern pattern;
        try { pattern=Pattern.compile(StringArgumentType.getString(c,"pattern"),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE); }
        catch(PatternSyntaxException e) { return fail(c,"stock_invalid_pattern",e.getDescription()); }
        List<FakePlayerItemSortManager.StockEntry> matches=FakePlayerItemSortManager.stockEntries().stream()
                .filter(entry->pattern.matcher(entry.name()).find() || pattern.matcher(entry.itemId()).find() || pattern.matcher(entry.targets()).find())
                .sorted(Comparator.comparing(FakePlayerItemSortManager.StockEntry::name,String.CASE_INSENSITIVE_ORDER)).toList();
        int pages=Math.max(1,(matches.size()+PAGE-1)/PAGE);
        if(page>pages)return fail(c,"page_out_of_range");
        MutableComponent out=text("stock_title",page,pages,matches.size()).withStyle(ChatFormatting.GOLD).append(Component.literal("\n"));
        for(int i=(page-1)*PAGE;i<Math.min(matches.size(),page*PAGE);i++){
            FakePlayerItemSortManager.StockEntry entry=matches.get(i);
            long boxes=(entry.count()+1727L)/1728L;
            out.append(text("stock_line",entry.name(),entry.count(),boxes,entry.targets()).withStyle(ChatFormatting.GRAY).append(Component.literal("\n")));
        }
        return sendSuccess(c,out);
    }
    static List<String> settingKeys(){
        //#if MC == 1.21.1 || MC == 26.3
        //#if MC == 26.3
        return List.of("whitelistMode","quickShulker","targetLanguage","summonNotices","shulkerRestock","cleanOpenedTarget","inventoryRebuild","dashboard","cpuThreads","speed");
        //#else
        //$$ return List.of("whitelistMode","quickShulker","targetLanguage","shulkerRestock","cleanOpenedTarget","inventoryRebuild","dashboard","cpuThreads","speed");
        //#endif
        //#else
        //$$ return List.of("whitelistMode","quickShulker","targetLanguage","cleanOpenedTarget");
        //#endif
    }
    static List<String> settingValues(String key){
        return switch(key){
            case "whitelistMode"->List.of("false","vanillaWhitelist","modWhitelist");
            case "dashboard" -> {
                //#if MC == 26.3
                yield List.of("false","true","login");
                //#else
                //$$ yield List.of("false","true");
                //#endif
            }
            case "quickShulker","cleanOpenedTarget","shulkerRestock","summonNotices"->List.of("false","true");
            case "targetLanguage"->List.of("english","chinese","custom");
            case "inventoryRebuild"->List.of("false","true","opall");
            case "cpuThreads"-> {
                //#if MC == 26.3
                yield List.of("0","1","2");
                //#else
                //$$ yield List.of("0","1","2");
                //#endif
            }
            case "speed"->List.of("4","8","16");
            default->List.of();
        };
    }
    private static int help(CommandContext<CommandSourceStack> c){
        MutableComponent out = text("help_title").withStyle(ChatFormatting.GOLD).append(Component.literal("\n"));
        //#if MC == 26.3
        out.append(setupPanel()).append(Component.literal("\n"));
        //#endif
        String rootCommand = commandRoot();
        String settingsCommand = rootCommand;
        //#if MC == 26.3
        if (usesGroupedSettings()) {
            settingsCommand += " set";
            helpLine(out,settingsCommand,"help_setting");
            helpLine(out,rootCommand+" language chinese|english|custom","setup_language");
        }
        //#endif
        helpLine(out,rootCommand+" status","help_status");
        helpLine(out,settingsCommand+" mode summon|quickopen","help_mode");
        if (usesGroupedSettings()) helpLine(out,settingsCommand+" <option> <value>","help_setting");
        else helpLine(out,rootCommand+" setting <name> <value>","help_setting");
        helpLine(out,rootCommand+" whitelist add|remove <player>","help_whitelist");
        helpLine(out,rootCommand+" whitelist list [page]", "help_whitelist_list");
        if (usesGroupedSettings()) {
            helpLine(out,rootCommand+" whitelist status","help_whitelist_status");
            helpLine(out,settingsCommand+" whitelistMode false|vanillaWhitelist|modWhitelist","help_whitelist_status");
        } else helpLine(out,rootCommand+" whitelist status|mode <false|vanillaWhitelist|modWhitelist>","help_whitelist_status");
        helpLine(out,settingsCommand+" format prefix|suffix <text>","help_format");
        helpLine(out,rootCommand+" name set|remove|list|reload ...","help_names");
        helpLine(out,"/player <fake> bot_sort [continuous|stop]","help_sort");
        //#if MC == 1.21.1 || MC == 26.3
        helpLine(out,settingsCommand+" workers <initial> <cached>","help_workers");
        helpLine(out,settingsCommand+" dashboard status|port <port>","help_dashboard");
        //#if MC == 26.3
        helpLine(out,settingsCommand+" dashboard false|true|login","setup_dashboard_note");
        helpLine(out,settingsCommand+" dashboard password <password>","dashboard_password_help");
        //#endif
        helpLine(out,"/player <fake> bot_sort restart <item|all confirm|stop>","help_rebuild");
        //#endif
        //#if MC == 26.3
        helpLine(out,rootCommand+" stock list <regex>|all","help_stock");
        helpLine(out,rootCommand+" permission <command> <ops|0-4|player> <true|false>","help_permission");
        //#endif
        return sendSuccess(c,out);
    }
    private static void helpLine(MutableComponent out,String command,String descriptionKey){out.append(Component.literal(command+"\n").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withClickEvent(FgaClickEvents.suggestCommand(command))));out.append(text(descriptionKey).withStyle(ChatFormatting.GOLD).append(Component.literal("\n")));}
    private static int status(CommandContext<CommandSourceStack> c){FakePlayerItemSortConfig.State s=FakePlayerItemSortConfig.snapshot();MutableComponent out=text("status",FGASettings.fakePlayerItemSort,s.mode(),s.whitelistMode(),s.whitelist().size(),s.targetLanguage(),s.quickShulker(),s.shulkerRestock(),s.cleanOpenedTarget(),s.inventoryRebuild(),s.dashboard(),s.cpuThreads(),s.speed(),s.names().size()).append(Component.literal("\n")).append(FakePlayerItemSortManager.statusText());
        //#if MC == 26.3
        out.append(Component.literal("\n")).append(text("setup_status",SETUP_FIELDS_DONE(),FakePlayerItemSortConfig.setupFields().size()));
        //#endif
        return sendSuccess(c,out);}
    private static int list(CommandContext<CommandSourceStack> c,String titleKey,List<String> values,int page,String remove){Collections.sort(values);int pages=Math.max(1,(values.size()+PAGE-1)/PAGE);if(page>pages)return fail(c,"page_out_of_range");MutableComponent out=text(titleKey,page,pages).withStyle(ChatFormatting.GOLD).append(Component.literal("\n"));for(int i=(page-1)*PAGE;i<Math.min(values.size(),page*PAGE);i++){String v=values.get(i);out.append(Component.literal(v+" ").withStyle(ChatFormatting.GRAY)).append(Component.literal("[-]").withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withClickEvent(FgaClickEvents.runCommand(remove+v)))).append("\n");}return sendSuccess(c,out);}
    private static int ok(CommandContext<CommandSourceStack> c,String key,Object...args){return sendSuccess(c,text(key,args).withStyle(ChatFormatting.GREEN));}
    private static int fail(CommandContext<CommandSourceStack> c,String key,Object...args){return sendFailure(c,text(key,args));}
    private static int sendSuccess(CommandContext<CommandSourceStack> c,Component component){c.getSource().sendSuccess(()->component,false);return 1;}
    private static int sendFailure(CommandContext<CommandSourceStack> c,Component component){c.getSource().sendFailure(component);return 0;}
    private record PendingRebuild(UUID target,long expiresAt) {}
}
//#endif
