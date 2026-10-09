//#if MC >= 1.20.1 && MC <= 26.3
package carpet.fga;

import carpet.CarpetServer;
import carpet.api.settings.CarpetRule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/** Reads the current Carpet rule independently of AMS internal settings classes. */
final class AmsLargeShulkerBoxCompat {
    private static final String MOD_ID = "carpet-ams-addition";

    private AmsLargeShulkerBoxCompat() {
    }

    static boolean isEnabled() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return false;
        var settingsManager = CarpetServer.settingsManager;
        if (settingsManager == null) return false;
        CarpetRule<?> rule = settingsManager.getCarpetRule("largeShulkerBox");
        return rule != null && Boolean.TRUE.equals(rule.value());
    }

    static int actualCapacity() {
        return new ShulkerBoxBlockEntity(BlockPos.ZERO, Blocks.SHULKER_BOX.defaultBlockState())
                .getContainerSize();
    }
}
//#endif
