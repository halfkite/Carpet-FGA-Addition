package carpet.fga;

//#if MC >= 1.21 && MC <= 26.3
//#if MC >= 1.21.5
//$$ import net.minecraft.world.entity.EntityReference;
//$$ import net.minecraft.world.level.entity.UniquelyIdentifyable;
//#endif
import carpet.CarpetServer;
import carpet.patches.EntityPlayerMPFake;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Converts between a player's persistent gameplay identity and its current body. */
public final class PossessionOwners {
    private PossessionOwners() {}

    public static boolean hasSessions() {
        var server = CarpetServer.minecraft_server;
        return server != null && server.isSameThread() && PlayerPossessionManager.hasSessions();
    }

    public static Entity counterpart(Entity entity) {
        if (!(entity instanceof ServerPlayer player)) return entity;
        var server = CarpetServer.minecraft_server;
        if (server == null || !server.isSameThread()) return entity;
        var partner = PlayerPossessionManager.partner(player);
        if (partner == null || !isFakePair(player, partner)) return entity;
        return partner;
    }

    /** Convert an action's player UUID at its assignment or local lookup, never during NBT decoding. */
    public static UUID counterpartId(UUID id) {
        var server = CarpetServer.minecraft_server;
        if (id == null || server == null || !server.isSameThread()
                || !PlayerPossessionManager.isParticipantId(id)) return id;
        var player = server.getPlayerList().getPlayer(id);
        return player == null ? id : counterpart(player).getUUID();
    }

    //#if MC >= 1.21.5
    //$$ public static <T extends UniquelyIdentifyable>
    //$$ EntityReference<T> bodyReference(EntityReference<T> stored) {
    //$$     if (stored == null || !hasSessions()) return stored;
    //$$     UUID id = stored.getUUID();
    //$$     UUID active = counterpartId(id);
    //$$     if (id.equals(active)) return stored;
    //#if MC >= 1.21.10
    //$$     return EntityReference.of(active);
    //#else
    //$$     return new EntityReference<>(active);
    //#endif
    //$$ }
    //#endif

    /** A cold vanilla lookup can miss B when B's login entity is in A's dimension. */
    public static ServerPlayer resolveMissingOwner(UUID storedId, Level level) {
        if (storedId == null || !(level instanceof ServerLevel serverLevel)) return null;
        var server = serverLevel.getServer();
        if (!server.isSameThread() || !PlayerPossessionManager.isParticipantId(storedId)) return null;
        var original = server.getPlayerList().getPlayer(storedId);
        if (original == null) return null;
        var active = (ServerPlayer) counterpart(original);
        return active != original && active.level() == level ? active : null;
    }

    private static boolean isFakePair(ServerPlayer first, ServerPlayer second) {
        return (first instanceof EntityPlayerMPFake) != (second instanceof EntityPlayerMPFake);
    }

    /** The rod uses this reverse link on the body that is holding it. */
    static void swapFishingHooks(ServerPlayer first, ServerPlayer second) {
        if (!isFakePair(first, second)) return;
        var hook = first.fishing;
        first.fishing = second.fishing;
        second.fishing = hook;
    }
}
//#endif
