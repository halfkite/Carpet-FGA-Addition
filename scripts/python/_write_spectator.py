from pathlib import Path

root = Path(r"D:/ai/carpet-fga")

def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.replace("\r\n", "\n"), encoding="utf-8", newline="\n")
    print(f"wrote {path}")

spectator = """//#if MC >= 1.21.1
package carpet.fga;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

/**
 * Allows non-OP spectators to use /tp and /teleport on themselves only.
 */
public final class SpectatorFreeTeleport {
    private static final int GAMEMASTER_PERMISSION_LEVEL = 2;
    private static final SimpleCommandExceptionType SELF_ONLY = new SimpleCommandExceptionType(
            Component.translatable("carpet-fga-addition.command.spectatorFreeTeleport.selfOnly")
    );

    private SpectatorFreeTeleport() {
    }

    public static boolean isPermissionBypassingSpectator(CommandSourceStack source) {
        if (!FGASettings.spectatorFreeTeleport || source.hasPermission(GAMEMASTER_PERMISSION_LEVEL)) {
            return false;
        }
        return source.getEntity() instanceof ServerPlayer player && player.isSpectator();
    }

    public static boolean canUseTeleportCommand(CommandSourceStack source) {
        return source.hasPermission(GAMEMASTER_PERMISSION_LEVEL) || isPermissionBypassingSpectator(source);
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
        for (Entity target : targets) {
            if (target != self) {
                throw SELF_ONLY.create();
            }
        }
    }
}
//#endif
"""

# Avoid referencing private nested LookAt: use @Inject with method descriptor only via CallbackInfo style
# Mixin can match by method name and use only needed prefix args if we use cancellable carefully.
# Safest: use @Inject(method="teleportToPos", at=@At("HEAD"), cancellable=false) with full descriptor string
# and args only via @Local or just use soft implementation with Redirect on nothing.
# Actually Mixin allows omitting trailing params? No.
# Use method descriptor in method= and Object for lookAt - Mixin docs say params must match.
# Alternative: inject with only CallbackInfoReturnable by using unique method slice? No, static inject needs all args or none with locals.
# Best approach used by many: @Inject method = "teleportToPos(L...;Ljava/util/Collection;...)V" 
# For private interface LookAt - from javap it is static nested with package or public?

teleport_mixin = """//#if MC >= 1.21.1
package carpet.fga.mixin;

import carpet.fga.SpectatorFreeTeleport;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.function.Predicate;

@Mixin(TeleportCommand.class)
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
        return source -> original.test(source) || SpectatorFreeTeleport.isPermissionBypassingSpectator(source);
    }

    @Inject(method = "teleportToEntity", at = @At("HEAD"))
    private static void carpetFga$restrictSpectatorTeleportToEntity(
            CommandSourceStack source,
            Collection<? extends Entity> targets,
            Entity destination,
            CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        SpectatorFreeTeleport.ensureSelfOnlyTargets(source, targets);
    }

    @Inject(
            method = "teleportToPos(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/Collection;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/commands/arguments/coordinates/Coordinates;Lnet/minecraft/commands/arguments/coordinates/Coordinates;Lnet/minecraft/server/commands/TeleportCommand$LookAt;)I",
            at = @At("HEAD")
    )
    private static void carpetFga$restrictSpectatorTeleportToPos(
            CommandSourceStack source,
            Collection<? extends Entity> targets,
            CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        SpectatorFreeTeleport.ensureSelfOnlyTargets(source, targets);
    }
}
//#endif
"""

# Wait - Mixin inject handlers MUST have the same arguments as the target method (plus CallbackInfo).
# You cannot omit middle/trailing args. So either full signature or use a different approach.
# Use @ModifyVariable on first Collection argument at HEAD? 
# @ModifyVariable(method="teleportToPos", at=@At("HEAD"), argsOnly=true, ordinal=0) 
# can receive Collection and validate, returning same collection - and throw from modify?

# Or use MixinExtras @WrapOperation - may not be available.

# Check if LookAt is public in bytecode via writing a note - use full accessible path.
# Private nested types ARE accessible in Mixin handler signatures because Mixin generates in same package? No, mixins are in carpet.fga.mixin.
# Private nested classes are NOT accessible from other packages in Java source - compilation will fail for TeleportCommand.LookAt.

# Solution: don't name LookAt in source. Use raw method injection via @Inject with CallbackInfo only?
# From Mixin wiki: "The handler method must have the same parameters as the target method, plus CallbackInfo"
# Exception: for target methods, you can use @Surrogate or @Coerce

# @Coerce Object lookAt might work:
# private static void handler(..., @Coerce Object lookAt, CallbackInfoReturnable<Integer> cir)

player_mixin = """//#if MC >= 1.21.1
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSpectatorTeleportMixin {
    @Inject(method = "setGameMode", at = @At("RETURN"))
    private void carpetFga$refreshCommandsAfterGameModeChange(
            GameType gameType, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) || !FGASettings.spectatorFreeTeleport) {
            return;
        }
        ServerPlayer player = (ServerPlayer) (Object) this;
        player.server.getCommands().sendCommands(player);
    }
}
//#endif
"""

# Final teleport mixin with @Coerce
teleport_mixin = """//#if MC >= 1.21.1
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

@Mixin(TeleportCommand.class)
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
        return source -> original.test(source) || SpectatorFreeTeleport.isPermissionBypassingSpectator(source);
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
"""

write(root / "src/main/java/carpet/fga/SpectatorFreeTeleport.java", spectator)
write(root / "src/main/java/carpet/fga/mixin/TeleportCommandMixin.java", teleport_mixin)
write(root / "src/main/java/carpet/fga/mixin/ServerPlayerSpectatorTeleportMixin.java", player_mixin)
print("core files done")