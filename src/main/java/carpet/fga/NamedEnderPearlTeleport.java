//#if MC >= 1.21 && MC <= 26.3
package carpet.fga;

import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

/**
 * Resolves named ender pearls without falling back to vanilla teleportation.
 *
 * <p>Version-upgrade smoke entry point:
 * {@code powershell.exe -NoProfile -ExecutionPolicy Bypass -File
 * scripts/powershell/named-ender-pearl-teleport-smoke-all.ps1} runs the full
 * build matrix; append {@code -VersionList 26.2} to run one build node.</p>
 *
 * <p>The procedure enables {@code namedEnderPearlTeleport}, summons fake player
 * {@code 1} as the target and fake player {@code 2} as the thrower, gives player
 * {@code 2} a pearl named {@code 1}, and verifies that the first throw moves
 * player {@code 1}. It then takes player {@code 1} offline, repeats the throw,
 * and verifies that player {@code 2} remains in place. A clean server stop and
 * absence of Mixin injection errors are also required.</p>
 */
public final class NamedEnderPearlTeleport {
    private NamedEnderPearlTeleport() {
    }

    public static ServerPlayer findTarget(ThrownEnderpearl pearl, ServerLevel level) {
        if (!FGASettings.namedEnderPearlTeleport) {
            return null;
        }

        String playerName = customTargetName(pearl);
        if (playerName == null) return null;

        ServerPlayer target = level.players().stream()
                .filter(player -> playerName.equals(player.getGameProfile().getName()))
                .findFirst()
                .orElseGet(() -> level.getServer().getPlayerList().getPlayerByName(playerName));
        return target != null && target.isAlive() ? target : null;
    }

    public static boolean shouldCancelVanillaTeleport(ThrownEnderpearl pearl, ServerLevel level) {
        return FGASettings.namedEnderPearlTeleport
                && customTargetName(pearl) != null
                && findTarget(pearl, level) == null;
    }

    public static void teleportTarget(ThrownEnderpearl pearl, ServerPlayer target) {
        target.teleportTo(pearl.getX(), pearl.getY(), pearl.getZ());
        target.resetFallDistance();
        pearl.discard();
    }

    private static String customTargetName(ThrownEnderpearl pearl) {
        ItemStack stack = pearl.getItem();
        if (!stack.is(Items.ENDER_PEARL)) return null;

        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName == null) return null;

        String playerName = customName.getString();
        return playerName.isEmpty() ? null : playerName;
    }
}
//#endif
