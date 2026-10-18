//#if MC >= 1.21.1 && MC < 1.21.4
package carpet.fga.mixin;

import carpet.fga.FakePlayerProfilePreloadManager;
import carpet.patches.EntityPlayerMPFake;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** These Carpet releases have no pending-login set; observe the real spawn future instead. */
@Mixin(value = EntityPlayerMPFake.class, remap = false)
public abstract class LegacyFakePlayerSpawnCompletionMixin {
    @WrapOperation(method = "createFake", at = @At(value = "INVOKE", target =
            "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), remap = false)
    private static CompletableFuture<Void> fga$trackSpawn(
            CompletableFuture<Optional<GameProfile>> profile, Consumer<Optional<GameProfile>> action,
            Executor executor, Operation<CompletableFuture<Void>> original,
            String name, MinecraftServer server, Vec3 position, double yaw, double pitch,
            ResourceKey<Level> dimension, GameType gameMode, boolean flying) {
        CompletableFuture<Void> spawn = original.call(profile, action, executor);
        FakePlayerProfilePreloadManager.trackLegacySpawn(server, name, spawn);
        return spawn;
    }
}
//#endif
