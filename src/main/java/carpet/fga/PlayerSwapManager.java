package carpet.fga;

//#if MC >= 1.21 && MC <= 26.2
import carpet.CarpetServer;
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
    /** Release interrupted midway; kept until both sides are restored so the swap is never half-committed. */
    private final Map<UUID, PendingRelease> pendingReleases = new HashMap<>();

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
            MinecraftServer server = CarpetServer.minecraft_server;
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

        PendingRelease pending = pendingReleases.get(uidA);
        if (pending == null) pending = pendingReleases.get(uidB);
        if (pending == null) {
            SwapSnapshot origA = originalStates.get(uidA);
            SwapSnapshot origB = originalStates.get(uidB);
            ServerPlayer playerB = server.getPlayerList().getPlayer(uidB);
            if (origA == null) {
                activeSwaps.remove(uidA);
                activeSwaps.remove(uidB);
                originalStates.remove(uidA);
                originalStates.remove(uidB);
                return SwapResult.fail(Component.translatable("fga.possession.failed"));
            }
            SwapSnapshot currentA = SwapSnapshot.capture(initiator);
            SwapSnapshot currentB = playerB == null ? null : SwapSnapshot.capture(playerB);
            GameProfile profileForB = origB == null ? null : origB.gameProfile();
            pending = new PendingRelease(uidA, uidB, currentB, origA.gameProfile(),
                    currentA, profileForB, origA, origB);
            pendingReleases.put(uidA, pending);
            pendingReleases.put(uidB, pending);
        }
        return completeRelease(pending, server);
    }

    private SwapResult completeRelease(PendingRelease pending, MinecraftServer server) {
        ServerPlayer playerA = server.getPlayerList().getPlayer(pending.uidA());
        ServerPlayer playerB = server.getPlayerList().getPlayer(pending.uidB());
        try {
            if (!pending.restoredA()) {
                if (playerA != null) {
                    SwapSnapshot payloadA = pending.currentB() == null ? pending.origA() : pending.currentB();
                    payloadA.applyTo(playerA, pending.profileForA());
                    server.getPlayerList().sendPlayerPermissionLevel(playerA);
                }
                pending.markRestoredA();
            }
            if (!pending.restoredB()) {
                if (playerB != null && pending.currentA() != null) {
                    GameProfile profileForB = pending.profileForB() == null
                            ? playerB.getGameProfile() : pending.profileForB();
                    pending.currentA().applyTo(playerB, profileForB);
                    server.getPlayerList().sendPlayerPermissionLevel(playerB);
                }
                pending.markRestoredB();
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("Possession release incomplete between {} and {}; state kept for retry",
                    pending.uidA(), pending.uidB(), failure);
            return SwapResult.fail(Component.translatable("fga.possession.failed"));
        }
        activeSwaps.remove(pending.uidA());
        activeSwaps.remove(pending.uidB());
        originalStates.remove(pending.uidA());
        originalStates.remove(pending.uidB());
        pendingReleases.remove(pending.uidA());
        pendingReleases.remove(pending.uidB());
        return SwapResult.successResult();
    }

    public SwapResult forceRelease(UUID targetUid, MinecraftServer server) {
        if (!activeSwaps.containsKey(targetUid)) return SwapResult.fail(Component.translatable("fga.possession.no_session"));
        ServerPlayer target = server.getPlayerList().getPlayer(targetUid);
        if (target != null) return release(target, server);

        UUID partnerUid = activeSwaps.get(targetUid);
        SwapSnapshot origTarget = originalStates.get(targetUid);
        SwapSnapshot origPartner = originalStates.get(partnerUid);
        ServerPlayer partner = server.getPlayerList().getPlayer(partnerUid);
        if (partner == null || origPartner == null) {
            activeSwaps.remove(targetUid);
            activeSwaps.remove(partnerUid);
            originalStates.remove(targetUid);
            originalStates.remove(partnerUid);
            pendingReleases.remove(targetUid);
            pendingReleases.remove(partnerUid);
            return SwapResult.fail(Component.translatable("fga.possession.failed"));
        }
        // The target is offline: restore the partner to their own original state and
        // discard the target's session changes (offline playerdata is never written).
        PendingRelease pending = new PendingRelease(partnerUid, targetUid, null,
                origPartner.gameProfile(), null, origTarget == null ? null : origTarget.gameProfile(),
                origPartner, origTarget);
        pending.markRestoredB();
        pendingReleases.put(targetUid, pending);
        pendingReleases.put(partnerUid, pending);
        SwapResult result = completeRelease(pending, server);
        if (result.success() && partner.isAlive() && !partner.isRemoved()) {
            partner.sendSystemMessage(PlayerPossessionManager.text(partner, "ended"));
        }
        return result;
    }

    public boolean isSwapped(UUID uid) { return activeSwaps.containsKey(uid); }
    public Optional<UUID> getSwapPartner(UUID uid) { return Optional.ofNullable(activeSwaps.get(uid)); }

    public String originalName(UUID uid, MinecraftServer server) {
        SwapSnapshot snapshot = originalStates.get(uid);
        if (snapshot != null) return PlayerPossessionManager.profileName(snapshot.gameProfile());
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(uid);
        return player == null ? uid.toString() : PlayerPossessionManager.profileName(player.getGameProfile());
    }

    public List<Map.Entry<UUID, UUID>> getAllSwapPairs() {
        Set<UUID> seen = new HashSet<>();
        List<Map.Entry<UUID, UUID>> pairs = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : activeSwaps.entrySet()) {
            if (seen.add(entry.getKey()) && seen.add(entry.getValue())) pairs.add(entry);
        }
        return pairs;
    }

    public Set<UUID> getAllParticipantIds() {
        return Set.copyOf(activeSwaps.keySet());
    }

    public void clear() {
        activeSwaps.clear();
        originalStates.clear();
        pendingReleases.clear();
    }

    private static final class PendingRelease {
        private final UUID uidA;
        private final UUID uidB;
        private final SwapSnapshot currentB;
        private final GameProfile profileForA;
        private final SwapSnapshot currentA;
        private final GameProfile profileForB;
        private final SwapSnapshot origA;
        private final SwapSnapshot origB;
        private boolean restoredA;
        private boolean restoredB;

        private PendingRelease(UUID uidA, UUID uidB, SwapSnapshot currentB, GameProfile profileForA,
                               SwapSnapshot currentA, GameProfile profileForB,
                               SwapSnapshot origA, SwapSnapshot origB) {
            this.uidA = uidA;
            this.uidB = uidB;
            this.currentB = currentB;
            this.profileForA = profileForA;
            this.currentA = currentA;
            this.profileForB = profileForB;
            this.origA = origA;
            this.origB = origB;
        }

        private UUID uidA() { return uidA; }
        private UUID uidB() { return uidB; }
        private SwapSnapshot currentB() { return currentB; }
        private GameProfile profileForA() { return profileForA; }
        private SwapSnapshot currentA() { return currentA; }
        private GameProfile profileForB() { return profileForB; }
        private SwapSnapshot origA() { return origA; }
        private boolean restoredA() { return restoredA; }
        private boolean restoredB() { return restoredB; }
        private void markRestoredA() { restoredA = true; }
        private void markRestoredB() { restoredB = true; }
    }

    public record SwapResult(boolean success, Component message) {
        public static SwapResult fail(Component message) { return new SwapResult(false, message); }
        public static SwapResult successResult() { return new SwapResult(true, Component.empty()); }
    }
}
//#endif
