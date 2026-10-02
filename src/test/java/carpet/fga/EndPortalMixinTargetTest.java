package carpet.fga;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
//#if MC >= 1.21.5
//$$ import net.minecraft.world.entity.InsideBlockEffectApplier;
//#endif
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class EndPortalMixinTargetTest {
    @Test
    void portalAndGatewayHaveTheExpectedInjectionSignature() throws ReflectiveOperationException {
        // Upgrade regression: both required injections must match the game's actual method.
        for (Class<?> block : new Class<?>[]{EndPortalBlock.class, EndGatewayBlock.class}) {
            Class<?>[] arguments = {
                    BlockState.class, Level.class, BlockPos.class, Entity.class
                    //#if MC >= 1.21.5
                    //$$ , InsideBlockEffectApplier.class
                    //#endif
                    //#if MC >= 1.21.10
                    //$$ , boolean.class
                    //#endif
            };
            assertEquals(void.class, block.getDeclaredMethod("entityInside", arguments).getReturnType());
        }
    }
}
