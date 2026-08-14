package carpet.fga;

import net.minecraft.world.entity.Entity;

public final class BabyMobNoGrowth {
    private BabyMobNoGrowth() {}

    public static boolean isLocked(Entity entity) {
        String mode = FGASettings.babyMobNoGrowth;
        if ("false".equals(mode)) return false;
        if ("true".equals(mode)) return true;
        return entity.hasCustomName()
                && entity.getCustomName() != null
                && mode.equals(entity.getCustomName().getString());
    }
}
