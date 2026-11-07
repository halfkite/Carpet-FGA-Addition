package carpet.fga.mixin;

//#if MC >= 1.21.3 && MC <= 26.3
//$$ import carpet.fga.PossessionOwners;
//$$ import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$
//$$ import java.util.Set;
//$$
//$$ /** Keep flying pearls in their owner's playerdata even when the server saves during possession. */
//$$ @Mixin(ServerPlayer.class)
//$$ public abstract class PossessionPearlRegistryMixin {
//$$     @ModifyExpressionValue(method = {"registerEnderPearl", "deregisterEnderPearl"}, at = @At(
//$$             value = "FIELD", target = "Lnet/minecraft/server/level/ServerPlayer;enderPearls:Ljava/util/Set;"))
//$$     private Set<?> fga$persistentPearlRegistry(Set<?> original) {
//$$         ServerPlayer body = (ServerPlayer) (Object) this;
//$$         ServerPlayer owner = (ServerPlayer) PossessionOwners.counterpart(body);
//$$         return owner == body ? original : owner.getEnderPearls();
//$$     }
//$$ }
//#endif
