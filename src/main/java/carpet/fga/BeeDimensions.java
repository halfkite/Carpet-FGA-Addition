package carpet.fga;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
//#if MC >= 26.2
//$$ import net.minecraft.world.entity.EntityTypes;
//#endif

public final class BeeDimensions {
    private static final EntityDimensions PRE_26_2_DIMENSIONS =
            //#if MC >= 1.21.2
            EntityDimensions.scalable(0.7F, 0.6F).withEyeHeight(0.3F);
            //#else
            //$$ EntityDimensions.scalable(0.7F, 0.6F);
            //#endif

    private BeeDimensions() {
    }

    public static EntityDimensions pre26Dimensions() {
        return PRE_26_2_DIMENSIONS;
    }

    public static boolean isBee(EntityType<?> type) {
        //#if MC >= 26.2
        //$$ return type == EntityTypes.BEE;
        //#else
        return type == EntityType.BEE;
        //#endif
    }

    public static void refreshLoadedBees(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (isBee(entity.getType())) {
                    entity.refreshDimensions();
                }
            }
        }
    }
}
