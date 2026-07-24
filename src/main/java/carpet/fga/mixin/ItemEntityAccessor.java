//#if MC <= 26.2
package carpet.fga.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntity.class)
public interface ItemEntityAccessor {
    @Accessor("pickupDelay")
    int carpetFga$getPickupDelay();

    @Accessor("pickupDelay")
    void carpetFga$setPickupDelay(int pickupDelay);
}
//#endif
