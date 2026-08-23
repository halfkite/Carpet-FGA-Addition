//#if MC >= 1.21 && MC <= 26.2
package carpet.fga;

import com.yiyihehe.quickcraft.litematica.QuickLitematicaEntityPlacementPayloads;
//#if MC >= 1.21.8
//$$ import net.minecraft.nbt.NbtOps;
//#endif
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
//#if MC >= 1.21.11
//$$ import net.minecraft.resources.Identifier;
//#else
import net.minecraft.resources.ResourceLocation;
//#endif
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
//#if MC >= 1.21.8
//$$ import net.minecraft.util.ProblemReporter;
//#endif
import net.minecraft.world.entity.Entity;
//#if MC >= 1.21.3
//$$ import net.minecraft.world.entity.EntitySpawnReason;
//#endif
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//#if MC >= 1.21.8
//$$ import net.minecraft.world.level.storage.TagValueInput;
//#endif
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** QuickCraft entity placement protocol endpoint; all authority stays on this server side. */
public final class QuickCraftEntityPlacementServer {
    private static final double DEFAULT_REACH = 4.5D;
    private static final double MAX_REACH = 64.0D;
    private static final double MAX_SPEED = 4.0D;
    private static final int MAX_ENTITY_NBT_BYTES = 262_144;
    private static final int MAX_ENTITY_TREE_DEPTH = 8;
    private static final int MAX_ENTITY_TREE_SIZE = 16;
    private static final int REQUEST_COOLDOWN_TICKS = 2;
    private static final int MAX_NONCES = 64;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private QuickCraftEntityPlacementServer() {
    }

    public static void handleHello(ServerPlayer player, QuickLitematicaEntityPlacementPayloads.HelloPayload payload) {
        if (player == null) return;
        boolean compatible = payload.version() == QuickLitematicaEntityPlacementPayloads.PROTOCOL_VERSION;
        String token = UUID.randomUUID().toString();
        Session session = new Session(token, compatible && FGASettings.quickCraftEasyPlaceEntities);
        SESSIONS.put(player.getUUID(), session);
        double reach = placementReach(player);
        ServerPlayNetworking.send(player, new QuickLitematicaEntityPlacementPayloads.CapabilityPayload(
                QuickLitematicaEntityPlacementPayloads.PROTOCOL_VERSION,
                session.enabled,
                reach,
                Math.min(MAX_ENTITY_NBT_BYTES, Math.max(0, payload.maxNbtBytes())),
                0,
                token
        ));
    }

    public static void handleRequest(ServerPlayer player, QuickLitematicaEntityPlacementPayloads.RequestPayload payload) {
        if (player == null || payload == null) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.token.equals(payload.sessionToken())) {
            sendResult(player, payload.nonce(), "DISABLED", "");
            return;
        }
        long tick = player.serverLevel().getGameTime();
        if (!session.enabled || !FGASettings.quickCraftEasyPlaceEntities) {
            sendResult(player, payload.nonce(), "DISABLED", "");
            return;
        }
        if (session.nonces.contains(payload.nonce())) {
            sendResult(player, payload.nonce(), "REPLAYED_REQUEST", "");
            return;
        }
        if (session.nonces.size() >= MAX_NONCES) session.nonces.removeFirst();
        session.nonces.add(payload.nonce());
        if (session.lastRequestTick != Long.MIN_VALUE
                && tick - session.lastRequestTick < REQUEST_COOLDOWN_TICKS) {
            sendResult(player, payload.nonce(), "RATE_LIMITED", "");
            return;
        }
        session.lastRequestTick = tick;

        ServerLevel level = player.serverLevel();
        if (!dimensionId(level).equals(payload.dimension().toString())) {
            sendResult(player, payload.nonce(), "OUT_OF_REACH", "");
            return;
        }
        double reach = placementReach(player);
        if (!isFinite(payload.target()) || player.getEyePosition().distanceToSqr(payload.target()) > reach * reach) {
            sendResult(player, payload.nonce(), "OUT_OF_REACH", "");
            return;
        }
        if (!isFinite(payload.velocity()) || payload.velocity().lengthSqr() > MAX_SPEED * MAX_SPEED) {
            sendResult(player, payload.nonce(), "INVALID_NBT", "");
            return;
        }
        BlockPos targetPos = BlockPos.containing(payload.target());
        if (!level.hasChunkAt(targetPos)) {
            sendResult(player, payload.nonce(), "WORLD_RULE_BLOCKED", "");
            return;
        }
        if (!level.mayInteract(player, targetPos)) {
            sendResult(player, payload.nonce(), "PERMISSION_DENIED", "");
            return;
        }

