//#if MC >= 26.0 && MC <= 26.2
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.DroppedItemStackLimitConfig;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.ModifyArg;
//$$
//$$ @Mixin(ItemStack.class)
//$$ public abstract class ItemStackCountCodec26Mixin {
//$$     @ModifyArg(
//$$             method = "lambda$static$1(Lcom/mojang/serialization/codecs/RecordCodecBuilder$Instance;)Lcom/mojang/datafixers/kinds/App;",
//$$             at = @At(
//$$                     value = "INVOKE",
//$$                     target = "Lnet/minecraft/util/ExtraCodecs;intRange(II)Lcom/mojang/serialization/Codec;"
//$$             ),
//$$             index = 1
//$$     )
//$$     private static int carpetFga$widenSavedStackCount(int originalMaximum) {
//$$         return DroppedItemStackLimitConfig.MAX_LIMIT;
//$$     }
//$$ }
//#endif
