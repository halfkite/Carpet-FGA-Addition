package carpet.fga;

//#if MC == 1.21.1
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Commands for configuring per-entity death-drop filters on Minecraft 1.21.1. */
public final class EntityDropRemovalCommand {
    private EntityDropRemovalCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("entityDropRemoval"));
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .requires(EntityDropRemovalConfig::canUseCommand)
                .executes(EntityDropRemovalCommand::help)
                .then(Commands.literal("help").executes(EntityDropRemovalCommand::help))
                .then(Commands.literal("status").executes(EntityDropRemovalCommand::status))
                .then(Commands.literal("set")
                        .then(entityArgument()
                                .then(dropArgument().executes(EntityDropRemovalCommand::set))))
                .then(Commands.literal("remove")
                        .then(entityArgument()
                                .then(dropArgument().executes(EntityDropRemovalCommand::remove))))
                .then(Commands.literal("list")
                        .executes(EntityDropRemovalCommand::listAll)
                        .then(entityArgument().executes(EntityDropRemovalCommand::listEntity)));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> entityArgument() {
        return Commands.argument("entity", StringArgumentType.word())
                .suggests(EntityDropRemovalCommand::entitySuggestions);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> dropArgument() {
        return Commands.argument("drop", StringArgumentType.word())
                .suggests(EntityDropRemovalCommand::dropSuggestions);
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        try {
            ResourceLocation entityId = entityId(context);
            String drop = StringArgumentType.getString(context, "drop");
            if (EntityDropRemovalConfig.ALL_EQUIPMENT.equals(drop)) {
                EntityDropRemovalConfig.setAllEquipment(entityId);
            } else {
                EntityDropRemovalConfig.setItem(entityId, EntityDropRemovalConfig.parseItemId(drop));
            }
            return success(context, "已添加生物掉落去除配置 / Added entity drop removal: "
                    + entityId + " -> " + drop);
        } catch (Exception exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        try {
            ResourceLocation entityId = entityId(context);
            String drop = StringArgumentType.getString(context, "drop");
            boolean removed = EntityDropRemovalConfig.ALL_EQUIPMENT.equals(drop)
                    ? EntityDropRemovalConfig.removeAllEquipment(entityId)
                    : EntityDropRemovalConfig.removeItem(entityId, EntityDropRemovalConfig.parseItemId(drop));
            if (!removed) return failure(context, "未找到配置 / Not configured: " + entityId + " -> " + drop);
            return success(context, "已移除生物掉落去除配置 / Removed entity drop removal: "
                    + entityId + " -> " + drop);
        } catch (Exception exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int listAll(CommandContext<CommandSourceStack> context) {
        Map<ResourceLocation, EntityDropRemovalConfig.Entry> entries = EntityDropRemovalConfig.snapshot().entities();
        MutableComponent message = FGACompat.literal("生物掉落物去除配置 / Entity drop removal\n")
                .withStyle(ChatFormatting.GOLD);
        if (entries.isEmpty()) {
            message.append(FGACompat.literal("暂无配置 / No entries configured").withStyle(ChatFormatting.GRAY));
        } else {
            entries.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                    .forEach(entry -> appendConfiguredEntry(message, entry.getKey(), entry.getValue()));
        }
        FGACompat.sendSuccess(context.getSource(), message, false);
        return 1;
    }

    private static void appendConfiguredEntry(MutableComponent message, ResourceLocation entityId,
                                               EntityDropRemovalConfig.Entry entry) {
        message.append(FGACompat.literal("\n" + entityId + "  ").withStyle(ChatFormatting.GRAY));
        for (ResourceLocation itemId : sorted(entry.items())) {
            appendRemoveButton(message, "/entityDropRemoval remove " + entityId + " " + itemId,
                    itemId.toString());
        }
        if (entry.allEquipment()) {
            appendRemoveButton(message, "/entityDropRemoval remove " + entityId + " "
                    + EntityDropRemovalConfig.ALL_EQUIPMENT, EntityDropRemovalConfig.ALL_EQUIPMENT);
        }
    }

    private static int listEntity(CommandContext<CommandSourceStack> context) {
        try {
            ResourceLocation entityId = entityId(context);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityId);
            EntityDropRemovalConfig.Entry entry = EntityDropRemovalConfig.entry(entityId);
            MutableComponent message = FGACompat.literal("生物掉落物 / Entity drops: " + entityId + "\n")
                    .withStyle(ChatFormatting.GOLD);
            var lootTable = type.getDefaultLootTable();
            message.append(FGACompat.literal("战利品表 / loot table: "
                    + (lootTable == null ? "none" : lootTable.location()) + "\n")
                    .withStyle(ChatFormatting.GRAY));
            Set<ResourceLocation> lootItems = lootTable == null ? Set.of()
                    : lootTableItems(context, lootTable.location());
            message.append(FGACompat.literal("原版战利品表 / Vanilla loot table drops:\n")
                    .withStyle(ChatFormatting.YELLOW));
            if (lootItems.isEmpty()) {
                message.append(FGACompat.literal("未解析到物品项 / No item entries found\n")
                        .withStyle(ChatFormatting.GRAY));
            } else {
                for (ResourceLocation itemId : sorted(lootItems)) {
                    if (!entry.items().contains(itemId)) appendDropLine(message, entityId, itemId, false);
                }
            }
            if (!entry.allEquipment()) {
                appendEquipmentLine(message, entityId, false);
            }
            if (entry.items().isEmpty() && !entry.allEquipment()) {
                message.append(FGACompat.literal("暂无去除配置 / No removal configured")
                        .withStyle(ChatFormatting.GRAY));
            } else {
                message.append(FGACompat.literal("已去除的掉落物 / Removed drops:\n")
                        .withStyle(ChatFormatting.YELLOW));
                for (ResourceLocation itemId : sorted(entry.items())) {
                    appendDropLine(message, entityId, itemId, true);
                }
                if (entry.allEquipment()) {
                    appendEquipmentLine(message, entityId, true);
                }
            }
            FGACompat.sendSuccess(context.getSource(), message, false);
            return 1;
        } catch (Exception exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        EntityDropRemovalConfig.State state = EntityDropRemovalConfig.snapshot();
        return success(context, "entityDropRemoval=" + FGASettings.entityDropRemoval
                + "; entities=" + state.entities().size()
                + (EntityDropRemovalConfig.isLoadFailed() ? "; 配置文件损坏 / configuration invalid" : ""));
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        MutableComponent message = FGACompat.literal("生物掉落物自定义去除 / Entity drop removal\n")
                .withStyle(ChatFormatting.GOLD);
        line(message, "/carpet entityDropRemoval true", "开启功能 / enable the feature");
        line(message, "/entityDropRemoval set <entity> <item>", "去除指定物品 / remove an item");
        line(message, "/entityDropRemoval set <entity> allEquipment", "去除六个装备槽 / remove six equipment slots");
        line(message, "/entityDropRemoval remove <entity> <item|allEquipment>", "删除配置 / delete a setting");
        line(message, "/entityDropRemoval list", "查看全部配置 / list configured entities");
        line(message, "/entityDropRemoval list <entity>", "查看指定生物 / inspect one entity");
        FGACompat.sendSuccess(context.getSource(), message, false);
        return 1;
    }

    private static ResourceLocation entityId(CommandContext<CommandSourceStack> context) {
        return EntityDropRemovalConfig.parseEntityId(StringArgumentType.getString(context, "entity"));
    }

    private static CompletableFuture<Suggestions> entitySuggestions(CommandContext<CommandSourceStack> context,
                                                                     SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            suggest(builder, prefix, id);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> dropSuggestions(CommandContext<CommandSourceStack> context,
                                                                   SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
        suggest(builder, prefix, EntityDropRemovalConfig.ALL_EQUIPMENT);
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) suggest(builder, prefix, id);
        return builder.buildFuture();
    }

    private static void suggest(SuggestionsBuilder builder, String prefix, ResourceLocation id) {
        if (id.toString().startsWith(prefix)) builder.suggest(id.toString());
        if ("minecraft".equals(id.getNamespace()) && id.getPath().startsWith(prefix)) builder.suggest(id.getPath());
    }

    private static void suggest(SuggestionsBuilder builder, String prefix, String value) {
        if (value.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) builder.suggest(value);
    }

    private static List<ResourceLocation> sorted(Iterable<ResourceLocation> values) {
        List<ResourceLocation> result = new ArrayList<>();
        values.forEach(result::add);
        result.sort(Comparator.comparing(ResourceLocation::toString));
        return result;
    }

    private static Set<ResourceLocation> lootTableItems(CommandContext<CommandSourceStack> context,
                                                        ResourceLocation lootTableId) {
        ResourceLocation resourceId = ResourceLocation.tryParse(lootTableId.getNamespace()
                + ":loot_table/" + lootTableId.getPath() + ".json");
        if (resourceId == null) return Set.of();
        try {
            var resource = context.getSource().getServer().getResourceManager().getResource(resourceId);
            if (resource.isEmpty()) return Set.of();
            try (var reader = resource.get().openAsReader()) {
                Set<ResourceLocation> result = new java.util.LinkedHashSet<>();
                collectLootItems(JsonParser.parseReader(reader), result);
                return result;
            }
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private static void collectLootItems(JsonElement element, Set<ResourceLocation> result) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("type") && "minecraft:item".equals(object.get("type").getAsString())
                    && object.has("name")) {
                ResourceLocation itemId = EntityDropRemovalConfig.parseItemIdQuiet(object.get("name").getAsString());
                if (itemId != null) result.add(itemId);
            }
            for (Map.Entry<String, JsonElement> child : object.entrySet()) collectLootItems(child.getValue(), result);
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) collectLootItems(child, result);
        }
    }

    private static void appendDropLine(MutableComponent message, ResourceLocation entityId,
                                       ResourceLocation itemId, boolean removed) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        MutableComponent label = new ItemStack(item).getHoverName().copy();
        message.append(label.withStyle(ChatFormatting.WHITE))
                .append(FGACompat.literal(" / " + itemId + " ").withStyle(ChatFormatting.GRAY));
        String action = removed ? "remove" : "set";
        String symbol = removed ? "[+]" : "[-]";
        message.append(FGACompat.literal(symbol).withStyle(Style.EMPTY.withColor(
                removed ? ChatFormatting.GREEN : ChatFormatting.RED)
                .withClickEvent(FgaClickEvents.runCommand("/entityDropRemoval " + action + " "
                        + entityId + " " + itemId)))
                )
                .append(FGACompat.literal("\n"));
    }

    private static void appendEquipmentLine(MutableComponent message, ResourceLocation entityId,
                                            boolean removed) {
        message.append(FGACompat.literal("装备 / Equipment ").withStyle(ChatFormatting.WHITE));
        String action = removed ? "remove" : "set";
        String symbol = removed ? "[+]" : "[-]";
        message.append(FGACompat.literal(symbol).withStyle(Style.EMPTY.withColor(
                removed ? ChatFormatting.GREEN : ChatFormatting.RED)
                .withClickEvent(FgaClickEvents.runCommand("/entityDropRemoval " + action + " "
                        + entityId + " " + EntityDropRemovalConfig.ALL_EQUIPMENT)))
                )
                .append(FGACompat.literal("\n"));
    }

    private static void appendRemoveButton(MutableComponent message, String command, String text) {
        message.append(FGACompat.literal("[" + text + "]").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
                .append(FGACompat.literal(" [-]\n").withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                        .withClickEvent(FgaClickEvents.runCommand(command))));
    }

    private static void line(MutableComponent message, String command, String description) {
        message.append(FGACompat.literal(command).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                        .withClickEvent(FgaClickEvents.suggestCommand(command))))
                .append(FGACompat.literal("  # " + description + "\n").withStyle(ChatFormatting.GOLD));
    }

    private static int success(CommandContext<CommandSourceStack> context, String message) {
        FGACompat.sendSuccess(context.getSource(), FGACompat.literal(message).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int failure(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendFailure(FGACompat.literal(message == null ? "unknown error" : message));
        return 0;
    }
}
//#endif
