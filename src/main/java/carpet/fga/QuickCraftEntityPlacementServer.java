//#if MC >= 1.21 && MC <= 1.21.1
package carpet.fga;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;

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
    private static final double REACH = 4.5D;
    private static final double MAX_SPEED = 4.0D;
    private static final int MAX_ENTITY_NBT_BYTES = 262_144;
    private static final int REQUEST_COOLDOWN_TICKS = 2;
    private static final int MAX_NONCES = 64;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private QuickCraftEntityPlacementServer() {
    }

    public static void handleHello(ServerPlayer player, FGAPayloads.EntityPlaceHelloPayload payload) {
        if (player == null) return;
        boolean compatible = payload.version() == FGAPayloads.ENTITY_PLACE_PROTOCOL_VERSION;
        String token = UUID.randomUUID().toString();
        Session session = new Session(token, compatible && FGASettings.quickCraftEasyPlaceEntities);
        SESSIONS.put(player.getUUID(), session);
        player.connection.send(new ClientboundCustomPayloadPacket(new FGAPayloads.EntityPlaceCapabilityPayload(
                FGAPayloads.ENTITY_PLACE_PROTOCOL_VERSION,
                session.enabled,
                REACH,
                Math.min(MAX_ENTITY_NBT_BYTES, Math.max(0, payload.maxNbtBytes())),
                0,
                token
        )));
    }

    public static void handleRequest(ServerPlayer player, FGAPayloads.EntityPlaceRequestPayload payload) {
        if (player == null || payload == null) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.token.equals(payload.sessionToken())) {
            sendResult(player, payload.nonce(), "DISABLED", "");
            return;
        }
        long tick = player.serverLevel().getGameTime();
        if (!session.enabled) {
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
        if (!level.dimension().location().equals(payload.dimension())) {
            sendResult(player, payload.nonce(), "OUT_OF_REACH", "");
            return;
        }
        if (!isFinite(payload.target()) || player.distanceToSqr(payload.target()) > REACH * REACH) {
            sendResult(player, payload.nonce(), "OUT_OF_REACH", "");
            return;
        }
        if (!isFinite(payload.velocity()) || payload.velocity().lengthSqr() > MAX_SPEED * MAX_SPEED) {
            sendResult(player, payload.nonce(), "INVALID_NBT", "");
            return;
        }

        CompoundTag requested = payload.entityNbt();
        if (requested == null || estimatedNbtBytes(requested) > MAX_ENTITY_NBT_BYTES
                || payload.entityType() == null
                || !payload.entityType().equals(readEntityId(requested))) {
            sendResult(player, payload.nonce(), "INVALID_NBT", "");
            return;
        }
        if (hasPassengers(requested)) {
            sendResult(player, payload.nonce(), "UNSUPPORTED_ENTITY", "");
            return;
        }

        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(payload.entityType())) {
            sendResult(player, payload.nonce(), "UNSUPPORTED_ENTITY", "");
            return;
        }
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(payload.entityType());
        List<ItemStack> required = materialsFor(entityType, requested, level);
        if (required == null) {
            sendResult(player, payload.nonce(), "UNSUPPORTED_ENTITY", "");
            return;
        }
        if (!hasMaterials(player, required)) {
            sendResult(player, payload.nonce(), "NO_MATERIAL", "");
            return;
        }

        CompoundTag clean = sanitize(requested);
        Entity entity = entityType.create(level);
        if (entity == null) {
            sendResult(player, payload.nonce(), "UNSUPPORTED_ENTITY", "");
            return;
        }
        try {
            entity.load(clean);
            entity.moveTo(payload.target().x, payload.target().y, payload.target().z,
                    payload.yaw(), payload.pitch());
            entity.setDeltaMovement(payload.velocity());
            if (!level.noCollision(entity, entity.getBoundingBox())) {
                sendResult(player, payload.nonce(), "COLLISION", "");
                return;
            }
        } catch (RuntimeException exception) {
            sendResult(player, payload.nonce(), "INVALID_NBT", "");
            return;
        }

        List<ItemStack> snapshot = player.getInventory().items.stream().map(ItemStack::copy).toList();
        consumeMaterials(player, required);
        if (!level.addFreshEntity(entity)) {
            restoreInventory(player, snapshot);
            sendResult(player, payload.nonce(), "INTERNAL_ERROR", "");
            return;
        }
        sendResult(player, payload.nonce(), "SUCCESS", entity.getUUID().toString());
    }

    public static void clear(ServerPlayer player) {
        if (player != null) SESSIONS.remove(player.getUUID());
    }

    private static void sendResult(ServerPlayer player, long nonce, String status, String uuid) {
        player.connection.send(new ClientboundCustomPayloadPacket(new FGAPayloads.EntityPlaceResultPayload(
                nonce, status, uuid, "")));
    }

    private static ResourceLocation readEntityId(CompoundTag nbt) {
        String id = nbt.getString("id");
        return ResourceLocation.tryParse(id);
    }

    private static CompoundTag sanitize(CompoundTag source) {
        CompoundTag clean = source.copy();
        clean.remove("UUID");
        clean.remove("Pos");
        clean.remove("Motion");
        clean.remove("Rotation");
        clean.remove("Passengers");
        clean.remove("Dimension");
        return clean;
    }

    private static boolean hasPassengers(CompoundTag nbt) {
        return nbt.contains("Passengers", Tag.TAG_LIST)
                && !nbt.getList("Passengers", Tag.TAG_COMPOUND).isEmpty();
    }

    private static List<ItemStack> materialsFor(EntityType<?> type, CompoundTag nbt, ServerLevel level) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        ItemStack baseStack = stackForEntity(id, nbt, level);
        if (baseStack == null || baseStack.isEmpty()) return null;
        List<ItemStack> materials = new ArrayList<>();
        materials.add(baseStack);
        int capacity = containerCapacity(id);
        if (capacity > 0) {
            if (!nbt.contains("Items", Tag.TAG_LIST)) return materials;
            ListTag items = nbt.getList("Items", Tag.TAG_COMPOUND);
            Set<Integer> slots = new HashSet<>();
            for (int i = 0; i < items.size(); i++) {
                CompoundTag itemNbt = items.getCompound(i);
                int slot = itemNbt.getByte("Slot") & 255;
                if (!itemNbt.contains("Slot", Tag.TAG_BYTE) || slot >= capacity || !slots.add(slot)) return null;
                ItemStack stack = ItemStack.parseOptional(level.registryAccess(), itemNbt);
                if (stack.isEmpty()) return null;
                materials.add(stack);
            }
        }
        return mergeMaterials(materials);
    }

    private static ItemStack stackForEntity(ResourceLocation id, CompoundTag nbt, ServerLevel level) {
        if (id == null) return null;
        if (id.getPath().equals("item")) {
            if (!nbt.contains("Item", Tag.TAG_COMPOUND)) return null;
            ItemStack stack = ItemStack.parseOptional(level.registryAccess(), nbt.getCompound("Item"));
            return stack.isEmpty() ? null : stack;
        }
        String path = id.getPath();
        Item direct = switch (path) {
            case "armor_stand" -> Items.ARMOR_STAND;
            case "minecart" -> Items.MINECART;
            case "chest_minecart" -> Items.CHEST_MINECART;
            case "furnace_minecart" -> Items.FURNACE_MINECART;
            case "tnt_minecart" -> Items.TNT_MINECART;
            case "hopper_minecart" -> Items.HOPPER_MINECART;
            case "boat" -> boatItem(nbt.getString("Type"), false);
            case "chest_boat" -> boatItem(nbt.getString("Type"), true);
            default -> null;
        };
        if (direct != null) return new ItemStack(direct);
        ResourceLocation eggId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path + "_spawn_egg");
        Item egg = BuiltInRegistries.ITEM.containsKey(eggId) ? BuiltInRegistries.ITEM.get(eggId) : null;
        return egg == null ? null : new ItemStack(egg);
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
        ResourceLocation id = ResourceLocation.withDefaultNamespace(path);
        return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
    }

    private static int containerCapacity(ResourceLocation id) {
        return switch (id.getPath()) {
            case "hopper_minecart" -> 5;
            case "chest_minecart" -> 36;
            case "chest_boat" -> 27;
            default -> 0;
        };
    }

    private static boolean hasMaterials(ServerPlayer player, List<ItemStack> required) {
        for (ItemStack wanted : required) {
            int count = 0;
            for (ItemStack actual : player.getInventory().items) {
                if (FGACompat.isSameItemSameTags(actual, wanted)) count += actual.getCount();
            }
            if (count < wanted.getCount()) return false;
        }
        return true;
    }

    private static List<ItemStack> mergeMaterials(List<ItemStack> materials) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack material : materials) {
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
            for (ItemStack actual : player.getInventory().items) {
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
        for (int i = 0; i < snapshot.size() && i < player.getInventory().items.size(); i++) {
            player.getInventory().setItem(i, snapshot.get(i).copy());
        }
        player.getInventory().setChanged();
    }

    private static boolean isFinite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static int estimatedNbtBytes(CompoundTag nbt) {
        return nbt.toString().length() * 2;
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