        CompoundTag requested = payload.entityNbt();
        int[] entityCount = {0};
        if (requested == null || requested.sizeInBytes() > MAX_ENTITY_NBT_BYTES
                || payload.entityType() == null
                || !payload.entityType().toString().equals(readEntityId(requested))
                || !validateEntityTree(requested, 0, entityCount)) {
            sendResult(player, payload.nonce(), "INVALID_NBT", "");
            return;
        }

        List<ItemStack> required = materialsForTree(requested, level);
        if (required == null) {
            sendResult(player, payload.nonce(), "UNSUPPORTED_ENTITY", "");
            return;
        }
        boolean creativeMaterialBypass = payload.creativeMaterialBypass() && player.isCreative();
        if (!creativeMaterialBypass && !hasMaterials(player, required)) {
            sendResult(player, payload.nonce(), "NO_MATERIAL", "");
            return;
        }

        Entity root;
        try {
            root = createEntityTree(level, requested, payload.target(), payload.yaw(), payload.pitch(),
                    payload.velocity(), true, 0);
        } catch (RuntimeException ignored) {
            root = null;
        }
        if (root == null) {
            sendResult(player, payload.nonce(), "INVALID_NBT", "");
            return;
        }
        if (!isTreeWithinReach(player, root, reach)) {
            discardTree(root);
            sendResult(player, payload.nonce(), "OUT_OF_REACH", "");
            return;
        }
        if (!isTreePlacementAllowed(level, player, root)) {
            discardTree(root);
            sendResult(player, payload.nonce(), "PERMISSION_DENIED", "");
            return;
        }
        if (!isTreeInsideWorldBorder(level, root) || !canTreeStayAttached(root)) {
            discardTree(root);
            sendResult(player, payload.nonce(), "COLLISION", "");
            return;
        }

