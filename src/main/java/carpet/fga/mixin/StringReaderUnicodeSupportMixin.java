/*
 * Adapted from Yet Another Carpet Addition by Ryan100c and contributors.
 * The adapted portion is licensed under LGPL-3.0-or-later.
 * See META-INF/LICENSE-yaca-LGPL-3.0.txt in the distributed JAR.
 */
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import com.mojang.brigadier.StringReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StringReader.class)
public class StringReaderUnicodeSupportMixin {
    @Inject(method = "isAllowedInUnquotedString", at = @At("RETURN"), remap = false, cancellable = true)
    private static void carpetFga$allowUnicodeArguments(char character, CallbackInfoReturnable<Boolean> cir) {
        if (FGASettings.unicodeArgumentsSupport && !cir.getReturnValueZ()) {
            cir.setReturnValue(Character.isLetterOrDigit(character) || character > 0x7F);
        }
    }
}
