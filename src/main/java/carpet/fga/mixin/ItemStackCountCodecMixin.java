//#if MC >= 1.20.5 && MC < 26.0
package carpet.fga.mixin;

import carpet.fga.DroppedItemStackLimitConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public abstract class ItemStackCountCodecMixin {
    @ModifyExpressionValue(
            method = "method_57371",
            //#if MC < 1.21
            //$$ at = @At(
            //$$         value = "FIELD",
            //$$         target = "Lnet/minecraft/util/ExtraCodecs;POSITIVE_INT:Lcom/mojang/serialization/Codec;"
            //$$ )
            //#else
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/ExtraCodecs;intRange(II)Lcom/mojang/serialization/Codec;"
            )
            //#endif
    )
    private static Codec<Integer> carpetFga$widenSavedStackCount(Codec<Integer> original) {
        return ExtraCodecs.intRange(1, DroppedItemStackLimitConfig.MAX_LIMIT);
    }
}
//#endif
