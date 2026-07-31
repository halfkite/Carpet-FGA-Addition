//#if MC >= 1.20.5
package carpet.fga.inventoryadvancement.index;

import java.util.Set;
//#if MC >= 26.2
//$$ import net.minecraft.advancements.triggers.CriterionTrigger;
//#else
//$$ import net.minecraft.advancements.CriterionTrigger;
//#endif
//#if MC >= 26.2
//$$ import net.minecraft.advancements.triggers.InventoryChangeTrigger;
//#else
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
//#endif

public record CompiledPlan(
        InventoryTriggerEntry listener,
        Set<Integer> rawItemIds,
        boolean alwaysCheck,
        boolean wildcard,
        boolean slotSensitive,
        boolean indexSafe) {
}
//#endif

