//#if MC < 1.20.5
//$$ package carpet.fga.mixin;
//$$
//$$ import net.minecraft.nbt.CompoundTag;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.Redirect;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ @Mixin(ItemStack.class)
//$$ public abstract class ItemStackLegacyCountNbtMixin {
//$$     @Shadow
//$$     private int count;
//$$
//$$     @Redirect(
//$$             method = "save(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
//$$             at = @At(
//$$                     value = "INVOKE",
//$$                     target = "Lnet/minecraft/nbt/CompoundTag;putByte(Ljava/lang/String;B)V"
//$$             )
//$$     )
//$$     private void carpetFga$writeExtendedCount(CompoundTag tag, String key, byte vanillaCount) {
//$$         if ("Count".equals(key) && count > Byte.MAX_VALUE) {
//$$             tag.putInt(key, count);
//$$         } else {
//$$             tag.putByte(key, vanillaCount);
//$$         }
//$$     }
//$$
//$$     @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
//$$     private void carpetFga$readExtendedCount(CompoundTag tag, CallbackInfo ci) {
//$$         int savedCount = tag.getInt("Count");
//$$         if (savedCount > Byte.MAX_VALUE) {
//$$             count = savedCount;
//$$         }
//$$     }
//$$ }
//#endif
