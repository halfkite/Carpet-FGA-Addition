//#if MC >= 1.16.5 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FullShulkerBoxCraftingManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerFullShulkerReloadMixin {
    @Inject(method = "reloadResources(Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"))
    private void carpetFga$refreshFullShulkerRecipes(Collection<String> packs,
                                                      CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        cir.getReturnValue().thenRun(() -> server.execute(
                () -> FullShulkerBoxCraftingManager.refresh(server)));
    }
}
//#endif
