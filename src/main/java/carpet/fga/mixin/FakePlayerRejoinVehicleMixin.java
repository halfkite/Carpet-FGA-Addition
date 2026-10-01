//#if MC >= 26.1 && MC <= 26.3
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.FakePlayerRejoinCommand;
//$$ import carpet.patches.EntityPlayerMPFake;
//$$ import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
//$$ import com.mojang.authlib.GameProfile;
//$$ import net.minecraft.resources.ResourceKey;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.server.level.ServerLevel;
//$$ import net.minecraft.world.level.GameType;
//$$ import net.minecraft.world.level.Level;
//$$ import net.minecraft.world.phys.Vec3;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ @Mixin(value = EntityPlayerMPFake.class, remap = false)
//$$ public abstract class FakePlayerRejoinVehicleMixin {
//$$     @WrapWithCondition(method = "lambda$createFake$0",
//$$             at = @At(value = "INVOKE", target = "Lcarpet/patches/EntityPlayerMPFake;stopRiding()V"),
//$$             remap = false, require = 1)
//$$     private static boolean fga$keepRejoinedVehicle(EntityPlayerMPFake player) {
//$$         return !FakePlayerRejoinCommand.isTisRejoin()
//$$                 && !FakePlayerRejoinCommand.isPending(player.getScoreboardName());
//$$     }
//$$
//$$     @Inject(method = "lambda$createFake$0", at = @At("RETURN"), remap = false, require = 1)
//$$     private static void fga$finishRejoin(String name, GameProfile originalProfile, MinecraftServer server,
//$$             ServerLevel startingLevel, Vec3 startingPosition, double yaw, double pitch, GameType gameType,
//$$             ResourceKey<Level> dimension, boolean flying, GameProfile resolvedProfile, Throwable error,
//$$             CallbackInfo ci) {
//$$         FakePlayerRejoinCommand.finish(server, name, error);
//$$     }
//$$ }
//#endif
