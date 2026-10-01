//#if MC == 26.3
//$$ package carpet.fga.mixin;
//$$ import carpet.patches.EntityPlayerMPFake;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.world.level.storage.ValueOutput;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /** Retain the existing quickopen identity marker when Carpet saves a live fake. Never mark real players. */
//$$ @Mixin(ServerPlayer.class)
//$$ public abstract class SorterInventoryIdentityMixin {
//$$     @Inject(method="addAdditionalSaveData(Lnet/minecraft/world/level/storage/ValueOutput;)V",at=@At("RETURN"))
//$$     private void carpetFga$saveFakeIdentity(ValueOutput output, CallbackInfo ci) {
//$$         if ((Object)this instanceof EntityPlayerMPFake fake && !fake.isAShadow)
//$$             output.putString("fgaOfflineSorterName",fake.getGameProfile().name());
//$$     }
//$$ }
//$$
//#endif
