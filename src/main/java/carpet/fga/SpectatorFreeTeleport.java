//#if MC >= 1.20.1 && MC <= 1.21.5
package carpet.fga;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.util.Collection;

/**
 * Allows non-OP spectators to use /tp and /teleport on themselves only.
 * Takes priority over other non-operator cheat blocks for the vanilla teleport command path.
 */
public final class SpectatorFreeTeleport {
    private static final int GAMEMASTER_PERMISSION_LEVEL = 2;
    private static final SimpleCommandExceptionType SELF_ONLY = new SimpleCommandExceptionType(
            Component.translatable("carpet-fga-addition.command.spectatorFreeTeleport.selfOnly")
    );

    private SpectatorFreeTeleport() {
    }

    public static boolean isRealOperator(CommandSourceStack source) {
        if (source.hasPermission(GAMEMASTER_PERMISSION_LEVEL)) {
            return true;
        }
        if (source.getEntity() instanceof ServerPlayer player) {
            return player.server.getProfilePermissions(player.getGameProfile()) >= GAMEMASTER_PERMISSION_LEVEL;
        }
        return false;
    }

    /**
     * TIS and AMS intentionally remove cheat-command access from operators.
     * When either rule is active, do not turn the spectator convenience rule
     * into a way for operators to regain broader teleport authority.
     */
    public static boolean isOperatorCheatPreventionEnabled() {
        return readBooleanRule("carpettisaddition.CarpetTISAdditionSettings", "opPlayerNoCheat")
                || readBooleanRule("club.mcams.carpet.AmsServerSettings", "preventAdministratorCheat");
    }

    private static boolean readBooleanRule(String className, String fieldName) {
        try {
            Class<?> settingsClass = Class.forName(className, false, SpectatorFreeTeleport.class.getClassLoader());
            Field field = settingsClass.getField(fieldName);
            return field.getType() == boolean.class && field.getBoolean(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean isPermissionBypassingSpectator(CommandSourceStack source) {
        if (!FGASettings.spectatorFreeTeleport) {
            return false;
        }
        if (!(source.getEntity() instanceof ServerPlayer player) || !player.isSpectator()) {
            return false;
        }
        // With TIS/AMS cheat prevention enabled, operators have the same self-only
        // spectator route as non-operators. Otherwise operators retain vanilla /tp.
        return !isRealOperator(source) || isOperatorCheatPreventionEnabled();
    }

    public static boolean isPermissionBypassingSpectator(Object source) {
        return source instanceof CommandSourceStack stack && isPermissionBypassingSpectator(stack);
    }

    public static boolean canUseTeleportCommand(CommandSourceStack source) {
        return (isRealOperator(source) && !isOperatorCheatPreventionEnabled())
                || isPermissionBypassingSpectator(source);
    }

    /**
     * Allows entity-selector parsing/suggestions for free-teleport spectators so /tp @s ... works.
     */
    public static boolean allowEntitySelectors(Object source) {
        if (source instanceof SharedSuggestionProvider provider && provider.hasPermission(GAMEMASTER_PERMISSION_LEVEL)) {
            return true;
        }
        return isPermissionBypassingSpectator(source);
    }

    /**
     * Runtime selector permission bypass for free-teleport spectators.
     * Multi-target teleport remains blocked by ensureSelfOnlyTargets.
     */
    public static boolean bypassSelectorPermissionCheck(CommandSourceStack source) {
        return isPermissionBypassingSpectator(source);
    }

    public static void ensureSelfOnlyTargets(CommandSourceStack source, Collection<? extends Entity> targets)
            throws CommandSyntaxException {
        if (!isPermissionBypassingSpectator(source)) {
            return;
        }
        Entity self = source.getEntity();
        if (!(self instanceof ServerPlayer)) {
            throw SELF_ONLY.create();
        }
        if (targets.isEmpty()) {
            throw SELF_ONLY.create();
        }
        for (Entity target : targets) {
            if (target != self) {
                throw SELF_ONLY.create();
            }
        }
    }
}
//#endif
