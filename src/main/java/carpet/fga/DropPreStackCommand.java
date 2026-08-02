//#if MC >= 1.20.5 && MC < 26.2
package carpet.fga;

import carpet.CarpetSettings;
import carpet.utils.CommandHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
//#if MC >= 1.21.3
//$$ import net.minecraft.world.entity.EntitySpawnReason;
//#endif
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class DropPreStackCommand {
    private static final int PAGE_SIZE = 10;
    private static final Map<String, List<ResourceLocation>> ITEM_NAMES = loadNames(false);
    private static final Map<String, List<ResourceLocation>> ENTITY_NAMES = loadNames(true);

    private DropPreStackCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("dropPreStack"));
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .requires(source -> CommandHelper.canUseCommand(source, CarpetSettings.commandPlayer))
                .executes(DropPreStackCommand::help)
                .then(Commands.literal("help").executes(DropPreStackCommand::help))
                .then(Commands.literal("status").executes(DropPreStackCommand::status))
                .then(collection("entity", true))
                .then(collection("block", false))
                .then(containerCollection());
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> containerCollection() {
        return Commands.literal("container")
                .then(Commands.literal("add").then(containerIdArgument()
                        .executes(c -> addContainer(c, false))
                        .then(rangeArgument().executes(c -> addContainer(c, false)))))
                .then(Commands.literal("remove").then(containerIdArgument()
                        .suggests((c, b) -> configuredContainerSuggestions(c, b))
                        .executes(DropPreStackCommand::removeContainer)))
                .then(Commands.literal("set").then(containerIdArgument()
                        .executes(c -> addContainer(c, true))
                        .then(rangeArgument().executes(c -> addContainer(c, true)))))
                .then(Commands.literal("list").executes(c -> listContainers(c, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(c -> listContainers(c, IntegerArgumentType.getInteger(c, "page")))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> containerIdArgument() {
        return Commands.argument("id", ResourceLocationArgument.id())
                .suggests(DropPreStackCommand::containerIdSuggestions);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> collection(
            String type, boolean entity) {
        return Commands.literal(type)
                .then(Commands.literal("add").then(idArgument(entity)
                        .executes(c -> add(c, entity, false))
                        .then(rangeArgument().executes(c -> add(c, entity, false)))))
                .then(Commands.literal("remove").then(idArgument(entity)
                        .suggests((c, b) -> configuredSuggestions(c, b, entity))
                        .executes(c -> remove(c, entity))))
                .then(Commands.literal("set").then(idArgument(entity)
                        .executes(c -> add(c, entity, true))
                        .then(rangeArgument().executes(c -> add(c, entity, true)))))
                .then(Commands.literal("list").executes(c -> list(c, entity, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(c -> list(c, entity, IntegerArgumentType.getInteger(c, "page")))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> idArgument(boolean entity) {
        return Commands.argument("id", ResourceLocationArgument.id())
                .suggests((c, b) -> resourceSuggestions(c, b, entity));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Double> rangeArgument() {
        return Commands.argument("range", DoubleArgumentType.doubleArg(0.0D, 16.0D));
    }

    private static String idInput(CommandContext<CommandSourceStack> context) {
        return ResourceLocationArgument.getId(context, "id").toString();
    }

    private static int add(CommandContext<CommandSourceStack> context, boolean entity, boolean replace) {
        try {
            ResourceLocation id = resolveId(idInput(context), entity);
            double range = context.getNodes().stream().anyMatch(node -> "range".equals(node.getNode().getName()))
                    ? DoubleArgumentType.getDouble(context, "range") : DropPreStackConfig.defaultRange();
            if (entity) {
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) throw new IllegalArgumentException("unknown entity id: " + id);
                if (replace) DropPreStackConfig.setEntity(id, range);
                else DropPreStackConfig.addEntity(id, range);
            } else {
                if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("unknown item id: " + id);
                if (replace) DropPreStackConfig.setBlock(id, range);
                else DropPreStackConfig.addBlock(id, range);
            }
            return success(context, (replace ? "Updated / 已更新: " : "Added / 已添加: ") + display(id, entity)
                    + " range=" + format(range));
        } catch (Exception exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int remove(CommandContext<CommandSourceStack> context, boolean entity) {
        try {
            ResourceLocation id = resolveId(idInput(context), entity);
            boolean removed = entity ? DropPreStackConfig.removeEntity(id) : DropPreStackConfig.removeBlock(id);
            return removed ? success(context, "Removed / 已移除: " + display(id, entity))
                    : failure(context, "Not configured / 未找到配置: " + id);
        } catch (Exception exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int list(CommandContext<CommandSourceStack> context, boolean entity, int page) {
        List<Map.Entry<ResourceLocation, Double>> entries = new ArrayList<>(
                entity ? DropPreStackConfig.snapshot().entities().entrySet() : DropPreStackConfig.snapshot().blocks().entrySet());
        entries.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) return failure(context, "Page out of range / 页码超出范围: 1-" + pages);
        MutableComponent message = Component.literal((entity ? "Entities / 生物" : "Block drops / 方块掉落物")
                + " " + page + "/" + pages + " (" + entries.size() + ")").withStyle(ChatFormatting.GOLD);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(entries.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            Map.Entry<ResourceLocation, Double> entry = entries.get(index);
            String remove = "/dropPreStack " + (entity ? "entity" : "block") + " remove " + entry.getKey();
            String set = "/dropPreStack " + (entity ? "entity" : "block") + " set " + entry.getKey() + " ";
            message.append(Component.literal("\n").withStyle(ChatFormatting.GRAY))
                    .append(displayComponent(entry.getKey(), entity).setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                            .withClickEvent(FgaClickEvents.suggestCommand(set))))
                    .append(Component.literal("  ").withStyle(ChatFormatting.GRAY))
                    .append(editable(entry.getKey().toString(), set, ChatFormatting.YELLOW))
                    .append(editable("  range=" + format(entry.getValue()), set, ChatFormatting.AQUA))
                    .append(Component.literal(" [-]").setStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                            .withClickEvent(FgaClickEvents.runCommand(remove))));
        }
        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        DropPreStackConfig.State state = DropPreStackConfig.snapshot();
        return success(context, "preStackDroppedItems=" + FGASettings.preStackDroppedItems
                + "; entities=" + state.entities().size() + "; block drops=" + state.blocks().size()
                + "; containers=" + (state.containerEntities().size() + state.containerBlocks().size())
                + (DropPreStackConfig.isLoadFailed() ? " / configuration invalid, new entries disabled" : ""));
    }

    private static int addContainer(CommandContext<CommandSourceStack> context, boolean replace) {
        try {
            ContainerTarget target = resolveContainer(idInput(context), context);
            double range = context.getNodes().stream().anyMatch(node -> "range".equals(node.getNode().getName()))
                    ? DoubleArgumentType.getDouble(context, "range") : DropPreStackConfig.defaultRange();
            if (target.entity()) {
                if (replace) DropPreStackConfig.setContainerEntity(target.id(), range);
                else DropPreStackConfig.addContainerEntity(target.id(), range);
            } else {
                if (replace) DropPreStackConfig.setContainerBlock(target.id(), range);
                else DropPreStackConfig.addContainerBlock(target.id(), range);
            }
            return success(context, (replace ? "Updated / 已更新 " : "Added / 已添加 ")
                    + displayComponent(target.id(), target.entity()).getString() + " range=" + format(range));
        } catch (Exception exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int removeContainer(CommandContext<CommandSourceStack> context) {
        try {
            ContainerTarget target = resolveConfiguredContainer(idInput(context), context);
            boolean removed = target.entity()
                    ? DropPreStackConfig.removeContainerEntity(target.id())
                    : DropPreStackConfig.removeContainerBlock(target.id());
            return removed ? success(context, "Removed / 已移除 " + target.id())
                    : failure(context, "Not configured / 未找到配置 " + target.id());
        } catch (Exception exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static ContainerTarget resolveContainer(String raw, CommandContext<CommandSourceStack> context) {
        ResourceLocation entityId = resolveRegisteredId(raw, true);
        if (entityId != null && isContainerEntity(context, entityId)) {
            return new ContainerTarget(entityId, true);
        }
        ResourceLocation itemId = resolveId(raw, false);
        Item item = itemAt(itemId);
        Block block = item == null ? null : Block.byItem(item);
        ResourceLocation blockId = block == null ? null : BuiltInRegistries.BLOCK.getKey(block);
        if (blockId != null && isContainerBlock(blockId)) {
            return new ContainerTarget(blockId, false);
        }
        throw new IllegalArgumentException("not a supported container entity or block / 不是支持的容器实体或方块: " + raw);
    }

    private static ContainerTarget resolveConfiguredContainer(String raw, CommandContext<CommandSourceStack> context) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        String full = value.contains(":") ? value : "minecraft:" + value;
        for (ResourceLocation id : DropPreStackConfig.snapshot().containerEntities().keySet()) {
            if (id.toString().equals(full)) return new ContainerTarget(id, true);
        }
        for (ResourceLocation id : DropPreStackConfig.snapshot().containerBlocks().keySet()) {
            if (id.toString().equals(full)) return new ContainerTarget(id, false);
        }
        List<ResourceLocation> entityMatches = entityNameCandidates(raw.trim());
        for (ResourceLocation id : entityMatches) {
            if (DropPreStackConfig.snapshot().containerEntities().containsKey(id)) {
                return new ContainerTarget(id, true);
            }
        }
        ResourceLocation blockId = resolveContainer(raw, context).id();
        boolean entity = DropPreStackConfig.snapshot().containerEntities().containsKey(blockId);
        return new ContainerTarget(blockId, entity);
    }

    private static CompletableFuture<Suggestions> containerIdSuggestions(CommandContext<CommandSourceStack> context,
                                                                          SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            if (isContainerEntity(context, id)) suggestResource(builder, prefix, id);
        }
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            if (isContainerBlock(id)) suggestResource(builder, prefix, id);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> configuredContainerSuggestions(CommandContext<CommandSourceStack> context,
                                                                                  SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        DropPreStackConfig.snapshot().containerEntities().keySet()
                .forEach(id -> suggestResource(builder, prefix, id));
        DropPreStackConfig.snapshot().containerBlocks().keySet()
                .forEach(id -> suggestResource(builder, prefix, id));
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> resourceSuggestions(CommandContext<CommandSourceStack> context,
                                                                      SuggestionsBuilder builder, boolean entity) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        Iterable<ResourceLocation> ids = entity ? BuiltInRegistries.ENTITY_TYPE.keySet() : BuiltInRegistries.ITEM.keySet();
        for (ResourceLocation id : ids) suggestResource(builder, prefix, id);
        if (!entity) {
            for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
                suggestResource(builder, prefix, id);
            }
        }
        return builder.buildFuture();
    }

    private static ResourceLocation resolveRegisteredId(String raw, boolean entity) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (!value.contains(":")) value = "minecraft:" + value;
        Iterable<ResourceLocation> keys = entity ? BuiltInRegistries.ENTITY_TYPE.keySet() : BuiltInRegistries.ITEM.keySet();
        for (ResourceLocation id : keys) if (id.toString().equals(value)) return id;
        return null;
    }

    private static boolean isContainerBlock(ResourceLocation id) {
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return false;
        Block block = blockAt(id);
        if (block == null) return false;
        var state = block.defaultBlockState();
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            if (!type.isValid(state)) continue;
            BlockEntity entity = type.create(BlockPos.ZERO, state);
            if (entity instanceof Container) return true;
        }
        return false;
    }

    private static int listContainers(CommandContext<CommandSourceStack> context, int page) {
        List<ContainerEntry> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Double> entry : DropPreStackConfig.snapshot().containerEntities().entrySet()) {
            if (isContainerEntity(context, entry.getKey())) {
                entries.add(new ContainerEntry("entity", entry.getKey(), entry.getValue(), true));
            }
        }
        for (Map.Entry<ResourceLocation, Double> entry : DropPreStackConfig.snapshot().containerBlocks().entrySet()) {
            if (isContainerBlock(entry.getKey())) {
                entries.add(new ContainerEntry("block", entry.getKey(), entry.getValue(), false));
            }
        }
        entries.sort(Comparator.comparing((ContainerEntry entry) -> entry.kind)
                .thenComparing(entry -> entry.id.toString()));
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) return failure(context, "Page out of range / page out of range: 1-" + pages);
        MutableComponent message = Component.literal("Containers / 容器 " + page + "/" + pages
                + " (" + entries.size() + ")").withStyle(ChatFormatting.GOLD);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(entries.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            ContainerEntry entry = entries.get(index);
            String remove = "/dropPreStack " + entry.kind + " remove " + entry.id;
            String set = "/dropPreStack container set " + entry.id + " ";
            message.append(Component.literal("\n").withStyle(ChatFormatting.GRAY))
                    .append(displayComponent(entry.id, entry.entity).setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                            .withClickEvent(FgaClickEvents.suggestCommand(set))))
                    .append(editable("  " + entry.id + "  " + entry.kind
                            + "  range=" + format(entry.range), set, ChatFormatting.YELLOW))
                    .append(Component.literal(" [-]").setStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                            .withClickEvent(FgaClickEvents.runCommand(remove))));
        }
        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static boolean isContainerEntity(CommandContext<CommandSourceStack> context, ResourceLocation id) {
        EntityType<?> type = entityTypeAt(id);
        if (type == null) return false;
        Entity entity =
                //#if MC >= 1.21.3
                //$$ type.create(context.getSource().getLevel(), EntitySpawnReason.COMMAND);
                //#else
                type.create(context.getSource().getLevel());
                //#endif
        try {
            return entity instanceof AbstractMinecartContainer;
        } finally {
            if (entity != null) entity.discard();
        }
    }

    private static boolean isContainerItem(ResourceLocation id) {
        Item item = itemAt(id);
        if (item == null) return false;
        Block block = Block.byItem(item);
        if (block == null) return false;
        var state = block.defaultBlockState();
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            if (!type.isValid(state)) continue;
            BlockEntity entity = type.create(BlockPos.ZERO, state);
            if (entity instanceof Container) return true;
        }
        return false;
    }

    private static Item itemAt(ResourceLocation id) {
        //#if MC >= 1.21.3
        //$$ return BuiltInRegistries.ITEM.get(id).map(reference -> reference.value()).orElse(null);
        //#else
        return BuiltInRegistries.ITEM.get(id);
        //#endif
    }

    private static Block blockAt(ResourceLocation id) {
        //#if MC >= 1.21.3
        //$$ return BuiltInRegistries.BLOCK.get(id).map(reference -> reference.value()).orElse(null);
        //#else
        return BuiltInRegistries.BLOCK.get(id);
        //#endif
    }

    private static EntityType<?> entityTypeAt(ResourceLocation id) {
        //#if MC >= 1.21.3
        //$$ return BuiltInRegistries.ENTITY_TYPE.get(id).map(reference -> reference.value()).orElse(null);
        //#else
        return BuiltInRegistries.ENTITY_TYPE.get(id);
        //#endif
    }

    private record ContainerEntry(String kind, ResourceLocation id, double range, boolean entity) {
    }

    private record ContainerTarget(ResourceLocation id, boolean entity) {
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        MutableComponent message = Component.literal("Drop pre-stacking help / 掉落物预堆叠帮助\n")
                .withStyle(ChatFormatting.GOLD);
        line(message, "/carpet preStackDroppedItems true", "Enable unified entity and block-drop pre-stacking / 开启统一预堆叠");
        line(message, "/dropPreStack entity add <id> [range]", "Configure mob drops / 配置生物掉落");
        line(message, "/dropPreStack block add <item|中文名> [range]", "Configure block-drop items / 配置方块掉落物");
        line(message, "/dropPreStack entity|block set <id> [range]", "Update one entry; default range 1 / 更新单项，默认范围 1");
        line(message, "/dropPreStack entity|block remove <id>", "Remove one entry / 移除单项");
        line(message, "/dropPreStack entity|block list", "List Chinese name, English ID and range / 查看中文名、英文 ID 和范围");
        line(message, "/dropPreStack container list", "List configured hopper minecarts and container block drops separately / 单独查看漏斗矿车与容器方块掉落");
        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static void line(MutableComponent message, String command, String description) {
        message.append(Component.literal(command).setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                        .withClickEvent(FgaClickEvents.suggestCommand(command))))
                .append(Component.literal("  # " + description + "\n").withStyle(ChatFormatting.GOLD));
    }

    private static ResourceLocation resolveId(String raw, boolean entity) {
        ResourceLocation parsed = null;
        try {
            parsed = DropPreStackConfig.parseId(raw);
        } catch (IllegalArgumentException ignored) {
            // A block argument may be an official Chinese display name.
        }
        if (parsed != null && entity && BuiltInRegistries.ENTITY_TYPE.containsKey(parsed)) return parsed;
        if (parsed != null && !entity) {
            if (BuiltInRegistries.ITEM.containsKey(parsed)) return parsed;
            if (BuiltInRegistries.BLOCK.containsKey(parsed)) {
                Block block = blockAt(parsed);
                Item item = block == null ? null : block.asItem();
                if (item != null) return BuiltInRegistries.ITEM.getKey(item);
            }
        }
        ResourceLocation registered = findRegisteredId(raw, entity);
        if (registered != null) return registered;
        List<ResourceLocation> candidates = entity ? entityNameCandidates(raw.trim()) : itemNameCandidates(raw.trim());
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException(entity
                    ? "unknown entity ID or Chinese name: " + raw
                    : "unknown item ID or Chinese name: " + raw);
        }
        if (candidates.size() > 1) {
            throw new IllegalArgumentException("ambiguous Chinese item name / 中文名称有歧义: " + candidates);
        }
        return candidates.get(0);
    }

    private static ResourceLocation findRegisteredId(String raw, boolean entity) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (!value.contains(":")) value = "minecraft:" + value;
        Iterable<ResourceLocation> keys = entity ? BuiltInRegistries.ENTITY_TYPE.keySet() : BuiltInRegistries.ITEM.keySet();
        for (ResourceLocation key : keys) {
            if (key.toString().equals(value)) return key;
        }
        if (!entity) {
            for (ResourceLocation key : BuiltInRegistries.BLOCK.keySet()) {
                if (!key.toString().equals(value)) continue;
                Block block = blockAt(key);
                Item item = block == null ? null : block.asItem();
                if (item != null) return BuiltInRegistries.ITEM.getKey(item);
            }
        }
        return null;
    }

    private static String display(ResourceLocation id, boolean entity) {
        if (entity) {
            for (Map.Entry<String, List<ResourceLocation>> entry : ITEM_NAMES.entrySet()) {
                if (entry.getValue().contains(id)) return entry.getKey();
            }
            return id.getPath().replace('_', ' ');
        }
        Item item = itemAt(id);
        if (item == null) return id.getPath().replace('_', ' ');
        String translated = findTranslation(item.getDescriptionId());
        return translated == null ? id.getPath().replace('_', ' ') : translated;
    }

    private static MutableComponent displayComponent(ResourceLocation id, boolean entity) {
        return Component.literal(display(id, entity)).withStyle(ChatFormatting.GRAY);
    }

    private static Map<String, List<ResourceLocation>> loadNames(boolean entity) {
        Map<String, List<ResourceLocation>> result = new HashMap<>();
        try (InputStream input = DropPreStackCommand.class.getClassLoader()
                .getResourceAsStream("assets/carpet-fga-addition/lang/minecraft-1.21.1-zh_cn.json")) {
            if (input == null) return Map.of();
            var object = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                boolean match = entity ? entry.getKey().startsWith("entity.")
                        : (entry.getKey().startsWith("item.") || entry.getKey().startsWith("block."));
                if (!match || !entry.getKey().contains(".")) continue;
                String key = entry.getKey().substring(entry.getKey().indexOf('.') + 1);
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null && (entity ? BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                        : BuiltInRegistries.ITEM.containsKey(id))) {
                    result.computeIfAbsent(entry.getValue().getAsString(), ignored -> new ArrayList<>()).add(id);
                }
            }
            result.replaceAll((name, values) -> values.stream().distinct()
                    .sorted(Comparator.comparing(ResourceLocation::toString)).toList());
            return Map.copyOf(result);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static List<ResourceLocation> entityNameCandidates(String name) {
        List<ResourceLocation> result = new ArrayList<>();
        List<ResourceLocation> direct = ENTITY_NAMES.get(name);
        if (direct != null) result.addAll(direct);
        List<ResourceLocation> itemNames = ITEM_NAMES.get(name);
        if (itemNames != null) {
            for (ResourceLocation id : itemNames) {
                if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) result.add(id);
            }
        }
        return result.stream().distinct().sorted(Comparator.comparing(ResourceLocation::toString)).toList();
    }

    private static List<ResourceLocation> itemNameCandidates(String name) {
        List<ResourceLocation> candidates = ITEM_NAMES.get(name);
        if (candidates != null && !candidates.isEmpty()) return candidates;
        return List.of();
    }

    private static String findTranslation(String descriptionId) {
        for (Map.Entry<String, List<ResourceLocation>> entry : ITEM_NAMES.entrySet()) {
            for (ResourceLocation id : entry.getValue()) {
                Item item = itemAt(id);
                if (item != null && item.getDescriptionId().equals(descriptionId)) return entry.getKey();
            }
        }
        return null;
    }

    private static CompletableFuture<Suggestions> configuredSuggestions(CommandContext<CommandSourceStack> context,
                                                                          SuggestionsBuilder builder, boolean entity) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        Map<ResourceLocation, Double> values = entity ? DropPreStackConfig.snapshot().entities() : DropPreStackConfig.snapshot().blocks();
        values.keySet().forEach(id -> suggestResource(builder, prefix, id));
        return builder.buildFuture();
    }

    private static void suggestResource(SuggestionsBuilder builder, String lowerPrefix, ResourceLocation id) {
        suggestIfMatches(builder, lowerPrefix, id.toString());
        if ("minecraft".equals(id.getNamespace())) {
            suggestIfMatches(builder, lowerPrefix, id.getPath());
        }
    }

    private static void suggestIfMatches(SuggestionsBuilder builder, String lowerPrefix, String value) {
        if (value.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) builder.suggest(value);
    }

    private static MutableComponent editable(String text, String command, ChatFormatting color) {
        return Component.literal(text).setStyle(Style.EMPTY.withColor(color)
                .withClickEvent(FgaClickEvents.suggestCommand(command)));
    }

    private static int success(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int failure(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendFailure(Component.literal(message == null ? "operation failed / 操作失败" : message));
        return 0;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
//#endif
