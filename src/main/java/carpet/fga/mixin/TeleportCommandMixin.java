//#if MC >= 1.21 && MC <= 26.3
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
 * On 26.3 this must run after the TIS, AMS, and Carpet Org permission modifiers so the
 * spectator exception wraps their final predicate instead of being wrapped and denied by them.
 */
//#if MC < 26.3
@Mixin(value = TeleportCommand.class, priority = 50)
//#else
//$$ @Mixin(value = TeleportCommand.class, priority = 2000)
//#endif
public abstract class TeleportCommandMixin {
    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;requires(Ljava/util/function/Predicate;)Lcom/mojang/brigadier/builder/ArgumentBuilder;",
                    remap = false
            ),
            index = 0,
            //#if MC < 26.3
            require = 0
            //#else
            //$$ require = 2
            //#endif
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
            // actual operators retain the complete vanilla command, full mode grants
            // player sources the complete command, and true grants spectators self-only access.
            return SpectatorFreeTeleport.canUseTeleportCommand(source);
        };
    }

    @Inject(
            method = "teleportToEntity",
            at = @At("HEAD"),
            //#if MC < 26.3
            require = 0
            //#else
            //$$ require = 1
            //#endif
    )
    private static void carpetFga$restrictSpectatorTeleportToEntity(
            CommandSourceStack source,
            Collection<? extends Entity> targets,
            Entity destination,
            CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        SpectatorFreeTeleport.ensureSelfOnlyTargets(source, targets);
    }

    @Inject(
            method = "teleportToPos",
            at = @At("HEAD"),
            //#if MC < 26.3
            require = 0
            //#else
            //$$ require = 1
            //#endif
    )
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
