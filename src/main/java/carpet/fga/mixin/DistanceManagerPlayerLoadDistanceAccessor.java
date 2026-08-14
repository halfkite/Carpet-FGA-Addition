//#if MC == 1.21.1
package carpet.fga.mixin;

import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DistanceManager.class)
public interface DistanceManagerPlayerLoadDistanceAccessor {
    @Invoker("addTicket")
    void carpetFga$addTicket(TicketType type, ChunkPos pos, int level, Object identifier);

    @Invoker("removeTicket")
    void carpetFga$removeTicket(TicketType type, ChunkPos pos, int level, Object identifier);
}
//#endif
