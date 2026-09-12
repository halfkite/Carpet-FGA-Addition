package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.2
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface PossessionTrackedEntityAccessor {
    @Accessor("seenBy")
    Set<ServerPlayerConnection> fga$seenBy();

    @Invoker("removePlayer")
    void fga$removePlayer(ServerPlayer player);

    @Invoker("updatePlayer")
    void fga$updatePlayer(ServerPlayer player);
}
//#endif
