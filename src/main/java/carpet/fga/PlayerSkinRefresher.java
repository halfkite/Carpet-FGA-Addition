package carpet.fga;

//#if MC >= 1.21 && MC <= 26.2
import carpet.CarpetServer;
import carpet.fga.mixin.PossessionChunkMapAccessor;
import carpet.fga.mixin.PossessionPlayerAccessor;
import carpet.fga.mixin.PossessionTrackedEntityAccessor;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.server.players.PlayerList;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Refreshes the vanilla client view after a reference-style profile swap. */
public final class PlayerSkinRefresher {
    private static final String TEXTURES_KEY = "textures";

    private PlayerSkinRefresher() {}

    public static void applyProfileAndRefresh(ServerPlayer player, GameProfile newProfile) {
        GameProfile current = player.getGameProfile();
        //#if MC >= 1.21.10
        //$$ GameProfile applied = new GameProfile(current.id(), newProfile.name(), newProfile.properties());
        //#else
        PropertyMap merged = new PropertyMap();
        for (var entry : current.getProperties().entries()) {
            if (!TEXTURES_KEY.equals(entry.getKey())) merged.put(entry.getKey(), entry.getValue());
        }
        for (Property texture : newProfile.getProperties().get(TEXTURES_KEY)) {
            merged.put(TEXTURES_KEY, texture);
        }
        GameProfile applied = new GameProfile(current.getId(), newProfile.getName());
        applied.getProperties().putAll(merged);
        //#endif
        ((PossessionPlayerAccessor) player).fga$gameProfile(applied);

        PlayerList players = Objects.requireNonNull(CarpetServer.minecraft_server).getPlayerList();
        players.broadcastAll(new ClientboundBundlePacket(List.of(
                new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())),
                ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(Collections.singleton(player))
        )));
        refreshEntityTracking(player);
        if (player.isDeadOrDying()) return;

        ServerLevel level = player.serverLevel();
        player.connection.send(new ClientboundBundlePacket(List.of(
                new ClientboundRespawnPacket(player.createCommonSpawnInfo(level), ClientboundRespawnPacket.KEEP_ALL_DATA),
                new ClientboundGameEventPacket(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0)
        )));
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        if (player.getVehicle() != null) player.connection.send(new ClientboundSetPassengersPacket(player.getVehicle()));
        if (!player.getPassengers().isEmpty()) player.connection.send(new ClientboundSetPassengersPacket(player));
        player.onUpdateAbilities();
        player.giveExperiencePoints(0);
        players.sendPlayerPermissionLevel(player);
        players.sendLevelInfo(player, level);
        players.sendAllPlayerInfo(player);
        for (var effect : player.getActiveEffects()) {
            player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), effect, false));
        }
    }

    private static void refreshEntityTracking(ServerPlayer player) {
        ChunkMap chunkMap = player.serverLevel().getChunkSource().chunkMap;
        var entityMap = ((PossessionChunkMapAccessor) chunkMap).fga$entityMap();
        PossessionTrackedEntityAccessor tracked = (PossessionTrackedEntityAccessor) entityMap.get(player.getId());
        if (tracked == null) return;
        Set<ServerPlayerConnection> observers = Set.copyOf(tracked.fga$seenBy());
        for (ServerPlayerConnection connection : observers) {
            ServerPlayer observer = connection.getPlayer();
            tracked.fga$removePlayer(observer);
            PossessionTrackedEntityAccessor observerTracked =
                    (PossessionTrackedEntityAccessor) entityMap.get(observer.getId());
            if (observerTracked != null) {
                observerTracked.fga$removePlayer(player);
                observerTracked.fga$updatePlayer(player);
            }
            tracked.fga$updatePlayer(observer);
        }
    }
}
//#endif
