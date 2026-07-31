//#if MC >= 1.16.5 && MC < 26.0
package carpet.fga;

import net.minecraft.world.entity.npc.WanderingTrader;
//#if MC >= 1.21.1
import net.minecraft.core.registries.BuiltInRegistries;
//#endif

public final class WanderingTraderNoDespawn {
    private WanderingTraderNoDespawn() {}

    public static boolean preventsDespawn(WanderingTrader trader) {
        String mode = FGASettings.wanderingTraderNoDespawn;
        if ("true".equals(mode)) return true;
        //#if MC < 1.21.1
        //$$ return false;
        //#else
        if (!"controlled".equals(mode)) return false;
        if (trader.hasCustomName() && VillagerPerformanceConfig.wanderingTraderNames().contains(trader.getCustomName().getString())) return true;
        return VillagerPerformanceConfig.wanderingTraderBlocks().contains(
                BuiltInRegistries.BLOCK.getKey(trader.level().getBlockState(trader.blockPosition().below()).getBlock()));
        //#endif
    }
}
//#endif
