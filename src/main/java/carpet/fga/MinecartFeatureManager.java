//#if MC == 1.21.1
package carpet.fga;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MinecartFeatureManager {
    private static final String DATA_NAME = "carpet_fga_minecart_links";
    private static final double VANILLA_LAND_SPEED = 0.4D;
    private static final double VANILLA_WATER_SPEED = 0.2D;
    private static final double BREAK_DISTANCE = 16.0D;
    private static final long SELECTION_TIMEOUT = 1200L;
    private static final SavedData.Factory<LinkData> DATA_FACTORY = new SavedData.Factory<>(
            LinkData::new, LinkData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private static final Map<UUID, BoostState> BOOSTS = new HashMap<>();
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final Map<LinkKey, Long> LAST_SOLVED_TICK = new HashMap<>();
    private static final Map<LinkKey, Long> LAST_TENSION_PARTICLE = new HashMap<>();
    private static LinkData links;
    private static MinecraftServer currentServer;
    private static int lastBoostDuration;

    private MinecartFeatureManager() {
    }

    public static void load(MinecraftServer server) {
        currentServer = server;
        links = null;
        BOOSTS.clear();
        SELECTIONS.clear();
        LAST_SOLVED_TICK.clear();
        LAST_TENSION_PARTICLE.clear();
        lastBoostDuration = 0;
    }

    public static void tick(MinecraftServer server) {
        ensureLinks(server);
        if (server.overworld() == null) return;
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Selection>> iterator = SELECTIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Selection> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Selection selection = entry.getValue();
            if (player == null || now > selection.expiresAt()
                    || !player.level().dimension().equals(selection.dimension())) {
                iterator.remove();
            }
        }
        BOOSTS.entrySet().removeIf(entry -> findMinecart(server, entry.getKey()) == null);
        LAST_SOLVED_TICK.entrySet().removeIf(entry -> now - entry.getValue() > 2L);
    }

    public static void clear() {
        BOOSTS.clear();
        SELECTIONS.clear();
        LAST_SOLVED_TICK.clear();
        LAST_TENSION_PARTICLE.clear();
        links = null;
        currentServer = null;
        lastBoostDuration = 0;
    }

    public static void clearBoosts() {
        BOOSTS.clear();
    }

    public static void cancelBoost(Minecart minecart) {
        BOOSTS.remove(minecart.getUUID());
    }

    public static List<Minecart> loadedTrain(Minecart origin) {
        MinecraftServer server = currentServer;
        if (server == null && origin.level() instanceof ServerLevel level) server = level.getServer();
        if (server == null) return List.of(origin);
        ensureLinks(server);

        Set<UUID> visited = new HashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        queue.add(origin.getUUID());
        while (!queue.isEmpty()) {
            UUID current = queue.removeFirst();
            if (!visited.add(current) || links == null) continue;
            for (LinkKey key : links.links.keySet()) {
                UUID other = key.other(current);
                if (other != null && !visited.contains(other)) queue.addLast(other);
            }
        }

        List<Minecart> result = new ArrayList<>();
        for (UUID uuid : visited) {
            Minecart minecart = findMinecart(server, uuid);
            if (minecart != null) result.add(minecart);
        }
        if (result.stream().noneMatch(minecart -> minecart.getUUID().equals(origin.getUUID()))) result.add(origin);
        return result;
    }

    public static void removePlayer(ServerPlayer player) {
        SELECTIONS.remove(player.getUUID());
    }

    public static int linkCount() {
        if (currentServer != null) ensureLinks(currentServer);
        return links == null ? 0 : links.links.size();
    }

    public static int refundableLinkCount() {
        if (currentServer != null) ensureLinks(currentServer);
        if (links == null) return 0;
        int count = 0;
        for (LinkRecord record : links.links.values()) if (record.refund()) count++;
        return count;
    }

    public static int activeBoostCount() {
        return BOOSTS.size();
    }

    public static int longestFullBoostTicks() {
        int longest = 0;
        for (BoostState state : BOOSTS.values()) longest = Math.max(longest, state.fullTicks);
        return longest;
    }

    public static int lastBoostDuration() {
        return lastBoostDuration;
    }

    public static boolean boost(Player player, InteractionHand hand) {
        if (!FGASettings.fireworkMinecartBoost || !(player.getVehicle() instanceof Minecart minecart)
                || minecart.getMinecartType() != AbstractMinecart.Type.RIDEABLE) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.FIREWORK_ROCKET)) return false;
        if (player.level().isClientSide()) return true;

        Vec3 look = player.getLookAngle();
        Vec3 direction = horizontalDirection(look);
        if (direction.lengthSqr() < 1.0E-6D) direction = horizontalDirection(minecart.getDeltaMovement());
        if (direction.lengthSqr() < 1.0E-6D) direction = new Vec3(0.0D, 0.0D, 1.0D);

        Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        int flight = fireworks == null ? 1 : Math.max(1, fireworks.flightDuration());
        int ticks = Math.multiplyExact(flight, MinecartFeatureConfig.snapshot().durationPerFlight());
        lastBoostDuration = ticks;
        BOOSTS.put(minecart.getUUID(), new BoostState(direction, ticks));

        if (!player.getAbilities().instabuild) stack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(Items.FIREWORK_ROCKET));
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, minecart.getX(), minecart.getY(), minecart.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.FIREWORK, minecart.getX(), minecart.getY() + 0.5D, minecart.getZ(),
                12, 0.25D, 0.15D, 0.25D, 0.08D);
        return true;
    }

    public static void beforeMinecartTick(Minecart minecart) {
        if (!(minecart.level() instanceof ServerLevel)) return;
        BoostState state = BOOSTS.get(minecart.getUUID());
        if (state == null) return;
        if (!FGASettings.fireworkMinecartBoost || minecart.isRemoved()) {
            BOOSTS.remove(minecart.getUUID());
            return;
        }
        MinecartFeatureConfig.State config = MinecartFeatureConfig.snapshot();
        Vec3 movement = minecart.getDeltaMovement();
        if (state.fullTicks > 0) {
            minecart.setDeltaMovement(state.direction.x * config.maxSpeed(), movement.y,
                    state.direction.z * config.maxSpeed());
            return;
        }

        double speed = horizontalSpeed(movement);
        double vanilla = vanillaSpeed(minecart);
        if (speed <= vanilla + 1.0E-6D) {
            BOOSTS.remove(minecart.getUUID());
            return;
        }
        Vec3 direction = horizontalDirection(movement);
        if (direction.lengthSqr() < 1.0E-6D) direction = state.direction;
        double next = Math.max(vanilla, speed - config.deceleration());
        state.direction = direction;
        minecart.setDeltaMovement(direction.x * next, movement.y, direction.z * next);
    }

    public static void afterMinecartTick(Minecart minecart) {
        if (!(minecart.level() instanceof ServerLevel)) return;
        BoostState state = BOOSTS.get(minecart.getUUID());
        if (state != null && FGASettings.fireworkMinecartBoost && state.fullTicks > 0) {
            Vec3 current = horizontalDirection(minecart.getDeltaMovement());
            if (current.lengthSqr() >= 1.0E-6D) state.direction = current;
            state.fullTicks--;
        }
        solveLinks(minecart);
    }

    public static boolean suppressNaturalSlowdown(Minecart minecart) {
        return FGASettings.fireworkMinecartBoost && BOOSTS.containsKey(minecart.getUUID());
    }

    public static double maxSpeed(Minecart minecart, double vanilla) {
        if (FGASettings.fireworkMinecartBoost && BOOSTS.containsKey(minecart.getUUID())) {
            return Math.max(vanilla, MinecartFeatureConfig.snapshot().maxSpeed());
        }
        if (FGASettings.chainMinecartBinding && isLinked(minecart.getUUID())) {
            return Math.max(vanilla, horizontalSpeed(minecart.getDeltaMovement()));
        }
        return vanilla;
    }

    public static InteractionResult interactChain(Minecart minecart, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!FGASettings.chainMinecartBinding || !stack.is(Items.CHAIN)
                || minecart.getMinecartType() != AbstractMinecart.Type.RIDEABLE) {
            return null;
        }
        if (player.level().isClientSide()) return InteractionResult.sidedSuccess(true);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.FAIL;

        MinecraftServer server = serverPlayer.getServer();
        ensureLinks(server);
        if (links == null) return InteractionResult.FAIL;
        long now = server.overworld().getGameTime();
        Selection selected = SELECTIONS.get(player.getUUID());
        if (selected == null || now > selected.expiresAt()
                || !selected.dimension().equals(player.level().dimension())) {
            SELECTIONS.put(player.getUUID(), new Selection(minecart.getUUID(), player.level().dimension(),
                    now + SELECTION_TIMEOUT));
            message(player, "已选择第一辆矿车，再用锁链点击第二辆矿车 / First minecart selected");
            chainEffect(minecart, false);
            return InteractionResult.sidedSuccess(false);
        }
        if (selected.minecart().equals(minecart.getUUID())) {
            SELECTIONS.remove(player.getUUID());
            message(player, "已取消矿车选择 / Minecart selection cancelled");
            return InteractionResult.sidedSuccess(false);
        }

        Minecart first = findMinecart(server, selected.minecart());
        SELECTIONS.remove(player.getUUID());
        if (first == null || first.isRemoved()) {
            message(player, "第一辆矿车未加载或已不存在 / First minecart is unavailable");
            return InteractionResult.FAIL;
        }
        if (first.level() != minecart.level()) {
            message(player, "两辆矿车必须位于同一维度 / Minecarts must be in the same dimension");
            return InteractionResult.FAIL;
        }

        LinkKey key = LinkKey.of(first.getUUID(), minecart.getUUID());
        LinkRecord existing = links.links.get(key);
        if (existing != null) {
            removeLink(key);
            refund(existing, player, minecart);
            chainEffect(minecart, true);
            message(player, "已解除矿车锁链并返还物品 / Minecart chain removed");
            return InteractionResult.sidedSuccess(false);
        }
        if (first.distanceTo(minecart) > BREAK_DISTANCE) {
            message(player, "矿车距离超过16格 / Minecarts are more than 16 blocks apart");
            return InteractionResult.FAIL;
        }
        if (degree(first.getUUID()) >= 2 || degree(minecart.getUUID()) >= 2) {
            message(player, "每辆矿车最多连接两辆矿车 / Each minecart supports at most two links");
            return InteractionResult.FAIL;
        }
        if (connected(first.getUUID(), minecart.getUUID())) {
            message(player, "连接会形成闭环，已拒绝 / Link rejected because it would create a cycle");
            return InteractionResult.FAIL;
        }

        boolean paid = !player.getAbilities().instabuild;
        if (paid) stack.consume(1, player);
        links.links.put(key, new LinkRecord(paid));
        links.setDirty();
        chainEffect(first, false);
        chainEffect(minecart, false);
        message(player, "已用锁链连接两辆矿车 / Minecarts linked");
        return InteractionResult.sidedSuccess(false);
    }

    public static void minecartRemoved(Minecart minecart, Entity.RemovalReason reason) {
        BOOSTS.remove(minecart.getUUID());
        if (minecart.level() instanceof ServerLevel level) ensureLinks(level.getServer());
        boolean breakForDimensionChange = reason == Entity.RemovalReason.CHANGED_DIMENSION
                && FGASettings.chainMinecartBinding;
        if (links == null || (!reason.shouldDestroy() && !breakForDimensionChange)) return;
        List<LinkKey> connected = linksFor(minecart.getUUID());
        for (LinkKey key : connected) {
            LinkRecord record = links.links.get(key);
            removeLink(key);
            if (record != null && record.refund()) dropChain(minecart);
        }
    }

    private static void solveLinks(Minecart minecart) {
        if (minecart.level() instanceof ServerLevel level) ensureLinks(level.getServer());
        if (links == null) return;
        MinecraftServer server = ((ServerLevel) minecart.level()).getServer();
        long tick = ((ServerLevel) minecart.level()).getGameTime();
        for (LinkKey key : linksFor(minecart.getUUID())) {
            Long previousTick = LAST_SOLVED_TICK.put(key, tick);
            if (previousTick != null && previousTick == tick) continue;
            Minecart first = findMinecart(server, key.first());
            Minecart second = findMinecart(server, key.second());
            if (first == null || second == null) continue;
            if (!FGASettings.chainMinecartBinding) continue;
            LinkRecord record = links.links.get(key);
            if (first.level() != second.level()) {
                removeLink(key);
                if (record != null && record.refund()) dropChain(first);
                continue;
            }
            Vec3 delta = second.position().subtract(first.position());
            double distance = delta.length();
            if (distance > BREAK_DISTANCE) {
                removeLink(key);
                if (record != null && record.refund()) dropChain(first);
                chainEffect(first, true);
                chainEffect(second, true);
                continue;
            }
            double maximum = MinecartFeatureConfig.snapshot().chainDistance();
            if (distance <= maximum || distance < 1.0E-6D) continue;
            Vec3 axis = delta.scale(1.0D / distance);
            double separationSpeed = second.getDeltaMovement().subtract(first.getDeltaMovement()).dot(axis);
            double force = Math.min(0.6D, (distance - maximum) * 0.35D
                    + Math.max(0.0D, separationSpeed) * 0.5D);
            first.setDeltaMovement(first.getDeltaMovement().add(axis.scale(force)));
            second.setDeltaMovement(second.getDeltaMovement().subtract(axis.scale(force)));

            if (distance > maximum + 0.25D
                    && tick - LAST_TENSION_PARTICLE.getOrDefault(key, Long.MIN_VALUE / 2L) >= 10L) {
                LAST_TENSION_PARTICLE.put(key, tick);
                Vec3 middle = first.position().add(second.position()).scale(0.5D);
                ((ServerLevel) first.level()).sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        middle.x, middle.y + 0.3D, middle.z, 3, 0.1D, 0.1D, 0.1D, 0.02D);
            }
        }
    }

    private static void chainEffect(Minecart minecart, boolean breaking) {
        if (!(minecart.level() instanceof ServerLevel level)) return;
        level.playSound(null, minecart.getX(), minecart.getY(), minecart.getZ(),
                breaking ? SoundEvents.CHAIN_BREAK : SoundEvents.CHAIN_PLACE,
                SoundSource.NEUTRAL, 0.8F, breaking ? 0.8F : 1.1F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, minecart.getX(), minecart.getY() + 0.4D,
                minecart.getZ(), 6, 0.2D, 0.1D, 0.2D, 0.03D);
    }

    private static void refund(LinkRecord record, Player player, Minecart fallback) {
        if (!record.refund()) return;
        ItemStack chain = new ItemStack(Items.CHAIN);
        if (!player.getInventory().add(chain)) dropChain(fallback);
    }

    private static void dropChain(Minecart minecart) {
        if (!(minecart.level() instanceof ServerLevel level)) return;
        level.addFreshEntity(new ItemEntity(level, minecart.getX(), minecart.getY() + 0.25D,
                minecart.getZ(), new ItemStack(Items.CHAIN)));
    }

    private static void removeLink(LinkKey key) {
        if (links == null || links.links.remove(key) == null) return;
        links.setDirty();
        LAST_SOLVED_TICK.remove(key);
        LAST_TENSION_PARTICLE.remove(key);
    }

    private static int degree(UUID minecart) {
        int count = 0;
        for (LinkKey key : links.links.keySet()) if (key.contains(minecart)) count++;
        return count;
    }

    private static boolean isLinked(UUID minecart) {
        return links != null && degree(minecart) > 0;
    }

    private static List<LinkKey> linksFor(UUID minecart) {
        List<LinkKey> result = new ArrayList<>();
        if (links == null) return result;
        for (LinkKey key : links.links.keySet()) if (key.contains(minecart)) result.add(key);
        return result;
    }

    private static boolean connected(UUID start, UUID target) {
        Set<UUID> visited = new HashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            UUID current = queue.removeFirst();
            if (!visited.add(current)) continue;
            if (current.equals(target)) return true;
            for (LinkKey key : links.links.keySet()) {
                UUID other = key.other(current);
                if (other != null && !visited.contains(other)) queue.addLast(other);
            }
        }
        return false;
    }

    private static Minecart findMinecart(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof Minecart minecart
                    && minecart.getMinecartType() == AbstractMinecart.Type.RIDEABLE) return minecart;
        }
        return null;
    }

    private static void ensureLinks(MinecraftServer server) {
        if (links == null && server != null && server.overworld() != null) {
            links = server.overworld().getDataStorage().computeIfAbsent(DATA_FACTORY, DATA_NAME);
        }
    }

    private static Vec3 horizontalDirection(Vec3 value) {
        double length = Math.sqrt(value.x * value.x + value.z * value.z);
        return length < 1.0E-6D ? Vec3.ZERO : new Vec3(value.x / length, 0.0D, value.z / length);
    }

    private static double horizontalSpeed(Vec3 movement) {
        return Math.sqrt(movement.x * movement.x + movement.z * movement.z);
    }

    private static double vanillaSpeed(Minecart minecart) {
        return minecart.isInWater() ? VANILLA_WATER_SPEED : VANILLA_LAND_SPEED;
    }

    private static void message(Player player, String text) {
        player.sendSystemMessage(Component.literal(text));
    }

    private static final class BoostState {
        private Vec3 direction;
        private int fullTicks;

        private BoostState(Vec3 direction, int fullTicks) {
            this.direction = direction;
            this.fullTicks = fullTicks;
        }
    }

    private record Selection(UUID minecart, net.minecraft.resources.ResourceKey<Level> dimension, long expiresAt) {
    }

    private record LinkRecord(boolean refund) {
    }

    private record LinkKey(UUID first, UUID second) {
        private static LinkKey of(UUID first, UUID second) {
            return first.compareTo(second) <= 0 ? new LinkKey(first, second) : new LinkKey(second, first);
        }

        private boolean contains(UUID value) {
            return first.equals(value) || second.equals(value);
        }

        private UUID other(UUID value) {
            if (first.equals(value)) return second;
            if (second.equals(value)) return first;
            return null;
        }
    }

    private static final class LinkData extends SavedData {
        private final Map<LinkKey, LinkRecord> links = new LinkedHashMap<>();

        private static LinkData load(CompoundTag tag, HolderLookup.Provider provider) {
            LinkData data = new LinkData();
            ListTag list = tag.getList("links", Tag.TAG_COMPOUND);
            for (int index = 0; index < list.size(); index++) {
                CompoundTag entry = list.getCompound(index);
                if (!entry.hasUUID("first") || !entry.hasUUID("second")) continue;
                UUID first = entry.getUUID("first");
                UUID second = entry.getUUID("second");
                if (first.equals(second)) continue;
                data.links.put(LinkKey.of(first, second), new LinkRecord(entry.getBoolean("refund")));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag list = new ListTag();
            for (Map.Entry<LinkKey, LinkRecord> link : links.entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("first", link.getKey().first());
                entry.putUUID("second", link.getKey().second());
                entry.putBoolean("refund", link.getValue().refund());
                list.add(entry);
            }
            tag.put("links", list);
            return tag;
        }
    }
}
//#endif
