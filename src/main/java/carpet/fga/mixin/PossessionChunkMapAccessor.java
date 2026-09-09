package carpet.fga.mixin;

//#if MC == 1.21.1
import net.minecraft.server.level.ChunkMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface PossessionChunkMapAccessor {
    @Accessor("entityMap")
    Int2ObjectMap<?> fga$entityMap();
}
//#endif
