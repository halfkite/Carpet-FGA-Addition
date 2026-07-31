//#if MC >= 1.20.5
package carpet.fga.inventoryadvancement.index;

//#if MC >= 26.2
//$$ import net.minecraft.advancements.AdvancementHolder;
//$$ import net.minecraft.advancements.triggers.InventoryChangeTrigger;
//#else
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
//#endif
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.resources.ResourceLocation;

/**
 * Stable representation of one inventory advancement criterion.
 *
 * Mojang removed CriterionTrigger.Listener in 26.2. Keeping the index bound
 * to this small value object lets the selector use the same algorithm on both
 * trigger implementations without depending on their private storage format.
 */
public final class InventoryTriggerEntry {
    //#if MC >= 26.2
    //$$ private final AdvancementHolder advancement;
    //$$ private final String criterion;
    //$$ private final InventoryChangeTrigger.TriggerInstance trigger;
    //$$
    //$$ public InventoryTriggerEntry(AdvancementHolder advancement, String criterion,
    //$$         InventoryChangeTrigger.TriggerInstance trigger) {
    //$$     this.advancement = advancement;
    //$$     this.criterion = criterion;
    //$$     this.trigger = trigger;
    //$$ }
    //#else
    private final CriterionTrigger.Listener<InventoryChangeTrigger.TriggerInstance> listener;
    public InventoryTriggerEntry(CriterionTrigger.Listener<InventoryChangeTrigger.TriggerInstance> listener) {
        this.listener = listener;
    }
    //#endif

    public InventoryChangeTrigger.TriggerInstance trigger() {
        //#if MC >= 26.2
        //$$ return trigger;
        //#else
        return listener.trigger();
        //#endif
    }

    public ResourceLocation advancementId() {
        //#if MC >= 26.2
        //$$ return advancement.id();
        //#else
        return listener.advancement().id();
        //#endif
    }

    public String criterion() {
        //#if MC >= 26.2
        //$$ return criterion;
        //#else
        return listener.criterion();
        //#endif
    }

    public void award(PlayerAdvancements advancements) {
        //#if MC >= 26.2
        //$$ advancements.award(advancement, criterion);
        //#else
        listener.run(advancements);
        //#endif
    }
}
//#endif
