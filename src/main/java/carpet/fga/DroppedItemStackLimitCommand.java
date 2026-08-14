//#if MC <= 26.2
package carpet.fga;

//#if MC < 1.21.1
//$$ import carpet.CarpetSettings;
//#endif
//#if MC >= 1.19
import carpet.utils.CommandHelper;
//#else
//$$ import carpet.settings.SettingsManager;
//#endif
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
//#if MC >= 1.19.3
import net.minecraft.core.registries.BuiltInRegistries;
//#else
//$$ import net.minecraft.core.Registry;
//#endif
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class DroppedItemStackLimitCommand {
    private static final int PAGE_SIZE = 10;

    private DroppedItemStackLimitCommand() {
    }

    private static final class Component {
        private static MutableComponent literal(String text) {
            return FGACompat.literal(text);
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("droppedItemStackLimit")
                .requires(source ->
                        //#if MC >= 1.21.1
                        CommandHelper.canUseCommand(source, FGASettings.droppedItemStackLimit)
                        //#elseif MC >= 1.19
                        //$$ CommandHelper.canUseCommand(source, CarpetSettings.carpetCommandPermissionLevel)
                        //#else
                        //$$ SettingsManager.canUseCommand(source, CarpetSettings.commandPlayer)
                        //#endif
                )
                .then(Commands.literal("mode")
                        .then(Commands.literal("inventory")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1,
                                                DroppedItemStackLimitConfig.MAX_LIMIT))
                                        .executes(DroppedItemStackLimitCommand::setInventoryLimit)))
                        .then(Commands.literal("container")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1,
                                                DroppedItemStackLimitConfig.MAX_LIMIT))
                                        .executes(DroppedItemStackLimitCommand::setContainerLimit)))
                        .then(Commands.literal("all")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1,
                                                DroppedItemStackLimitConfig.MAX_LIMIT))
                                        .executes(DroppedItemStackLimitCommand::setAllMode)))
                        .then(Commands.literal("black")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1,
                                                DroppedItemStackLimitConfig.MAX_LIMIT))
                                        .executes(DroppedItemStackLimitCommand::setBlackMode)))
                        .then(Commands.literal("whitelist")
                                .executes(DroppedItemStackLimitCommand::setWhitelistMode)))
                .then(Commands.literal("set")
                        .then(Commands.literal("black")
                                .then(itemArgument().executes(DroppedItemStackLimitCommand::addBlacklist)))
                        .then(Commands.literal("whitelist")
                                .then(itemArgument()
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1,
                                                        DroppedItemStackLimitConfig.MAX_LIMIT))
                                                .executes(DroppedItemStackLimitCommand::setWhitelistItem)))))
                .then(Commands.literal("remove")
                        .then(Commands.literal("black")
                                .then(itemArgument().executes(DroppedItemStackLimitCommand::removeBlacklist)))
                        .then(Commands.literal("whitelist")
                                .then(itemArgument().executes(DroppedItemStackLimitCommand::removeWhitelistItem))))
                .then(Commands.literal("reset")
                        .then(Commands.literal("inventory").executes(DroppedItemStackLimitCommand::resetInventoryLimit))
                        .then(Commands.literal("container").executes(DroppedItemStackLimitCommand::resetContainerLimit)))
                .then(Commands.literal("clear").executes(DroppedItemStackLimitCommand::clearActiveList))
                .then(Commands.literal("list")
                        .executes(DroppedItemStackLimitCommand::showSummary)
                        .then(Commands.literal("black")
                                .executes(context -> listBlacklist(context, 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> listBlacklist(context,
                                                IntegerArgumentType.getInteger(context, "page")))))
                        .then(Commands.literal("whitelist")
                                .executes(context -> listWhitelist(context, 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> listWhitelist(context,
                                                IntegerArgumentType.getInteger(context, "page")))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> itemArgument() {
        return Commands.argument("item", ResourceLocationArgument.id())
                .suggests(DroppedItemStackLimitCommand::suggestItems);
    }

    private static CompletableFuture<Suggestions> suggestItems(CommandContext<CommandSourceStack> context,
                                                                SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(
                //#if MC >= 1.19.3
                BuiltInRegistries.ITEM.keySet()
                //#else
                //$$ Registry.ITEM.keySet()
                //#endif
                , builder);
    }

    private static int setAllMode(CommandContext<CommandSourceStack> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        return mutate(context, () -> DroppedItemStackLimitConfig.setAllMode(count),
                "掉落物堆叠模式已设为 all，数量：" + count);
    }

    private static int setInventoryLimit(CommandContext<CommandSourceStack> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        return mutate(context, () -> DroppedItemStackLimitConfig.setInventoryLimit(count),
                "player inventory stack limit set to " + count);
    }

    private static int setContainerLimit(CommandContext<CommandSourceStack> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        return mutate(context, () -> DroppedItemStackLimitConfig.setContainerLimit(count),
                "container stack limit set to " + count);
    }

    private static int resetInventoryLimit(CommandContext<CommandSourceStack> context) {
        return mutate(context, DroppedItemStackLimitConfig::resetInventoryLimit,
                "player inventory stack limit reset to vanilla");
    }

    private static int resetContainerLimit(CommandContext<CommandSourceStack> context) {
        return mutate(context, DroppedItemStackLimitConfig::resetContainerLimit,
                "container stack limit reset to vanilla");
    }

    private static int setBlackMode(CommandContext<CommandSourceStack> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        return mutate(context, () -> DroppedItemStackLimitConfig.setBlackMode(count),
                "掉落物堆叠模式已设为 black，非黑名单数量：" + count);
    }

    private static int setWhitelistMode(CommandContext<CommandSourceStack> context) {
        return mutate(context, DroppedItemStackLimitConfig::setWhitelistMode,
                "掉落物堆叠模式已设为 whitelist");
    }

    private static int addBlacklist(CommandContext<CommandSourceStack> context) {
        ResourceLocation itemId = getRegisteredItem(context);
        if (itemId == null) {
            return 0;
        }
        try {
            if (!DroppedItemStackLimitConfig.addBlacklist(itemId)) {
                context.getSource().sendFailure(Component.literal("物品已在黑名单中：" + itemId));
                return 0;
            }
            sendSuccess(context, "已加入黑名单：" + itemId);
            return 1;
        } catch (IOException exception) {
            return configurationFailure(context, exception);
        }
    }

    private static int removeBlacklist(CommandContext<CommandSourceStack> context) {
        ResourceLocation itemId = getRegisteredItem(context);
        if (itemId == null) {
            return 0;
        }
        try {
            if (!DroppedItemStackLimitConfig.removeBlacklist(itemId)) {
                context.getSource().sendFailure(Component.literal("物品不在黑名单中：" + itemId));
                return 0;
            }
            sendSuccess(context, "已从黑名单删除：" + itemId);
            return 1;
        } catch (IOException exception) {
            return configurationFailure(context, exception);
        }
    }

    private static int setWhitelistItem(CommandContext<CommandSourceStack> context) {
        ResourceLocation itemId = getRegisteredItem(context);
        if (itemId == null) {
            return 0;
        }
        int count = IntegerArgumentType.getInteger(context, "count");
        return mutate(context, () -> DroppedItemStackLimitConfig.setWhitelistItem(itemId, count),
                "已设置白名单物品：" + itemId + " -> " + count);
    }

    private static int removeWhitelistItem(CommandContext<CommandSourceStack> context) {
        ResourceLocation itemId = getRegisteredItem(context);
        if (itemId == null) {
            return 0;
        }
        try {
            if (!DroppedItemStackLimitConfig.removeWhitelistItem(itemId)) {
                context.getSource().sendFailure(Component.literal("物品不在白名单中：" + itemId));
                return 0;
            }
            sendSuccess(context, "已从白名单删除：" + itemId);
            return 1;
        } catch (IOException exception) {
            return configurationFailure(context, exception);
        }
    }

    private static int clearActiveList(CommandContext<CommandSourceStack> context) {
        DroppedItemStackLimitConfig.State current = DroppedItemStackLimitConfig.snapshot();
        if (current.mode() == DroppedItemStackLimitConfig.Mode.ALL) {
            context.getSource().sendFailure(Component.literal("all 模式没有名单可清空"));
            return 0;
        }
        try {
            int removed = DroppedItemStackLimitConfig.clearActiveList();
            sendSuccess(context, "已清空当前 " + current.mode().serializedName() + " 名单，共删除 " + removed + " 项");
            return 1;
        } catch (IOException exception) {
            return configurationFailure(context, exception);
        }
    }

    private static int showSummary(CommandContext<CommandSourceStack> context) {
        DroppedItemStackLimitConfig.State current = DroppedItemStackLimitConfig.snapshot();
        MutableComponent message = Component.literal("掉落物堆叠配置\n")
                .append(Component.literal("规则：" +
                        //#if MC >= 1.21.1
                        FGASettings.droppedItemStackLimit
                        //#else
                        //$$ (FGASettings.droppedItemStackLimit ? "true" : "false")
                        //#endif
                        + "\n"))
                .append(Component.literal("模式：" + current.mode().serializedName() + "\n"))
                .append(Component.literal("all 数量：" + current.allLimit() + "\n"))
                .append(Component.literal("black 数量：" + current.blackLimit() + "，黑名单 "
                        + current.blacklist().size() + " 项\n"))
                .append(Component.literal("whitelist：" + current.whitelist().size() + " 项"));
        if (DroppedItemStackLimitConfig.isStackSizeTweaksCompatibilityActive()) {
            message.append(Component.literal("\nStack Size Tweaks 兼容模式 / compatibility mode: active")
                    .withStyle(ChatFormatting.GREEN));
        }
        if (DroppedItemStackLimitConfig.isLoadFailed()) {
            message.append(Component.literal("\n配置文件损坏，当前安全回退到原版").withStyle(ChatFormatting.RED));
        }
        FGACompat.sendSuccess(context.getSource(), message, false);
        return 1;
    }

    private static int listBlacklist(CommandContext<CommandSourceStack> context, int page) {
        List<ResourceLocation> entries = DroppedItemStackLimitConfig.snapshot().sortedBlacklist();
        return showPage(context, "black", entries.size(), page, index -> {
            ResourceLocation itemId = entries.get(index);
            return itemLine(itemId, null, "black");
        });
    }

    private static int listWhitelist(CommandContext<CommandSourceStack> context, int page) {
        List<Map.Entry<ResourceLocation, Integer>> entries = DroppedItemStackLimitConfig.snapshot().sortedWhitelist();
        return showPage(context, "whitelist", entries.size(), page, index -> {
            Map.Entry<ResourceLocation, Integer> entry = entries.get(index);
            return itemLine(entry.getKey(), entry.getValue(), "whitelist");
        });
    }

    private static int showPage(CommandContext<CommandSourceStack> context, String listName, int size, int page,
                                java.util.function.IntFunction<MutableComponent> lineFactory) {
        int pages = Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) {
            context.getSource().sendFailure(Component.literal("页码超出范围：1-" + pages));
            return 0;
        }
        MutableComponent message = Component.literal(listName + " 名单，第 " + page + "/" + pages + " 页，共 " + size + " 项");
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(size, start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            message.append(Component.literal("\n")).append(lineFactory.apply(index));
        }
        FGACompat.sendSuccess(context.getSource(), message, false);
        return 1;
    }

    private static MutableComponent itemLine(ResourceLocation itemId, Integer count, String listName) {
        Item item =
                //#if MC >= 1.21.2
                //$$ BuiltInRegistries.ITEM.getValue(itemId);
                //#else
                //#if MC >= 1.19.3
                BuiltInRegistries.ITEM.get(itemId);
                //#else
                //$$ Registry.ITEM.get(itemId);
                //#endif
                //#endif
        MutableComponent line = Component.literal(itemId.toString()).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                .append(new ItemStack(item).getHoverName())
                .append(Component.literal(")").withStyle(ChatFormatting.GRAY));
        if (count != null) {
            line.append(Component.literal(" -> " + count).withStyle(ChatFormatting.AQUA));
        }
        String removeCommand = "/droppedItemStackLimit remove " + listName + " " + itemId;
        Style removeStyle = Style.EMPTY.withColor(ChatFormatting.RED)
                .withClickEvent(
                        //#if MC >= 1.21.5
                        //$$ new ClickEvent.RunCommand(removeCommand)
                        //#else
                        new ClickEvent(ClickEvent.Action.RUN_COMMAND, removeCommand)
                        //#endif
                )
                .withHoverEvent(
                        //#if MC >= 1.21.5
                        //$$ new HoverEvent.ShowText(Component.literal("点击删除 " + itemId))
                        //#else
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击删除 " + itemId))
                        //#endif
                );
        return line.append(Component.literal(" [-]").setStyle(removeStyle));
    }

    private static ResourceLocation getRegisteredItem(CommandContext<CommandSourceStack> context) {
        ResourceLocation itemId = ResourceLocationArgument.getId(context, "item");
        if (!
                //#if MC >= 1.19.3
                BuiltInRegistries.ITEM.containsKey(itemId)
                //#else
                //$$ Registry.ITEM.containsKey(itemId)
                //#endif
        ) {
            context.getSource().sendFailure(Component.literal("未注册的物品 ID：" + itemId));
            return null;
        }
        return itemId;
    }

    private static int mutate(CommandContext<CommandSourceStack> context, IoAction action, String successMessage) {
        try {
            action.run();
            sendSuccess(context, successMessage);
            return 1;
        } catch (IOException | IllegalArgumentException exception) {
            return configurationFailure(context, exception);
        }
    }

    private static int configurationFailure(CommandContext<CommandSourceStack> context, Exception exception) {
        context.getSource().sendFailure(Component.literal("无法更新掉落物堆叠配置：" + exception.getMessage()));
        return 0;
    }

    private static void sendSuccess(CommandContext<CommandSourceStack> context, String message) {
        FGACompat.sendSuccess(context.getSource(), Component.literal(message), true);
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }
}
//#endif
