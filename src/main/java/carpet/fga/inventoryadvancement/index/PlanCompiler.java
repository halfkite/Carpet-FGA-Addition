//#if MC >= 1.20.5
package carpet.fga.inventoryadvancement.index;

import java.util.HashSet;
import java.util.Set;
//#if MC >= 26.2
//$$ import net.minecraft.advancements.triggers.CriterionTrigger;
//#else
import net.minecraft.advancements.CriterionTrigger;
//#endif
//#if MC >= 26.2
//$$ import net.minecraft.advancements.triggers.InventoryChangeTrigger;
//#else
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
//#endif
//#if MC >= 26.2
//$$ import net.minecraft.advancements.predicates.ItemPredicate;
//#else
import net.minecraft.advancements.critereon.ItemPredicate;
//#endif
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public final class PlanCompiler {
    private PlanCompiler() {}

    public static CompiledPlan compile(InventoryTriggerEntry listener) {
        InventoryChangeTrigger.TriggerInstance trigger = listener.trigger();
        Set<Integer> rawIds = new HashSet<>();
        boolean always = trigger.items().isEmpty() || trigger.player().isPresent();
        boolean wildcard = false;
        boolean safe = true;

        try {
            for (ItemPredicate predicate : trigger.items()) {
                if (predicate.items().isEmpty()) {
                    wildcard = true;
                    continue;
                }
                HolderSet<Item> holders = predicate.items().orElseThrow();
                for (Holder<Item> holder : holders) {
                    int rawId = BuiltInRegistries.ITEM.getId(holder.value());
                    if (rawId < 0) {
                        safe = false;
                    } else {
                        rawIds.add(rawId);
                    }
                }
            }
        } catch (RuntimeException | LinkageError exception) {
            safe = false;
        }

        InventoryChangeTrigger.TriggerInstance.Slots slots = trigger.slots();
        boolean slotSensitive = !slots.full().isAny() || !slots.empty().isAny() || !slots.occupied().isAny();
        if (!safe) {
            always = true;
        }
        return new CompiledPlan(listener, Set.copyOf(rawIds), always, wildcard, slotSensitive, safe);
    }
}
//#endif

