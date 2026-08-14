//#if MC >= 1.21 && MC <= 26.2
package carpet.fga;

import carpet.CarpetServer;
import carpet.fga.mixin.TrialSpawnerDataAccessor;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;

import java.util.Set;
import java.util.UUID;

public final class TrialSpawnerMultiplier {
    private TrialSpawnerMultiplier() {}

    public static int additionalPlayers(TrialSpawnerData data, int vanillaValue) {
        if (!enabled()) return vanillaValue;
        Set<UUID> players = ((TrialSpawnerDataAccessor) data).carpetFga$getDetectedPlayers();
        long equivalent = 0L;
        for (UUID player : players) {
            equivalent = Math.min((long) Integer.MAX_VALUE + 1L, equivalent + participantWeight(player));
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, equivalent - 1L));
    }

    public static int participantWeight(UUID player) {
        return matches(player) ? FGASettings.trialSpawnerPlayerMultiplier : 1;
    }

    private static boolean enabled() {
        return !"false".equals(FGASettings.trialSpawnerPlayerFilter)
                && FGASettings.trialSpawnerPlayerMultiplier > 1;
    }

    private static boolean matches(UUID playerId) {
        String filter = FGASettings.trialSpawnerPlayerFilter;
        if ("false".equals(filter)) return false;
        if ("true".equals(filter)) return true;
        MinecraftServer server = CarpetServer.minecraft_server;
        if (server == null) return false;
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) return online.getGameProfile().getName().startsWith(filter);
        return
                //#if MC >= 1.21.10
                //$$ server.services().nameToIdCache().get(playerId)
                //#else
                server.getProfileCache().get(playerId)
                //#endif
                .map(
                        //#if MC >= 1.21.10
                        //$$ net.minecraft.server.players.NameAndId::name
                        //#else
                        GameProfile::getName
                        //#endif
                )
                .map(name -> name.startsWith(filter))
                .orElse(false);
    }
}
//#endif
