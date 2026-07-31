//#if MC >= 1.20.5 && MC < 26.2
package carpet.fga.mixin;

import java.util.Map;
import java.util.Set;
//#if MC >= 26.2
//$$ import net.minecraft.advancements.triggers.CriterionTrigger;
//#else
import net.minecraft.advancements.CriterionTrigger;
//#endif
//#if MC >= 26.2
//$$ import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
//#else
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
//#endif
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleCriterionTrigger.class)
public interface SimpleCriterionTriggerAccessor {
    @Accessor("players")
    Map<PlayerAdvancements, Set<CriterionTrigger.Listener<?>>> invadvopt$getPlayers();
}
//#endif

