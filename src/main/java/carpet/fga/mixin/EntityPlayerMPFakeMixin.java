//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga.mixin;

//#if MC == 1.20.1 || MC >= 1.21.1
import carpet.fga.FakePlayerProfilePreloadManager;
//#endif
import carpet.patches.EntityPlayerMPFake;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityPlayerMPFake.class, remap = false)
public abstract class EntityPlayerMPFakeMixin {
    //#if MC == 1.20.1 || MC >= 1.21.1
    @Inject(method = "createFake", at = @At("HEAD"), cancellable = true, remap = false)
    private static void carpetFga$preloadDirectFakePlayerProfile(
            String name, MinecraftServer server, Vec3 position, double yaw, double pitch,
            ResourceKey<Level> dimension, GameType gameMode, boolean flying,
            CallbackInfoReturnable<
                    //#if MC >= 1.21.1
                    Boolean
                    //#else
                    //$$ EntityPlayerMPFake
                    //#endif
                    > cir) {
        if (FakePlayerProfilePreloadManager.interceptDirectSpawn(
                name, server, position, yaw, pitch, dimension, gameMode, flying)) {
            cir.setReturnValue(
                    //#if MC >= 1.21.1
                    true
                    //#else
                    //$$ null
                    //#endif
            );
        }
    }
    //#endif
}
//#endif
