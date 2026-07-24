//#if MC >= 1.21.8
//$$ package carpet.fga.mixin;
//$$
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//$$ import net.minecraft.network.protocol.Packet;
//$$ import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
//$$ import net.minecraft.server.network.config.SynchronizeRegistriesTask;
//$$ import net.minecraft.server.packs.repository.KnownPack;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$
//$$ import java.util.List;
//$$ import java.util.function.Consumer;
//$$
//$$ @Mixin(ServerConfigurationPacketListenerImpl.class)
//$$ public class DialogWarningServerConfigurationPacketListenerMixin {
//$$     @WrapOperation(
//$$         method = "handleSelectKnownPacks",
//$$         at = @At(
//$$             value = "INVOKE",
//$$             target = "Lnet/minecraft/server/network/config/SynchronizeRegistriesTask;handleResponse(Ljava/util/List;Ljava/util/function/Consumer;)V"
//$$         )
//$$     )
//$$     private void carpetFga$runWithPacketContext(
//$$             SynchronizeRegistriesTask task,
//$$             List<KnownPack> packs,
//$$             Consumer<Packet<?>> consumer,
//$$             Operation<Void> original
//$$     ) {
//$$         original.call(task, packs, consumer);
//$$     }
//$$ }
//#endif
