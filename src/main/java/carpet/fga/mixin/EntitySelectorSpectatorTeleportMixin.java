//#if MC >= 1.21.1 && MC <= 1.21.5
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
    @Inject(method = "checkPermissions", at = @At("HEAD"), cancellable = true, require = 0)
    private void carpetFga$bypassSpectatorSelectorPermission(CommandSourceStack source, CallbackInfo ci) {
        if (SpectatorFreeTeleport.bypassSelectorPermissionCheck(source)) {
            ci.cancel();
        }
    }
}
//#endif
