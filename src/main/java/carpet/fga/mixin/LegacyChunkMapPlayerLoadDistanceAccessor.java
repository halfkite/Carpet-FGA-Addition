//#if MC == 1.20.1
//$$ package carpet.fga.mixin;
//$$
//$$ import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
//$$ import net.minecraft.server.level.ChunkMap;
//$$ import net.minecraft.server.level.DistanceManager;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.world.level.ChunkPos;
//$$ import org.apache.commons.lang3.mutable.MutableObject;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.gen.Invoker;
//$$
//$$ @Mixin(ChunkMap.class)
//$$ public interface LegacyChunkMapPlayerLoadDistanceAccessor {
//$$     @Invoker("updateChunkTracking")
//$$     void carpetFga$updateChunkTracking(ServerPlayer player, ChunkPos position,
//$$             MutableObject<ClientboundLevelChunkWithLightPacket> packet, boolean wasInRange, boolean isInRange);
//$$
//$$     @Invoker("updatePlayerStatus")
//$$     void carpetFga$updatePlayerStatus(ServerPlayer player, boolean add);
//$$
//$$     @Invoker("getDistanceManager")
//$$     DistanceManager carpetFga$getDistanceManager();
//$$ }
//#endif
