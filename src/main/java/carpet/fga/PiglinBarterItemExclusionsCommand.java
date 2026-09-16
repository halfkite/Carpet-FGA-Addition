//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Commands for editing the current piglin barter loot table. */
public final class PiglinBarterItemExclusionsCommand {
    private PiglinBarterItemExclusionsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root(PiglinBarterCustomizationManager.COMMAND));
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .requires(PiglinBarterCustomizationManager::canUseCommand)
                .executes(PiglinBarterItemExclusionsCommand::list)
                .then(Commands.literal("list").executes(PiglinBarterItemExclusionsCommand::list))
                .then(Commands.literal("add").then(entryArgument().executes(PiglinBarterItemExclusionsCommand::add)))
                .then(Commands.literal("enable").then(entryArgument().executes(PiglinBarterItemExclusionsCommand::add)))
                .then(Commands.literal("disable").then(entryArgument().executes(PiglinBarterItemExclusionsCommand::disable)))
                .then(Commands.literal("remove").then(entryArgument().executes(PiglinBarterItemExclusionsCommand::disable)))
                .then(Commands.literal("set")
                        .then(entryArgument()
                                .then(Commands.argument("probability", DoubleArgumentType.doubleArg(0.0D))
                                        .then(Commands.argument("range", StringArgumentType.greedyString())
                                                .executes(PiglinBarterItemExclusionsCommand::set)))))
                .then(Commands.literal("reset").then(entryArgument().executes(PiglinBarterItemExclusionsCommand::reset)));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, net.minecraft.resources.ResourceLocation> entryArgument() {
        return Commands.argument("entry", ResourceLocationArgument.id()).suggests(PiglinBarterItemExclusionsCommand::suggestEntries);
    }

    private static CompletableFuture<Suggestions> suggestEntries(CommandContext<CommandSourceStack> context,
                                                                  SuggestionsBuilder builder) {
        MinecraftServer server = context.getSource().getServer();
        PiglinBarterCustomizationManager.snapshot(server).entries().keySet().stream()
                .filter(key -> key.startsWith(builder.getRemainingLowerCase()))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        PiglinBarterCustomizationManager.State state = PiglinBarterCustomizationManager.snapshot(server);
        MutableComponent message = FGACompat.literal("猪灵交易物品自定义 / Piglin barter customization\n")
                .withStyle(ChatFormatting.GOLD);
        message.append(FGACompat.literal("当前会计入交易的交易物品 / Enabled barter items\n")
                .withStyle(ChatFormatting.YELLOW));
        boolean hasEnabled = false;
        for (PiglinBarterCustomizationManager.Entry entry : state.entries().values()) {
            if (!entry.present() || !entry.enabled()) continue;
            hasEnabled = true;
            appendEntry(message, entry, true);
        }
        if (!hasEnabled) message.append(FGACompat.literal("暂无启用条目 / No enabled entries\n")
                .withStyle(ChatFormatting.GRAY));

        message.append(FGACompat.literal("已删除的默认交易物品 / Disabled default barter items\n")
                .withStyle(ChatFormatting.YELLOW));
        boolean hasDisabled = false;
        for (PiglinBarterCustomizationManager.Entry entry : state.entries().values()) {
            if (entry.present() && entry.enabled()) continue;
            hasDisabled = true;
            appendEntry(message, entry, false);
        }
        if (!hasDisabled) message.append(FGACompat.literal("暂无删除条目 / No disabled entries\n")
                .withStyle(ChatFormatting.GRAY));

        message.append(FGACompat.literal("[+] 添加交易物品 / Add barter item\n")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                        .withClickEvent(FgaClickEvents.suggestCommand(
                                "/fga " + PiglinBarterCustomizationManager.COMMAND + " add "))));
        FGACompat.sendSuccess(context.getSource(), message, false);
        return 1;
    }

    private static void appendEntry(MutableComponent message, PiglinBarterCustomizationManager.Entry entry,
                                    boolean enabled) {
        String base = "/fga " + PiglinBarterCustomizationManager.COMMAND + " ";
        String range = entry.min() + "-" + entry.max();
        String setCommand = base + "set " + entry.key() + " " + format(entry.probability()) + " " + range;
        message.append(PiglinBarterCustomizationManager.displayName(entry))
                .append(FGACompat.literal(" / " + entry.itemId() + " ").withStyle(ChatFormatting.GRAY))
                .append(FGACompat.literal(format(entry.probability()) + "%")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                                .withClickEvent(FgaClickEvents.suggestCommand(setCommand))))
                .append(FGACompat.literal(" " + range)
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                                .withClickEvent(FgaClickEvents.suggestCommand(setCommand))))
                .append(FGACompat.literal(" ").withStyle(ChatFormatting.GRAY));

        String action = enabled ? "disable" : "enable";
        ChatFormatting actionColor = enabled ? ChatFormatting.RED : ChatFormatting.GREEN;
        String symbol = enabled ? "[-]" : "[+]";
        message.append(FGACompat.literal(symbol).withStyle(Style.EMPTY.withColor(actionColor)
                        .withClickEvent(FgaClickEvents.runCommand(base + action + " " + entry.key()))))
                .append(FGACompat.literal(" ").withStyle(ChatFormatting.GRAY))
                .append(FGACompat.literal("[重置 / reset]").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                        .withClickEvent(FgaClickEvents.runCommand(base + "reset " + entry.key()))))
                .append(FGACompat.literal("\n"));
    }

    private static int add(CommandContext<CommandSourceStack> context) {
        return mutate(context, (key, server) -> PiglinBarterCustomizationManager.add(key, server),
                "已添加交易物品 / Added barter item: ");
    }

    private static int disable(CommandContext<CommandSourceStack> context) {
        return mutate(context, (key, server) -> PiglinBarterCustomizationManager.disable(key, server),
                "已删除交易物品 / Disabled barter item: ");
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        return mutate(context, (key, server) -> PiglinBarterCustomizationManager.reset(key, server),
                "已重置交易物品概率和数量 / Reset barter item values: ");
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        String key = ResourceLocationArgument.getId(context, "entry").toString();
        String range = StringArgumentType.getString(context, "range");
        int separator = range.indexOf('-');
        if (separator <= 0 || separator == range.length() - 1) {
            return failure(context, "数量范围必须写成 min-max / quantity range must be min-max");
        }
        try {
            int min = Integer.parseInt(range.substring(0, separator));
            int max = Integer.parseInt(range.substring(separator + 1));
            PiglinBarterCustomizationManager.set(key, DoubleArgumentType.getDouble(context, "probability"), min, max,
                    context.getSource().getServer());
            return success(context, "已更新交易物品 / Updated barter item: " + key);
        } catch (Exception exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int mutate(CommandContext<CommandSourceStack> context, Mutation mutation, String prefix) {
        String key = ResourceLocationArgument.getId(context, "entry").toString();
        try {
            mutation.apply(key, context.getSource().getServer());
            return success(context, prefix + key);
        } catch (Exception exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int success(CommandContext<CommandSourceStack> context, String message) {
        FGACompat.sendSuccess(context.getSource(), FGACompat.literal(message).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int failure(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendFailure(FGACompat.literal(message == null ? "unknown error" : message));
        return 0;
    }

    @FunctionalInterface
    private interface Mutation {
        void apply(String key, MinecraftServer server) throws Exception;
    }
}
//#endif
