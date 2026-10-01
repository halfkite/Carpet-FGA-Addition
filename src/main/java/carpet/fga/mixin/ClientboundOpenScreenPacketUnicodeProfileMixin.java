//#if MC == 26.3
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.UnicodePlayerComponentSanitizer;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
//$$ import net.minecraft.world.inventory.MenuType;
//$$ import org.spongepowered.asm.mixin.Final;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Mutable;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ @Mixin(ClientboundOpenScreenPacket.class)
//$$ public abstract class ClientboundOpenScreenPacketUnicodeProfileMixin {
//$$     @Shadow
//$$     @Final
//$$     @Mutable
//$$     private Component title;
//$$
//$$     @Inject(method = "<init>", at = @At("TAIL"))
//$$     private void carpetFga$sanitizeUnicodePlayerProfiles(
//$$             int containerId, MenuType<?> menuType, Component title, CallbackInfo ci) {
//$$         this.title = UnicodePlayerComponentSanitizer.sanitize(this.title);
//$$     }
//$$ }
//#endif
