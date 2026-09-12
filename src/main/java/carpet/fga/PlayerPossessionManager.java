package carpet.fga;

//#if MC >= 1.21 && MC <= 26.2
import carpet.CarpetSettings;
import carpet.CarpetServer;
import carpet.patches.EntityPlayerMPFake;
import carpet.utils.CommandHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
//#if MC >= 1.21.10
//$$ import net.minecraft.server.players.NameAndId;
//#endif
import net.minecraft.world.entity.player.Player;

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

    public static boolean isOp(MinecraftServer currentServer, com.mojang.authlib.GameProfile profile) {
        //#if MC >= 1.21.10
        //$$ return currentServer.getPlayerList().isOp(new NameAndId(profile));
        //#else
        return currentServer.getPlayerList().isOp(profile);
        //#endif
    }

    public static String profileName(com.mojang.authlib.GameProfile profile) {
        //#if MC >= 1.21.10
        //$$ return profile.name();
        //#else
        return profile.getName();
        //#endif
    }

    public static boolean canStart(ServerPlayer controller, ServerPlayer target) {
        return controller != null && target != null
                && !(controller instanceof EntityPlayerMPFake)
                && CommandHelper.canUseCommand(
                //#if MC == 1.21.1
                controller.createCommandSourceStack(),
                //#else
                //$$ controller.createCommandSourceStackForNameResolution(controller.serverLevel()),
                //#endif
                CarpetSettings.commandPlayer)
                && allows(FGASettings.playerPossession,
                isOp(CarpetServer.minecraft_server, controller.getGameProfile()),
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
        return partner == null ? null : CarpetServer.minecraft_server.getPlayerList().getPlayer(partner);
    }

    public static String originalName(UUID id, MinecraftServer currentServer) {
        return SWAPS.originalName(id, currentServer);
    }

    public static Component decorateName(ServerPlayer player, Component base) {
        if (!FGASettings.showControllerPrefix || player == null || !isParticipant(player)) return base;
        UUID partnerId = SWAPS.getSwapPartner(player.getUUID()).orElse(null);
        if (partnerId == null) return base;
        String ownName = SWAPS.originalName(player.getUUID(), CarpetServer.minecraft_server);
        MutableComponent decorated = Component.literal(ownName);
        if (!CONTROLLERS.containsKey(player.getUUID())) {
            String controllerName = SWAPS.originalName(partnerId, CarpetServer.minecraft_server);
            decorated.append(Component.literal("[" + controllerName + "]").withStyle(ChatFormatting.RED));
        }
        return decorated;
    }

    public static Component decoratePlayerInfo(UUID id, Component base, String fallbackName,
                                               MinecraftServer currentServer) {
        if (!FGASettings.showControllerPrefix || id == null || !SWAPS.isSwapped(id)) return base;
        UUID partnerId = SWAPS.getSwapPartner(id).orElse(null);
        if (partnerId == null) return base;
        String currentName = originalName(id, currentServer);
        if (currentName == null || currentName.isEmpty()) currentName = fallbackName;
        MutableComponent decorated = Component.literal(currentName);
        if (!CONTROLLERS.containsKey(id)) {
            String controllerName = originalName(partnerId, currentServer);
            decorated.append(Component.literal("[" + controllerName + "]")
                    .withStyle(ChatFormatting.RED));
        }
        return decorated;
    }

    public static Component decorateName(Player player, Component base) {
        return player instanceof ServerPlayer serverPlayer ? decorateName(serverPlayer, base) : base;
    }

    public static boolean isActive() {
        return !"false".equals(FGASettings.playerPossession);
    }

    public static void refreshPermissions(MinecraftServer currentServer) {
        if (currentServer == null) return;
        for (UUID id : SWAPS.getAllParticipantIds()) {
            ServerPlayer player = currentServer.getPlayerList().getPlayer(id);
            if (player != null) currentServer.getPlayerList().sendPlayerPermissionLevel(player);
        }
    }

    public static void refreshDisplayNames(MinecraftServer currentServer) {
        if (currentServer == null) return;
        currentServer.getPlayerList().broadcastAll(
                net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
                        .createPlayerInitializing(currentServer.getPlayerList().getPlayers()));
    }

    public static java.util.List<java.util.Map.Entry<UUID, UUID>> swapPairs() {
        return java.util.List.copyOf(CONTROLLERS.entrySet());
    }

    public static UUID swapPartner(UUID id) {
        return SWAPS.getSwapPartner(id).orElse(null);
    }

    public static String start(ServerPlayer controller, ServerPlayer target) {
        if (!canStart(controller, target)) return "denied";
        if (controller == target) return "self";
        if (isParticipant(controller) || isParticipant(target)) return "busy";
        if (!controller.isAlive() || !target.isAlive() || controller.isRemoved() || target.isRemoved()
                || CarpetServer.minecraft_server == null) return "unavailable";

        String controllerName = controller.getScoreboardName();
        String targetName = target.getScoreboardName();
        stopConflictingTasks(controller);
        stopConflictingTasks(target);
        controller.closeContainer();
        target.closeContainer();

        CONTROLLERS.put(controller.getUUID(), target.getUUID());
        PlayerSwapManager.SwapResult result = SWAPS.swap(controller, target);
        if (!result.success()) {
            CONTROLLERS.remove(controller.getUUID());
            return "failed";
        }
        server = CarpetServer.minecraft_server;
        refreshDisplayNames(server);
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
                && !profileName(other.getGameProfile()).equalsIgnoreCase(requested)
                && !actor.getScoreboardName().equalsIgnoreCase(requested)
                && !profileName(actor.getGameProfile()).equalsIgnoreCase(requested)) return false;
        end(actor, CarpetServer.minecraft_server);
        return true;
    }

    public static void endFor(ServerPlayer player) {
        if (player != null && isParticipant(player)) end(player, CarpetServer.minecraft_server);
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
                    isOp(currentServer, controller.getGameProfile()),
                    target instanceof EntityPlayerMPFake)) {
                if (controller != null) end(controller, currentServer);
            }
        }
    }

    private static void end(ServerPlayer actor, MinecraftServer currentServer) {
        UUID partnerId = SWAPS.getSwapPartner(actor.getUUID()).orElse(null);
        if (partnerId == null) return;
        ServerPlayer other = currentServer.getPlayerList().getPlayer(partnerId);
        PlayerSwapManager.SwapResult result;
        try {
            result = SWAPS.release(actor, currentServer);
        } finally {
            CONTROLLERS.remove(actor.getUUID());
            CONTROLLERS.remove(partnerId);
        }
        if (!result.success()) return;
        refreshDisplayNames(currentServer);
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
