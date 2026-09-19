//#if MC == 1.21.1
package carpet.fga;

import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

/** Resolves a named ender pearl to an online player with the same name. */
public final class NamedEnderPearlTeleport {
    private NamedEnderPearlTeleport() {
    }

    public static ServerPlayer findTarget(ThrownEnderpearl pearl, ServerLevel level) {
        if (!FGASettings.namedEnderPearlTeleport) {
            return null;
        }

        ItemStack stack = pearl.getItem();
        if (!stack.is(Items.ENDER_PEARL)) {
            return null;
        }

        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName == null) {
            return null;
        }

        String playerName = customName.getString();
        if (playerName.isEmpty()) {
            return null;
        }

        ServerPlayer target = level.getServer().getPlayerList().getPlayerByName(playerName);
        return target != null && target.isAlive() ? target : null;
    }
}
//#endif