        List<ItemStack> snapshot = creativeMaterialBypass
                ? List.of()
                : inventoryItems(player).stream().map(ItemStack::copy).toList();
        if (!creativeMaterialBypass) consumeMaterials(player, required);
        boolean added;
        try {
            added = level.tryAddFreshEntityWithPassengers(root);
        } catch (RuntimeException ignored) {
            added = false;
        }
        if (!added) {
            discardTree(root);
            if (!creativeMaterialBypass) restoreInventory(player, snapshot);
            sendResult(player, payload.nonce(), "INTERNAL_ERROR", "");
            return;
        }
        sendResult(player, payload.nonce(), "SUCCESS", root.getUUID().toString());
    }

    public static void clear(ServerPlayer player) {
        if (player != null) SESSIONS.remove(player.getUUID());
    }

    private static void sendResult(ServerPlayer player, long nonce, String status, String uuid) {
        ServerPlayNetworking.send(player, new QuickLitematicaEntityPlacementPayloads.ResultPayload(
                nonce, status, uuid, ""));
    }

    private static double placementReach(ServerPlayer player) {
        double reach = player == null ? DEFAULT_REACH : player.entityInteractionRange();
        return Math.max(0.0D, Math.min(MAX_REACH, reach));
    }

    private static String readEntityId(CompoundTag nbt) {
        String raw = stringValue(nbt, "id");
        //#if MC >= 1.21.11
        //$$ Identifier parsed = Identifier.tryParse(raw);
        //#else
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        //#endif
        return parsed == null ? null : parsed.toString();
    }

    private static boolean validateEntityTree(CompoundTag nbt, int depth, int[] entityCount) {
        if (depth > MAX_ENTITY_TREE_DEPTH || ++entityCount[0] > MAX_ENTITY_TREE_SIZE) return false;
        String id = readEntityId(nbt);
        if (id == null || entityTypeForId(id) == null) return false;
        if (!isFinite(readVector(nbt, "Motion")) || readVector(nbt, "Motion").lengthSqr() > MAX_SPEED * MAX_SPEED) {
            return false;
        }
        if (!isFiniteRotation(nbt)) return false;
        if (nbt.contains("Passengers") && !isTagOfType(nbt, "Passengers", Tag.TAG_LIST)) return false;
        ListTag passengers = listValue(nbt, "Passengers");
        if (!isListOf(passengers, Tag.TAG_COMPOUND)) return false;
        for (int i = 0; i < passengers.size(); i++) {
            if (!validateEntityTree(compoundAt(passengers, i), depth + 1, entityCount)) return false;
        }
        return true;
    }

    private static Entity createEntityTree(
            ServerLevel level,
            CompoundTag nbt,
            Vec3 rootPosition,
            float rootYaw,
            float rootPitch,
            Vec3 rootVelocity,
            boolean root,
            int depth
    ) {
        if (depth > MAX_ENTITY_TREE_DEPTH) return null;
        String id = readEntityId(nbt);
        EntityType<?> type = id == null ? null : entityTypeForId(id);
        if (type == null) return null;
        Entity entity = createEntity(type, level);
        if (entity == null) return null;
        boolean blockAttached = entity instanceof BlockAttachedEntity;
        CompoundTag clean = sanitizeSingleEntity(nbt, id);
        if (blockAttached) {
            // BlockAttachedEntity validates TileX/Y/Z against Pos during load before keeping its attachment point.
            ListTag position = new ListTag();
            position.add(DoubleTag.valueOf(rootPosition.x));
            position.add(DoubleTag.valueOf(rootPosition.y));
            position.add(DoubleTag.valueOf(rootPosition.z));
            clean.put("Pos", position);
        }
        loadEntity(entity, clean, level);
        float yaw = root ? rootYaw : readRotation(nbt, 0);
        float pitch = root ? rootPitch : readRotation(nbt, 1);
        Vec3 velocity = root ? rootVelocity : readVector(nbt, "Motion");
        if (!blockAttached) {
            entity.moveTo(rootPosition.x, rootPosition.y, rootPosition.z, yaw, pitch);
        }
        entity.setDeltaMovement(velocity);

        ListTag passengers = listValue(nbt, "Passengers");
        for (int i = 0; i < passengers.size(); i++) {
            Entity passenger = createEntityTree(level, compoundAt(passengers, i), rootPosition,
                    rootYaw, rootPitch, rootVelocity, false, depth + 1);
            if (passenger == null || !startRiding(passenger, entity)) return null;
        }
        return entity;
    }

    private static CompoundTag sanitizeSingleEntity(CompoundTag source, String id) {
        CompoundTag clean = source.copy();
        for (String key : List.of(
                "UUID", "UUIDMost", "UUIDLeast", "Pos", "Motion", "Rotation", "Passengers", "Dimension",
                "Leash", "leash", "Owner", "OwnerUUID", "Thrower", "Attributes", "attributes",
                "ActiveEffects", "active_effects", "Invulnerable", "NoAI", "Command", "LastOutput",
                "SuccessCount", "DeathLootTable", "DeathLootTableSeed", "Offers", "Gossips"
        )) {
            clean.remove(key);
        }
        String path = pathOf(id);
        if (path.equals("item")) {
            clean.remove("Age");
            clean.remove("PickupDelay");
        }
        if (path.equals("furnace_minecart")) {
            clean.remove("Fuel");
            clean.remove("PushX");
            clean.remove("PushZ");
        }
        if (path.equals("tnt_minecart") || path.equals("creeper")) {
            clean.remove("Fuse");
            clean.remove("ExplosionRadius");
            clean.remove("ignited");
            clean.remove("powered");
        }
        return clean;
    }

    private static boolean isTreeInsideWorldBorder(ServerLevel level, Entity root) {
        if (!level.getWorldBorder().isWithinBounds(root.getBoundingBox())) return false;
        for (Entity passenger : root.getPassengers()) {
            if (!isTreeInsideWorldBorder(level, passenger)) return false;
        }
        return true;
    }

    private static boolean isTreeWithinReach(ServerPlayer player, Entity root, double reach) {
        if (player.getEyePosition().distanceToSqr(root.position()) > reach * reach) return false;
        for (Entity passenger : root.getPassengers()) {
            if (!isTreeWithinReach(player, passenger, reach)) return false;
        }
        return true;
    }

    private static boolean isTreePlacementAllowed(ServerLevel level, ServerPlayer player, Entity root) {
        BlockPos position = root instanceof BlockAttachedEntity attached ? attached.getPos() : root.blockPosition();
        if (!level.hasChunkAt(position) || !level.mayInteract(player, position)) return false;
        for (Entity passenger : root.getPassengers()) {
            if (!isTreePlacementAllowed(level, player, passenger)) return false;
        }
        return true;
    }

    private static boolean canTreeStayAttached(Entity root) {
        if (root instanceof BlockAttachedEntity attached && !attached.survives()) return false;
        for (Entity passenger : root.getPassengers()) {
            if (!canTreeStayAttached(passenger)) return false;
        }
        return true;
    }

    private static void discardTree(Entity root) {
        root.getPassengersAndSelf().toList().forEach(Entity::discard);
    }

    private static List<ItemStack> materialsForTree(CompoundTag root, ServerLevel level) {
        List<ItemStack> materials = new ArrayList<>();
        int[] count = {0};
        return appendEntityTreeMaterials(root, level, materials, 0, count)
                ? mergeMaterials(materials)
                : null;
    }

    private static boolean appendEntityTreeMaterials(
            CompoundTag nbt,
            ServerLevel level,
            List<ItemStack> materials,
            int depth,
            int[] count
    ) {
        if (depth > MAX_ENTITY_TREE_DEPTH || ++count[0] > MAX_ENTITY_TREE_SIZE) return false;
        String id = readEntityId(nbt);
        if (id == null || entityTypeForId(id) == null) return false;
        ItemStack baseStack = stackForEntity(id, nbt, level);
        if (baseStack == null || baseStack.isEmpty()) return false;
        materials.add(baseStack);

        if (!pathOf(id).equals("item") && !appendStoredItem(materials, nbt, "Item", level)) return false;
        for (String key : List.of("SaddleItem", "ArmorItem", "DecorItem", "body_armor_item")) {
            if (!appendStoredItem(materials, nbt, key, level)) return false;
        }
        if (isChestedHorse(id, nbt)) materials.add(new ItemStack(Items.CHEST));
        for (String key : List.of("Inventory", "ArmorItems", "HandItems")) {
            if (!appendStoredItems(materials, nbt, key, level, 0)) return false;
        }
        int containerCapacity = containerCapacity(id, nbt);
        if (containerCapacity == Integer.MIN_VALUE
                || !appendStoredItems(materials, nbt, "Items", level, containerCapacity)) return false;

        ListTag passengers = listValue(nbt, "Passengers");
        for (int i = 0; i < passengers.size(); i++) {
            if (!appendEntityTreeMaterials(compoundAt(passengers, i), level, materials, depth + 1, count)) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack stackForEntity(String id, CompoundTag nbt, ServerLevel level) {
        if (id == null) return null;
        if (pathOf(id).equals("item")) {
            if (!isTagOfType(nbt, "Item", Tag.TAG_COMPOUND)) return null;
            ItemStack stack = parseItem(level, compoundValue(nbt, "Item"));
            return stack.isEmpty() ? null : stack;
        }
        String path = pathOf(id);
        Item direct = switch (path) {
            case "armor_stand" -> Items.ARMOR_STAND;
            case "painting" -> Items.PAINTING;
            case "item_frame" -> Items.ITEM_FRAME;
            case "glow_item_frame" -> Items.GLOW_ITEM_FRAME;
            case "end_crystal" -> Items.END_CRYSTAL;
            case "minecart" -> Items.MINECART;
            case "chest_minecart" -> Items.CHEST_MINECART;
            case "furnace_minecart" -> Items.FURNACE_MINECART;
            case "tnt_minecart" -> Items.TNT_MINECART;
            case "hopper_minecart" -> Items.HOPPER_MINECART;
            case "boat" -> boatItem(stringValue(nbt, "Type"), false);
            case "chest_boat" -> boatItem(stringValue(nbt, "Type"), true);
            default -> null;
        };
        if (direct == null && isSplitBoatPath(path)) {
            direct = itemForId(namespaceOf(id), path);
        }
        if (direct != null) return new ItemStack(direct);
        Item egg = itemForId(namespaceOf(id), path + "_spawn_egg");
        return egg == null ? null : new ItemStack(egg);
    }

    private static boolean appendStoredItem(
            List<ItemStack> materials,
            CompoundTag nbt,
            String key,
            ServerLevel level
    ) {
        if (!nbt.contains(key)) return true;
        if (!isTagOfType(nbt, key, Tag.TAG_COMPOUND)) return false;
        CompoundTag itemNbt = compoundValue(nbt, key);
        if (itemNbt.isEmpty()) return true;
        ItemStack stack = parseItem(level, itemNbt);
        if (stack.isEmpty()) return false;
        materials.add(stack);
        return true;
    }

    private static boolean appendStoredItems(
            List<ItemStack> materials,
            CompoundTag nbt,
            String key,
            ServerLevel level,
            int capacity
    ) {
        if (!nbt.contains(key)) return true;
        if (!isTagOfType(nbt, key, Tag.TAG_LIST)) return false;
        ListTag items = listValue(nbt, key);
        if (!isListOf(items, Tag.TAG_COMPOUND)) return false;
        if (capacity < 0 && !items.isEmpty()) return false;
        Set<Integer> slots = capacity > 0 ? new HashSet<>() : null;
        for (int i = 0; i < items.size(); i++) {
            CompoundTag itemNbt = compoundAt(items, i);
            if (itemNbt.isEmpty()) continue;
            if (capacity > 0) {
                int slot = byteValue(itemNbt, "Slot") & 255;
                if (!isTagOfType(itemNbt, "Slot", Tag.TAG_BYTE) || slot >= capacity || !slots.add(slot)) return false;
            }
            ItemStack stack = parseItem(level, itemNbt);
            if (stack.isEmpty()) return false;
            materials.add(stack);
        }
        return true;
    }

    private static Item boatItem(String type, boolean chest) {
        String prefix = switch (type) {
            case "spruce", "birch", "jungle", "acacia", "cherry", "dark_oak", "mangrove", "bamboo", "oak", "" -> type.isEmpty() ? "oak" : type;
            default -> null;
        };
        if (prefix == null) return null;
        String path = prefix.equals("bamboo")
                ? (chest ? "bamboo_chest_raft" : "bamboo_raft")
                : (prefix + (chest ? "_chest_boat" : "_boat"));
        return itemForId("minecraft", path);
    }

    private static boolean isChestedHorse(String id, CompoundTag nbt) {
        return switch (pathOf(id)) {
            case "donkey", "mule", "llama", "trader_llama" -> booleanValue(nbt, "ChestedHorse");
            default -> false;
        };
    }

    private static int containerCapacity(String id, CompoundTag nbt) {
        String path = pathOf(id);
        if (path.endsWith("_chest_boat") || path.endsWith("_chest_raft")) return 27;
        return switch (path) {
            case "hopper_minecart" -> 5;
            case "chest_minecart", "chest_boat" -> 27;
            case "donkey", "mule" -> booleanValue(nbt, "ChestedHorse") ? 15 : -1;
            case "llama", "trader_llama" -> llamaContainerCapacity(nbt);
            default -> -1;
        };
    }

    private static int llamaContainerCapacity(CompoundTag nbt) {
        if (!booleanValue(nbt, "ChestedHorse")) return -1;
        int strength = intValue(nbt, "Strength");
        return strength >= 1 && strength <= 5 ? strength * 3 : Integer.MIN_VALUE;
    }

    private static boolean hasMaterials(ServerPlayer player, List<ItemStack> required) {
        for (ItemStack wanted : required) {
            int count = 0;
            for (ItemStack actual : inventoryItems(player)) {
                if (FGACompat.isSameItemSameTags(actual, wanted)) count += actual.getCount();
            }
            if (count < wanted.getCount()) return false;
        }
        return true;
    }

    private static List<ItemStack> mergeMaterials(List<ItemStack> materials) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack material : materials) {
            if (material.isEmpty()) continue;
            ItemStack existing = merged.stream()
                    .filter(stack -> FGACompat.isSameItemSameTags(stack, material))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                merged.add(material.copy());
            } else {
                existing.grow(material.getCount());
            }
        }
        return merged;
    }

    private static void consumeMaterials(ServerPlayer player, List<ItemStack> required) {
        for (ItemStack wanted : required) {
            int left = wanted.getCount();
            for (ItemStack actual : inventoryItems(player)) {
                if (left <= 0) break;
                if (!FGACompat.isSameItemSameTags(actual, wanted)) continue;
                int amount = Math.min(left, actual.getCount());
                actual.shrink(amount);
                left -= amount;
            }
        }
        player.getInventory().setChanged();
    }

    private static void restoreInventory(ServerPlayer player, List<ItemStack> snapshot) {
        for (int i = 0; i < snapshot.size() && i < inventoryItems(player).size(); i++) {
            player.getInventory().setItem(i, snapshot.get(i).copy());
        }
        player.getInventory().setChanged();
    }

    private static Vec3 readVector(CompoundTag nbt, String key) {
        if (!nbt.contains(key)) return Vec3.ZERO;
        if (!isTagOfType(nbt, key, Tag.TAG_LIST)) return new Vec3(Double.NaN, Double.NaN, Double.NaN);
        ListTag list = listValue(nbt, key);
        if (list.size() != 3 || !isListOf(list, Tag.TAG_DOUBLE)) {
            return new Vec3(Double.NaN, Double.NaN, Double.NaN);
        }
        return new Vec3(doubleAt(list, 0), doubleAt(list, 1), doubleAt(list, 2));
    }

    private static float readRotation(CompoundTag nbt, int index) {
        if (!nbt.contains("Rotation")) return 0.0F;
        ListTag rotation = listValue(nbt, "Rotation");
        return rotation.size() > index ? floatAt(rotation, index) : 0.0F;
    }

    private static boolean isFiniteRotation(CompoundTag nbt) {
        if (!nbt.contains("Rotation")) return true;
        if (!isTagOfType(nbt, "Rotation", Tag.TAG_LIST)) return false;
        ListTag rotation = listValue(nbt, "Rotation");
        return rotation.size() == 2 && isListOf(rotation, Tag.TAG_FLOAT)
                && Float.isFinite(floatAt(rotation, 0)) && Float.isFinite(floatAt(rotation, 1));
    }

    private static boolean isFinite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static String dimensionId(ServerLevel level) {
        //#if MC >= 1.21.11
        //$$ return level.dimension().identifier().toString();
        //#else
        return level.dimension().location().toString();
        //#endif
    }

    private static EntityType<?> entityTypeForId(String value) {
        //#if MC >= 1.21.11
        //$$ Identifier id = Identifier.tryParse(value);
        //#else
        ResourceLocation id = ResourceLocation.tryParse(value);
        //#endif
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return null;
        //#if MC >= 1.21.3
        //$$ return BuiltInRegistries.ENTITY_TYPE.getValue(id);
        //#else
        return BuiltInRegistries.ENTITY_TYPE.get(id);
        //#endif
    }

    private static Item itemForId(String namespace, String path) {
        //#if MC >= 1.21.11
        //$$ Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
        //#else
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        //#endif
        if (!BuiltInRegistries.ITEM.containsKey(id)) return null;
        //#if MC >= 1.21.3
        //$$ return BuiltInRegistries.ITEM.getValue(id);
        //#else
        return BuiltInRegistries.ITEM.get(id);
        //#endif
    }

    private static Entity createEntity(EntityType<?> type, ServerLevel level) {
        //#if MC >= 1.21.3
        //$$ return type.create(level, EntitySpawnReason.LOAD);
        //#else
        return type.create(level);
        //#endif
    }

    private static void loadEntity(Entity entity, CompoundTag nbt, ServerLevel level) {
        //#if MC >= 1.21.8
        //$$ entity.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), nbt));
        //#else
        entity.load(nbt);
        //#endif
    }

    private static boolean startRiding(Entity passenger, Entity vehicle) {
        //#if MC >= 1.21.10
        //$$ return passenger.startRiding(vehicle, true, true);
        //#else
        return passenger.startRiding(vehicle, true);
        //#endif
    }

    private static NonNullList<ItemStack> inventoryItems(ServerPlayer player) {
        //#if MC >= 1.21.5
        //$$ return player.getInventory().getNonEquipmentItems();
        //#else
        return player.getInventory().items;
        //#endif
    }

    private static ItemStack parseItem(ServerLevel level, CompoundTag nbt) {
        //#if MC >= 1.21.8
        //$$ return ItemStack.OPTIONAL_CODEC
        //$$         .parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), nbt)
        //$$         .result().orElse(ItemStack.EMPTY);
        //#elseif MC >= 1.21.5
        //$$ return ItemStack.parse(level.registryAccess(), nbt).orElse(ItemStack.EMPTY);
        //#else
        return ItemStack.parseOptional(level.registryAccess(), nbt);
        //#endif
    }

    private static boolean isTagOfType(CompoundTag nbt, String key, int type) {
        Tag value = nbt.get(key);
        return value != null && value.getId() == type;
    }

    private static ListTag listValue(CompoundTag nbt, String key) {
        Tag value = nbt.get(key);
        return value instanceof ListTag list ? list : new ListTag();
    }

    private static CompoundTag compoundValue(CompoundTag nbt, String key) {
        Tag value = nbt.get(key);
        return value instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    private static CompoundTag compoundAt(ListTag list, int index) {
        //#if MC >= 1.21.5
        //$$ return list.getCompoundOrEmpty(index);
        //#else
        return list.getCompound(index);
        //#endif
    }

    private static String stringValue(CompoundTag nbt, String key) {
        //#if MC >= 1.21.5
        //$$ return nbt.getStringOr(key, "");
        //#else
        return nbt.getString(key);
        //#endif
    }

    private static boolean booleanValue(CompoundTag nbt, String key) {
        //#if MC >= 1.21.5
        //$$ return nbt.getBooleanOr(key, false);
        //#else
        return nbt.getBoolean(key);
        //#endif
    }

    private static int intValue(CompoundTag nbt, String key) {
        //#if MC >= 1.21.5
        //$$ return nbt.getIntOr(key, 0);
        //#else
        return nbt.getInt(key);
        //#endif
    }

    private static byte byteValue(CompoundTag nbt, String key) {
        //#if MC >= 1.21.5
        //$$ return nbt.getByteOr(key, (byte) 0);
        //#else
        return nbt.getByte(key);
        //#endif
    }

    private static double doubleAt(ListTag list, int index) {
        //#if MC >= 1.21.5
        //$$ return list.getDouble(index).orElse(Double.NaN);
        //#else
        return list.getDouble(index);
        //#endif
    }

    private static float floatAt(ListTag list, int index) {
        //#if MC >= 1.21.5
        //$$ return list.getFloat(index).orElse(Float.NaN);
        //#else
        return list.getFloat(index);
        //#endif
    }

    private static boolean isListOf(ListTag list, int type) {
        for (Tag element : list) {
            if (element.getId() != type) return false;
        }
        return true;
    }

    private static boolean isSplitBoatPath(String path) {
        return path.endsWith("_boat") || path.endsWith("_chest_boat")
                || path.endsWith("_raft") || path.endsWith("_chest_raft");
    }

    private static String namespaceOf(String id) {
        int separator = id.indexOf(':');
        return separator < 0 ? "minecraft" : id.substring(0, separator);
    }

    private static String pathOf(String id) {
        int separator = id.indexOf(':');
        return separator < 0 ? id : id.substring(separator + 1);
    }

    private static final class Session {
        private final String token;
        private final boolean enabled;
        private final Deque<Long> nonces = new ArrayDeque<>();
        private long lastRequestTick = Long.MIN_VALUE;

        private Session(String token, boolean enabled) {
            this.token = token;
            this.enabled = enabled;
        }
    }
}
//#endif
