package carpet.fga;

//#if MC >= 1.21 && MC <= 26.2
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Immutable in-memory copy of one player body, based on PlayerControl. */
public final class SwapSnapshot {
    private final GameProfile profile;
    private final boolean fallFlying;
    private final boolean wonGame;
    private final HumanoidArm mainArm;
    //#if MC == 1.21.1
    private final boolean noCulling;
    //#else
    //$$ private final boolean noCulling = false;
    //#endif
    private final List<UUID> passengers;
    private final UUID vehicle;
    private final RemoteChatSession chatSession;
    private final ListTag attributes;
    private final AbilitiesState abilities;
    private final ServerLevel level;
    private final double x;
    private final double y;
    private final double z;
    private final BlockPos sleepingPos;
    private final float yRot;
    private final float xRot;
    private final List<ItemStack> mainInventory;
    private final List<ItemStack> armorInventory;
    private final List<ItemStack> offhandInventory;
    private final List<ItemStack> enderChest;
    private final int selectedSlot;
    private final float health;
    private final int foodLevel;
    private final float saturationLevel;
    //#if MC == 1.21.1
    private final float exhaustionLevel;
    //#else
    //$$ private final float exhaustionLevel = 0.0F;
    //#endif
    private final int experienceLevel;
    private final float experienceProgress;
    private final int totalExperience;
    private final GameType gameMode;
    private final List<MobEffectInstance> effects;
    private final List<SynchedEntityData.DataValue<?>> entityData;
    private final CompoundTag additionalData;

