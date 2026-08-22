//#if MC == 1.20.1 || MC == 1.21.1
package carpet.fga.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerLevel.class)
public interface ServerLevelEntityTickListAccessor {
    @Accessor("entityTickList")
    EntityTickList carpetFga$getEntityTickList();
}
//#endif
