//#if MC == 1.20.1
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.LegacyPlayerLoadDistanceManager;
//$$ import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
//$$ import net.minecraft.server.level.ChunkMap;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.world.level.ChunkPos;
//$$ import org.apache.commons.lang3.mutable.MutableObject;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ @Mixin(ChunkMap.class)
//$$ public abstract class LegacyChunkMapPlayerLoadDistanceMixin {
//$$     @Inject(method = "updateChunkTracking", at = @At("HEAD"), cancellable = true)
//$$     private void carpetFga$filterTracking(ServerPlayer player, ChunkPos position,
//$$             MutableObject<ClientboundLevelChunkWithLightPacket> packet, boolean wasInRange,
//$$             boolean isInRange, CallbackInfo callback) {
//$$         if (LegacyPlayerLoadDistanceManager.cancelVanillaTracking(
//$$                 player, position, wasInRange, isInRange)) callback.cancel();
//$$     }
//$$
//$$     @Inject(method = "move", at = @At("TAIL"))
//$$     private void carpetFga$refreshPlayerView(ServerPlayer player, CallbackInfo callback) {
//$$         LegacyPlayerLoadDistanceManager.reapply(player);
//$$     }
//$$ }
//#endif
