//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.api.settings.SettingsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Uses a softer gray style for the long description shown by /carpet rule. */
@Mixin(SettingsManager.class)
public abstract class CarpetRuleDescriptionMixin {
    @ModifyArg(
            method = "displayRuleMenu",
            at = @At(value = "INVOKE", target =
                    "Lcarpet/utils/Messenger;m(Lnet/minecraft/commands/CommandSourceStack;[Ljava/lang/Object;)V", ordinal = 2),
            index = 1
    )
    private Object[] carpetFga$softenRuleDescription(Object[] fields) {
        if (fields.length == 1 && fields[0] instanceof String text && text.startsWith("w ")) {
            Object[] softened = fields.clone();
            softened[0] = "g " + text.substring(2);
            return softened;
        }
        return fields;
    }
}
//#endif
