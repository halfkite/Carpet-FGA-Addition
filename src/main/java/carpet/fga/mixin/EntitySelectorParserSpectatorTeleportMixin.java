//#if MC >= 1.21 && MC <= 26.2
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
    @Inject(method = "allowSelectors", at = @At("RETURN"), cancellable = true, require = 0)
    private static void carpetFga$allowSpectatorSelectors(Object source, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && SpectatorFreeTeleport.allowEntitySelectors(source)) {
            cir.setReturnValue(true);
        }
    }
}
//#endif