    private SwapSnapshot(ServerPlayer player) {
        this.profile = copyProfile(player.getGameProfile());
        this.fallFlying = player.isFallFlying();
        this.wonGame = player.wonGame;
        this.mainArm = player.getMainArm();
        //#if MC == 1.21.1
        this.noCulling = player.noCulling;
        //#endif
        this.chatSession = player.getChatSession();
        this.abilities = AbilitiesState.capture(player.getAbilities());
        //#if MC >= 1.21.8
        //$$ this.attributes = new ListTag();
        //#else
        this.attributes = player.getAttributes().save();
        //#endif
        this.level = player.serverLevel();
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.yRot = player.getYRot();
        this.xRot = player.getXRot();
        //#if MC >= 1.21.5
        //$$ this.mainInventory = copyItems(player.getInventory().getNonEquipmentItems());
        //$$ this.armorInventory = List.of(player.getItemBySlot(EquipmentSlot.FEET).copy(),
        //$$         player.getItemBySlot(EquipmentSlot.LEGS).copy(),
        //$$         player.getItemBySlot(EquipmentSlot.CHEST).copy(),
        //$$         player.getItemBySlot(EquipmentSlot.HEAD).copy());
        //$$ this.offhandInventory = List.of(player.getItemBySlot(EquipmentSlot.OFFHAND).copy());
        //$$ this.selectedSlot = player.getInventory().getSelectedSlot();
        //#else
        this.mainInventory = copyItems(player.getInventory().items);
        this.armorInventory = copyItems(player.getInventory().armor);
        this.offhandInventory = copyItems(player.getInventory().offhand);
        this.selectedSlot = player.getInventory().selected;
        //#endif
        this.enderChest = new ArrayList<>();
        for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
            this.enderChest.add(player.getEnderChestInventory().getItem(i).copy());
        }
        this.health = player.getHealth();
        FoodData food = player.getFoodData();
        this.foodLevel = food.getFoodLevel();
        this.saturationLevel = food.getSaturationLevel();
        //#if MC == 1.21.1
        this.exhaustionLevel = food.getExhaustionLevel();
        //#endif
        this.experienceLevel = player.experienceLevel;
        this.experienceProgress = player.experienceProgress;
        this.totalExperience = player.totalExperience;
        this.gameMode = player.gameMode.getGameModeForPlayer();
        this.effects = player.getActiveEffects().stream().map(MobEffectInstance::new).toList();
        this.vehicle = player.getVehicle() == null ? null : player.getVehicle().getUUID();
        this.passengers = player.getPassengers().stream().map(Entity::getUUID).toList();
        this.sleepingPos = player.isSleeping() && player.getSleepingPos().isPresent()
                ? player.getSleepingPos().get() : null;
        List<SynchedEntityData.DataValue<?>> values = player.getEntityData().getNonDefaultValues();
        //#if MC >= 1.21.5
        //$$ this.entityData = values == null ? List.of() : values;
        //#else
        this.entityData = values == null ? List.of() : values.stream()
                .filter(value -> value.serializer() != EntityDataSerializers.OPTIONAL_UUID)
                .toList();
        //#endif
        //#if MC >= 1.21.8
        //$$ this.additionalData = new CompoundTag();
        //#else
        CompoundTag additional = new CompoundTag();
        player.addAdditionalSaveData(additional);
        this.additionalData = additional;
        //#endif
    }

    public static SwapSnapshot capture(ServerPlayer player) {
        return new SwapSnapshot(player);
    }

    public GameProfile gameProfile() {
        return profile;
    }

    public void applyTo(ServerPlayer player, GameProfile profileToApply) {
        PlayerSkinRefresher.applyProfileAndRefresh(player, profileToApply);
        clearRiding(player);
        //#if MC >= 1.21.5
        //$$ copyItemsInto(player.getInventory().getNonEquipmentItems(), mainInventory);
        //$$ player.setItemSlot(EquipmentSlot.FEET, armorInventory.get(0).copy());
        //$$ player.setItemSlot(EquipmentSlot.LEGS, armorInventory.get(1).copy());
        //$$ player.setItemSlot(EquipmentSlot.CHEST, armorInventory.get(2).copy());
        //$$ player.setItemSlot(EquipmentSlot.HEAD, armorInventory.get(3).copy());
        //$$ player.setItemSlot(EquipmentSlot.OFFHAND, offhandInventory.get(0).copy());
        //#else
        copyItemsInto(player.getInventory().items, mainInventory);
        copyItemsInto(player.getInventory().armor, armorInventory);
        copyItemsInto(player.getInventory().offhand, offhandInventory);
        //#endif
        copyEnderChest(player);
        //#if MC < 1.21.8
        player.getAttributes().load(attributes.copy());
        //#endif
        player.setHealth(Math.min(health, player.getMaxHealth()));
        FoodData food = player.getFoodData();
        food.setFoodLevel(foodLevel);
        food.setSaturation(saturationLevel);
        //#if MC == 1.21.1
        food.setExhaustion(exhaustionLevel);
        //#endif
        player.experienceLevel = experienceLevel;
        player.setExperienceLevels(experienceLevel);
        player.totalExperience = totalExperience;
        player.experienceProgress = experienceProgress;
        player.setGameMode(gameMode);
        player.removeAllEffects();
        for (MobEffectInstance effect : effects) player.addEffect(new MobEffectInstance(effect));
        //#if MC >= 1.21.5
        //$$ player.getInventory().setSelectedSlot(selectedSlot);
        //#else
        player.getInventory().selected = selectedSlot;
        //#endif
        //#if MC == 1.21.1
        player.teleportTo(level, x, y, z, yRot, xRot);
        //#else
        //$$ player.teleportTo(level, x, y, z, java.util.Set.of(), yRot, xRot, false);
        //#endif
        if (!entityData.isEmpty()) player.getEntityData().assignValues(entityData);
        //#if MC < 1.21.8
        player.readAdditionalSaveData(additionalData.copy());
        //#endif
        applyRiding(player);
        if (sleepingPos != null) {
            player.startSleepInBed(sleepingPos);
        } else {
            player.clearSleepingPos();
            player.stopSleepInBed(true, true);
        }
        level.updateSleepingPlayerList();
        player.inventoryMenu.broadcastChanges();
        if (fallFlying) player.startFallFlying(); else player.stopFallFlying();
        player.wonGame = wonGame;
        player.setMainArm(mainArm);
        //#if MC == 1.21.1
        player.noCulling = noCulling;
        //#endif
        if (chatSession != null) player.setChatSession(chatSession);
        abilities.applyTo(player.getAbilities());
        player.onUpdateAbilities();
    }

    private void applyRiding(ServerPlayer player) {
        for (UUID uuid : passengers) {
            Entity entity = level.getEntity(uuid);
            //#if MC >= 1.21.10
            //$$ if (entity != null) entity.startRiding(player, true, true);
            //#else
            if (entity != null) entity.startRiding(player, true);
            //#endif
        }
        if (vehicle != null) {
            Entity entity = level.getEntity(vehicle);
            //#if MC >= 1.21.10
            //$$ if (entity != null) player.startRiding(entity, true, true);
            //#else
            if (entity != null) player.startRiding(entity, true);
            //#endif
        }
    }

    private static void clearRiding(ServerPlayer player) {
        player.stopRiding();
        for (Entity passenger : List.copyOf(player.getPassengers())) passenger.stopRiding();
    }

    private void copyEnderChest(ServerPlayer player) {
        for (int i = 0; i < Math.min(player.getEnderChestInventory().getContainerSize(), enderChest.size()); i++) {
            player.getEnderChestInventory().setItem(i, enderChest.get(i).copy());
        }
    }

    private static List<ItemStack> copyItems(List<ItemStack> source) {
        return source.stream().map(ItemStack::copy).toList();
    }

    private static void copyItemsInto(List<ItemStack> target, List<ItemStack> source) {
        for (int i = 0; i < target.size(); i++) {
            target.set(i, i < source.size() ? source.get(i).copy() : ItemStack.EMPTY);
        }
    }

    private static GameProfile copyProfile(GameProfile source) {
        //#if MC >= 1.21.10
        //$$ return new GameProfile(source.id(), source.name(), source.properties());
        //#else
        GameProfile copy = new GameProfile(source.getId(), source.getName());
        copy.getProperties().putAll(source.getProperties());
        return copy;
        //#endif
    }

    private record AbilitiesState(boolean invulnerable, boolean flying, boolean mayfly, boolean instabuild,
                                  float walkingSpeed, float flyingSpeed) {
        static AbilitiesState capture(Abilities source) {
            return new AbilitiesState(source.invulnerable, source.flying, source.mayfly, source.instabuild,
                    source.getWalkingSpeed(), source.getFlyingSpeed());
        }

        void applyTo(Abilities target) {
            target.invulnerable = invulnerable;
            target.flying = flying;
            target.mayfly = mayfly;
            target.instabuild = instabuild;
            target.setWalkingSpeed(walkingSpeed);
            target.setFlyingSpeed(flyingSpeed);
        }
    }
}
//#endif
