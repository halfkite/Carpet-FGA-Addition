//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Capacity is snapshotted after Carpet loads the world rules, never changed live. */
public final class BarrelCapacityManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition");
    private static volatile boolean active;
    private static boolean warnedOverflow;

    private BarrelCapacityManager() { }

    public static void load() {
        active = FGASettings.doubleBarrelCapacity;
        warnedOverflow = false;
        if (!active || !FabricLoader.getInstance().isModLoaded("carpet-tis-addition")) return;
        try {
            if (Class.forName("carpettisaddition.CarpetTISAdditionSettings").getField("largeBarrel").getBoolean(null)) {
                active = false;
                LOGGER.warn(FGAText.raw("carpet-fga-addition.features.barrel.tis"));
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            active = false;
            LOGGER.warn("Cannot verify TIS largeBarrel; FGA barrel expansion is disabled for this session", exception);
        }
    }

    public static boolean active() { return active; }

    public static synchronized void warnOverflow() {
        if (active || warnedOverflow) return;
        warnedOverflow = true;
        LOGGER.warn(FGAText.raw("carpet-fga-addition.features.barrel.overflow"));
    }

    public static void clear() { active = false; warnedOverflow = false; }
}
//#endif
