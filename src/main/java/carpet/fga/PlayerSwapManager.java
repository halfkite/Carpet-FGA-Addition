package carpet.fga;

//#if MC == 1.21.1
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * State swap adapted from the user-provided PlayerControl reference mod.
 * The table is server-thread confined and never writes offline playerdata.
 */
public final class PlayerSwapManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/possession-swap");
    private final Map<UUID, UUID> activeSwaps = new HashMap<>();
    private final Map<UUID, SwapSnapshot> originalStates = new HashMap<>();

    public SwapResult swap(ServerPlayer playerA, ServerPlayer playerB) {
        UUID uidA = playerA.getUUID();
        UUID uidB = playerB.getUUID();
        if (uidA.equals(uidB)) return SwapResult.fail(Component.translatable("fga.possession.self"));
        if (activeSwaps.containsKey(uidA) || activeSwaps.containsKey(uidB)) {
            return SwapResult.fail(Component.translatable("fga.possession.busy"));
        }

        SwapSnapshot snapA = SwapSnapshot.capture(playerA);
        SwapSnapshot snapB = SwapSnapshot.capture(playerB);
        activeSwaps.put(uidA, uidB);
        activeSwaps.put(uidB, uidA);
        originalStates.put(uidA, snapA);
        originalStates.put(uidB, snapB);
        try {
            snapB.applyTo(playerA, snapB.gameProfile());
            snapA.applyTo(playerB, snapA.gameProfile());
            MinecraftServer server = playerA.getServer();
            server.getPlayerList().sendPlayerPermissionLevel(playerA);
            server.getPlayerList().sendPlayerPermissionLevel(playerB);
            return SwapResult.successResult();
        } catch (RuntimeException failure) {
            LOGGER.warn("Unable to swap {} with {}", playerA.getScoreboardName(), playerB.getScoreboardName(), failure);
            activeSwaps.remove(uidA);
            activeSwaps.remove(uidB);
            originalStates.remove(uidA);
            originalStates.remove(uidB);
            try {
                snapA.applyTo(playerA, snapA.gameProfile());
                snapB.applyTo(playerB, snapB.gameProfile());
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            return SwapResult.fail(Component.translatable("fga.possession.failed"));
        }
    }

    public SwapResult release(ServerPlayer initiator, MinecraftServer server) {
        UUID uidA = initiator.getUUID();
        UUID uidB = activeSwaps.get(uidA);
        if (uidB == null) return SwapResult.fail(Component.translatable("fga.possession.no_session"));

        SwapSnapshot origA = originalStates.get(uidA);
        SwapSnapshot origB = originalStates.get(uidB);
        ServerPlayer playerB = server.getPlayerList().getPlayer(uidB);
        SwapSnapshot currentA = SwapSnapshot.capture(initiator);
        SwapSnapshot currentB = playerB == null ? null : SwapSnapshot.capture(playerB);
        activeSwaps.remove(uidA);
        activeSwaps.remove(uidB);
        originalStates.remove(uidA);
        originalStates.remove(uidB);

        if (origA == null) return SwapResult.fail(Component.translatable("fga.possession.failed"));
        try {
            if (currentB != null) currentB.applyTo(initiator, origA.gameProfile());
            else origA.applyTo(initiator, origA.gameProfile());
            if (playerB != null) {
                GameProfile profileForB = origB == null ? playerB.getGameProfile() : origB.gameProfile();
                currentA.applyTo(playerB, profileForB);
            }
            server.getPlayerList().sendPlayerPermissionLevel(initiator);
            if (playerB != null) server.getPlayerList().sendPlayerPermissionLevel(playerB);
            return SwapResult.successResult();
        } catch (RuntimeException failure) {
            return SwapResult.fail(Component.translatable("fga.possession.failed"));
        }
    }

    public SwapResult forceRelease(UUID targetUid, MinecraftServer server) {
        if (!activeSwaps.containsKey(targetUid)) return SwapResult.fail(Component.translatable("fga.possession.no_session"));
        ServerPlayer target = server.getPlayerList().getPlayer(targetUid);
        if (target != null) return release(target, server);

        UUID partnerUid = activeSwaps.get(targetUid);
        SwapSnapshot partnerOriginal = originalStates.get(partnerUid);
        ServerPlayer partner = server.getPlayerList().getPlayer(partnerUid);
        activeSwaps.remove(targetUid);
        activeSwaps.remove(partnerUid);
        originalStates.remove(targetUid);
        originalStates.remove(partnerUid);
        if (partner != null && partnerOriginal != null) {
            partnerOriginal.applyTo(partner, partnerOriginal.gameProfile());
            partner.sendSystemMessage(PlayerPossessionManager.text(partner, "ended"));
            server.getPlayerList().sendPlayerPermissionLevel(partner);
        }
        return SwapResult.successResult();
    }

    public boolean isSwapped(UUID uid) { return activeSwaps.containsKey(uid); }
    public Optional<UUID> getSwapPartner(UUID uid) { return Optional.ofNullable(activeSwaps.get(uid)); }

    public Set<UUID> getAllParticipantIds() {
        return Set.copyOf(activeSwaps.keySet());
    }

    public List<Map.Entry<UUID, UUID>> getAllSwapPairs() {
        Set<UUID> seen = new HashSet<>();
        List<Map.Entry<UUID, UUID>> pairs = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : activeSwaps.entrySet()) {
            if (seen.add(entry.getKey()) && seen.add(entry.getValue())) pairs.add(entry);
        }
        return pairs;
    }

    public void clear() {
        activeSwaps.clear();
        originalStates.clear();
    }

    public record SwapResult(boolean success, Component message) {
        public static SwapResult fail(Component message) { return new SwapResult(false, message); }
        public static SwapResult successResult() { return new SwapResult(true, Component.empty()); }
    }
}
//#endif
