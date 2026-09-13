package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.2
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatsCounter;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accesses the same stats table exposed by PlayerControl's access widener. */
@Mixin(StatsCounter.class)
public interface PossessionStatsCounterAccessor {
    @Accessor("stats")
    Object2IntMap<Stat<?>> fga$stats();
}
//#endif
