package carpet.fga;

//#if MC == 1.21.1
import carpet.CarpetSettings;
import carpet.patches.EntityPlayerMPFake;
import carpet.utils.CommandHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

/** FGA command and rule adapter for the reference mod's in-memory state swap. */
public final class PlayerPossessionManager {
    private static final PlayerSwapManager SWAPS = new PlayerSwapManager();
    /** Direction is kept only for permission checks and rule changes. */
    private static final Map<UUID, UUID> CONTROLLERS = new HashMap<>();
    private static MinecraftServer server;

    private PlayerPossessionManager() {}

    public static Component text(ServerPlayer viewer, String key, Object... args) {
        String language = viewer == null ? CarpetSettings.language : viewer.clientInformation().language();
        String pattern = FGATranslations.getTranslations(language).getOrDefault("fga.possession." + key, key);
        return Component.literal(String.format(java.util.Locale.ROOT, pattern, args));
    }

    public static boolean allows(String rule, boolean op, boolean fake) {
        return switch (rule) {
            case "true" -> true;
            case "onlyfake" -> fake;
            case "opreal" -> fake || op;
            case "ops" -> op;
            default -> false;
        };
    }

    public static boolean canStart(ServerPlayer controller, ServerPlayer target) {
        return controller != null && target != null
                && !(controller instanceof EntityPlayerMPFake)
                && CommandHelper.canUseCommand(controller.createCommandSourceStack(), CarpetSettings.commandPlayer)
                && allows(FGASettings.playerPossession,
                controller.server.getPlayerList().isOp(controller.getGameProfile()),
                target instanceof EntityPlayerMPFake);
    }

    public static boolean isParticipant(ServerPlayer player) {
        return player != null && SWAPS.isSwapped(player.getUUID());
    }

    public static boolean isParticipantId(UUID id) {
        return id != null && SWAPS.isSwapped(id);
    }

    public static Optional<UUID> partnerById(UUID id) {
        return SWAPS.getSwapPartner(id);
    }

    public static boolean isSessionTarget(ServerPlayer actor, ServerPlayer target) {
        return actor != null && target != null && isParticipant(actor)
                && SWAPS.getSwapPartner(actor.getUUID()).map(target.getUUID()::equals).orElse(false);
    }

    public static boolean isController(ServerPlayer player) {
        return player != null && CONTROLLERS.containsKey(player.getUUID());
    }

    /** A real participant whose own connection must remain a viewer during a swap. */
    public static boolean isWatchedTarget(ServerPlayer player) {
        return player != null && isParticipant(player) && !isController(player);
    }

    public static ServerPlayer partner(ServerPlayer player) {
        if (player == null) return null;
        UUID partner = SWAPS.getSwapPartner(player.getUUID()).orElse(null);
        return partner == null ? null : player.server.getPlayerList().getPlayer(partner);
    }

    public static String start(ServerPlayer controller, ServerPlayer target) {
        if (!canStart(controller, target)) return "denied";
        if (controller == target) return "self";
        if (isParticipant(controller) || isParticipant(target)) return "busy";
        if (!controller.isAlive() || !target.isAlive() || controller.isRemoved() || target.isRemoved()
                || controller.server != target.server) return "unavailable";

        String controllerName = controller.getScoreboardName();
        String targetName = target.getScoreboardName();
        stopConflictingTasks(controller);
        stopConflictingTasks(target);
        controller.closeContainer();
        target.closeContainer();

        PlayerSwapManager.SwapResult result = SWAPS.swap(controller, target);
        if (!result.success()) return "failed";
        server = controller.server;
        CONTROLLERS.put(controller.getUUID(), target.getUUID());
        controller.sendSystemMessage(text(controller, "started", targetName, targetName));
        target.sendSystemMessage(text(target, "watched", controllerName, controllerName));
        return null;
    }

    private static void stopConflictingTasks(ServerPlayer player) {
        RangeActionManager.stop(player);
        FakePlayerItemSortManager.stop(player);
        FGACompat.actionPack(player).stopAll();
        player.stopUsingItem();
        player.setShiftKeyDown(false);
        player.setSprinting(false);
        player.setJumping(false);
        player.xxa = 0;
        player.zza = 0;
    }

    public static boolean stop(ServerPlayer actor, String name) {
        if (actor == null || !isParticipant(actor)) return false;
        ServerPlayer other = partner(actor);
        if (other == null) return false;
        String requested = name == null ? "" : name;
        if (!other.getScoreboardName().equalsIgnoreCase(requested)
                && !other.getGameProfile().getName().equalsIgnoreCase(requested)
                && !actor.getScoreboardName().equalsIgnoreCase(requested)
                && !actor.getGameProfile().getName().equalsIgnoreCase(requested)) return false;
        end(actor, actor.server);
        return true;
    }

    public static void endFor(ServerPlayer player) {
        if (player != null && isParticipant(player)) end(player, player.server);
    }

    public static void disconnected(ServerPlayer player, MinecraftServer currentServer) {
        if (player != null && isParticipant(player)) end(player, currentServer);
    }

    /** Handles permission changes and removals that do not emit a rule or disconnect event. */
    public static void tick(MinecraftServer currentServer) {
        if (server == null) server = currentServer;
        for (UUID controllerId : new HashSet<>(CONTROLLERS.keySet())) {
            UUID targetId = CONTROLLERS.get(controllerId);
            ServerPlayer controller = currentServer.getPlayerList().getPlayer(controllerId);
            ServerPlayer target = currentServer.getPlayerList().getPlayer(targetId);
            if (controller != null && target != null
                    && (controller.isChangingDimension() || target.isChangingDimension())) continue;
            if (controller == null || target == null
                    || !allows(FGASettings.playerPossession,
                    currentServer.getPlayerList().isOp(controller.getGameProfile()),
                    target instanceof EntityPlayerMPFake)) {
                if (controller != null) end(controller, currentServer);
            }
        }
    }

    private static void end(ServerPlayer actor, MinecraftServer currentServer) {
        UUID partnerId = SWAPS.getSwapPartner(actor.getUUID()).orElse(null);
        if (partnerId == null) return;
        ServerPlayer other = currentServer.getPlayerList().getPlayer(partnerId);
        CONTROLLERS.remove(actor.getUUID());
        CONTROLLERS.remove(partnerId);
        PlayerSwapManager.SwapResult result = SWAPS.release(actor, currentServer);
        if (!result.success()) return;
        if (actor.isAlive() && !actor.isRemoved()) actor.sendSystemMessage(text(actor, "ended"));
        if (other != null && other.isAlive() && !other.isRemoved()) other.sendSystemMessage(text(other, "ended"));
    }

    /** Close sessions which are no longer permitted after a rule change. */
    public static void onRuleChanged() {
        if (server == null) return;
        for (UUID controllerId : new HashSet<>(CONTROLLERS.keySet())) {
            UUID targetId = CONTROLLERS.get(controllerId);
            ServerPlayer controller = server.getPlayerList().getPlayer(controllerId);
            ServerPlayer target = server.getPlayerList().getPlayer(targetId);
            if (controller == null || target == null || !canStart(controller, target)) {
                if (controller != null) end(controller, server);
            }
        }
    }

    public static void clear() {
        if (server != null) {
            for (UUID id : new HashSet<>(SWAPS.getAllParticipantIds())) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) end(player, server);
            }
        }
        SWAPS.clear();
        CONTROLLERS.clear();
        server = null;
    }
}
//#endif
