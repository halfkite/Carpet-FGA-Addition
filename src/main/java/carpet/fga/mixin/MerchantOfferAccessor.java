//#if MC >= 1.21 && MC <= 26.2
package carpet.fga.mixin;

import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantOffer.class)
public interface MerchantOfferAccessor {
    @Accessor("baseCostA")
    @Mutable
    void carpetFga$setBaseCostA(ItemCost value);

    @Accessor("costB")
    @Mutable
    void carpetFga$setCostB(java.util.Optional<ItemCost> value);

    @Accessor("result")
    @Mutable
    void carpetFga$setResult(ItemStack value);
}
//#endif
