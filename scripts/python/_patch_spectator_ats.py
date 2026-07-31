from pathlib import Path

root = Path(r"D:/ai/carpet-fga")

def write(rel: str, text: str) -> None:
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.replace("\r\n", "\n"), encoding="utf-8", newline="\n")
    print("wrote", rel)

spectator = r'''//#if MC >= 1.21.1
package carpet.fga;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

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

    public static boolean isPermissionBypassingSpectator(CommandSourceStack source) {
        if (!FGASettings.spectatorFreeTeleport || isRealOperator(source)) {
            return false;
        }
        return source.getEntity() instanceof ServerPlayer player && player.isSpectator();
    }

    public static boolean isPermissionBypassingSpectator(Object source) {
        return source instanceof CommandSourceStack stack && isPermissionBypassingSpectator(stack);
    }

    public static boolean canUseTeleportCommand(CommandSourceStack source) {
        return isRealOperator(source) || isPermissionBypassingSpectator(source);
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
'''

teleport_mixin = r'''//#if MC >= 1.21.1
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
            return SpectatorFreeTeleport.isPermissionBypassingSpectator(source);
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
'''

# Two top-level classes in one file is invalid Java. Split into two files.
selector_parser_mixin = r'''//#if MC >= 1.21.1
package carpet.fga.mixin;

import carpet.fga.SpectatorFreeTeleport;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enables /tp @s ... for free-teleport spectators by allowing selector parsing.
 * Low priority so this remains effective above other selector/anti-cheat mixins.
 */
@Mixin(value = EntitySelectorParser.class, priority = 50)
public abstract class EntitySelectorParserSpectatorTeleportMixin {
    @Inject(method = "allowSelectors", at = @At("RETURN"), cancellable = true)
    private static void carpetFga$allowSpectatorSelectors(Object source, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && SpectatorFreeTeleport.allowEntitySelectors(source)) {
            cir.setReturnValue(true);
        }
    }
}
//#endif
'''

selector_mixin = r'''//#if MC >= 1.21.1
package carpet.fga.mixin;

import carpet.fga.SpectatorFreeTeleport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runtime selector permission bypass for free-teleport spectators, including @s.
 * Low priority so this remains effective above other anti-cheat mixins.
 */
@Mixin(value = EntitySelector.class, priority = 50)
public abstract class EntitySelectorSpectatorTeleportMixin {
    @Inject(method = "checkPermissions", at = @At("HEAD"), cancellable = true)
    private void carpetFga$bypassSpectatorSelectorPermission(CommandSourceStack source, CallbackInfo ci) {
        if (SpectatorFreeTeleport.bypassSelectorPermissionCheck(source)) {
            ci.cancel();
        }
    }
}
//#endif
'''

write("src/main/java/carpet/fga/SpectatorFreeTeleport.java", spectator)
write("src/main/java/carpet/fga/mixin/TeleportCommandMixin.java", teleport_mixin)
write("src/main/java/carpet/fga/mixin/EntitySelectorParserSpectatorTeleportMixin.java", selector_parser_mixin)
write("src/main/java/carpet/fga/mixin/EntitySelectorSpectatorTeleportMixin.java", selector_mixin)

# Update mixins.json
mixins_path = root / "src/main/resources/carpet-fga-addition.mixins.json"
mixins = mixins_path.read_text(encoding="utf-8")
if "EntitySelectorSpectatorTeleportMixin" not in mixins:
    old = '''        ,"ClientDimensionIdPacketMixin",
        "TeleportCommandMixin",
        "ServerPlayerSpectatorTeleportMixin"
        //#endif'''
    new = '''        ,"ClientDimensionIdPacketMixin",
        "TeleportCommandMixin",
        "ServerPlayerSpectatorTeleportMixin",
        "EntitySelectorParserSpectatorTeleportMixin",
        "EntitySelectorSpectatorTeleportMixin"
        //#endif'''
    if old not in mixins:
        raise SystemExit("mixins anchor missing:\n" + mixins)
    mixins_path.write_text(mixins.replace(old, new, 1), encoding="utf-8", newline="\n")
    print("updated mixins.json")
else:
    print("mixins already has selector spectator mixins")

# Update lang extras and README briefly
en = root / "src/main/resources/assets/carpet-fga-addition/lang/en_us.json"
en_text = en.read_text(encoding="utf-8")
if "tp @s" not in en_text:
    en_text = en_text.replace(
        '"carpet.rule.spectatorFreeTeleport.extra.1": "true: Non-OP spectators may teleport themselves to coordinates or entities. Teleporting other entities is still denied."',
        '"carpet.rule.spectatorFreeTeleport.extra.1": "true: Non-OP spectators may teleport themselves, including /tp @s and coordinate targets. Teleporting other entities is still denied. This bypass has priority over other non-OP cheat blocks on vanilla /tp."'
    )
    en.write_text(en_text, encoding="utf-8", newline="\n")
    print("updated en_us")

zh = root / "src/main/resources/assets/carpet-fga-addition/lang/zh_cn.json"
zh_text = zh.read_text(encoding="utf-8")
if "tp @s" not in zh_text and "/tp @s" not in zh_text:
    zh_text = zh_text.replace(
        '"carpet.rule.spectatorFreeTeleport.extra.1": "true：非 OP 旁观者可传送自己到坐标或其他实体；传送其他实体仍会被拒绝。"',
        '"carpet.rule.spectatorFreeTeleport.extra.1": "true：非 OP 旁观者可传送自己，包括 /tp @s 与坐标目标；传送其他实体仍会被拒绝。该放行在原版 /tp 路径上优先于其他禁止非 OP 作弊的限制。"'
    )
    zh.write_text(zh_text, encoding="utf-8", newline="\n")
    print("updated zh_cn")

readme = root / "README.md"
readme_text = readme.read_text(encoding="utf-8")
if "tp @s" not in readme_text:
    readme_text = readme_text.replace(
        "开启后，**旁观模式且非 OP** 的玩家可以使用原版 `/tp` 与 `/teleport`，但**只能传送自己**（例如 `/tp ~ ~10 ~`、`/tp <实体>`、`/tp @s <坐标>`）。真正权限等级 ≥ 2 的 OP 不受额外限制。关闭规则时完全保持原版权限。",
        "开启后，**旁观模式且非 OP** 的玩家可以使用原版 `/tp` 与 `/teleport`，但**只能传送自己**（例如 `/tp ~ ~10 ~`、`/tp <实体>`、`/tp @s <坐标>`）。`@s` 选择器会一并放行。该放行在原版 `/tp` 路径上优先于其他禁止非 OP 使用作弊指令的限制。真正权限等级 ≥ 2 的 OP 不受额外限制。关闭规则时完全保持原版权限。"
    )
    readme.write_text(readme_text, encoding="utf-8", newline="\n")
    print("updated README")

print("done")