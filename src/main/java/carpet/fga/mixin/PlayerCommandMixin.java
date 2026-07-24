package carpet.fga.mixin;

import carpet.commands.PlayerCommand;
import carpet.fga.FGASettings;
//#if MC >= 1.21.1
import carpet.fga.FakePlayerProfilePreloadManager;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
//#endif
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 服务端 Mixin：覆盖 PlayerCommand.maxNameLength 方法，
 * 使用 FGASettings.fakePlayerNameLength 作为假人名字的最大长度。
 */
@Mixin(value = PlayerCommand.class, remap = false)
public class PlayerCommandMixin {

    //#if MC >= 1.21.1
    @Inject(method = "spawn", at = @At("HEAD"), cancellable = true, remap = false)
    private static void carpetFga$preloadCommandFakePlayerProfile(
            CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir) {
        if (FakePlayerProfilePreloadManager.interceptCommandSpawn(context)) {
            cir.setReturnValue(1);
        } else {
            FakePlayerProfilePreloadManager.beginCommandPassthrough();
        }
    }

    @Inject(method = "spawn", at = @At("RETURN"), remap = false)
    private static void carpetFga$finishCommandFakePlayerProfile(
            CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir) {
        FakePlayerProfilePreloadManager.endCommandPassthrough();
    }
    //#endif

    /**
     * 在 maxNameLength 方法入口处拦截，直接返回自定义的名字长度上限。
     */
    @Inject(method = "maxNameLength", at = @At("HEAD"), cancellable = true, remap = false)
    private static void overrideMaxNameLength(MinecraftServer server, CallbackInfoReturnable<Integer> cir) {
        if (FGASettings.fakePlayerNameLength > 0) {
            cir.setReturnValue(FGASettings.fakePlayerNameLength);
        }
    }
}
