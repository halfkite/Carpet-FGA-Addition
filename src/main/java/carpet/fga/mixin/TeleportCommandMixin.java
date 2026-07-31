//#if MC >= 1.21.1 && MC <= 1.21.5
package carpet.fga.mixin;

import carpet.fga.SpectatorFreeTeleport;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * Priority is intentionally low so this wraps other permission/anti-cheat modifies last
 * and keeps spectator free teleport effective above them on the vanilla /tp path.
 */
@Mixin(value = TeleportCommand.class, priority = 50)
public abstract class TeleportCommandMixin {
    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;requires(Ljava/util/function/Predicate;)Lcom/mojang/brigadier/builder/ArgumentBuilder;",
                    remap = false
            ),
            index = 0
    )
    private static Predicate<CommandSourceStack> carpetFga$allowSpectatorFreeTeleport(
            Predicate<CommandSourceStack> original) {
        return source -> {
            try {
                if (original != null && original.test(source)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // Another anti-cheat predicate may throw for non-ops; still allow free-teleport spectators.
            }
            // TIS opPlayerNoCheat and AMS preventAdministratorCheat both turn the
            // vanilla permission predicate false. Restore the intended policy here:
            // actual operators retain the complete vanilla command, while eligible
            // non-operator spectators receive the self-only path.
            return SpectatorFreeTeleport.canUseTeleportCommand(source);
        };
    }

    @Inject(method = "teleportToEntity", at = @At("HEAD"))
    private static void carpetFga$restrictSpectatorTeleportToEntity(
            CommandSourceStack source,
            Collection<? extends Entity> targets,
            Entity destination,
            CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        SpectatorFreeTeleport.ensureSelfOnlyTargets(source, targets);
    }

    @Inject(method = "teleportToPos", at = @At("HEAD"))
    private static void carpetFga$restrictSpectatorTeleportToPos(
            CommandSourceStack source,
            Collection<? extends Entity> targets,
            ServerLevel level,
            Coordinates position,
            Coordinates rotation,
            @Coerce Object lookAt,
            CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        SpectatorFreeTeleport.ensureSelfOnlyTargets(source, targets);
    }
}
//#endif
