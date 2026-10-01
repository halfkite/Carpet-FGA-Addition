//#if MC == 26.3
//$$ package carpet.fga.mixin;
//$$ import carpet.fga.PlayerSortOfflineWithdrawal;
//$$ import carpet.patches.EntityPlayerMPFake;
//$$ import net.minecraft.resources.ResourceKey;
//$$ import net.minecraft.server.MinecraftServer;
//$$ import net.minecraft.world.level.GameType;
//$$ import net.minecraft.world.level.Level;
//$$ import net.minecraft.world.phys.Vec3;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//$$
//$$ @Mixin(EntityPlayerMPFake.class)
//$$ public abstract class SilentInventorySpawnGuardMixin {
//$$     @Inject(method="createFake",at=@At("HEAD"),cancellable=true,remap=false)
//$$     private static void carpetFga$guardSilentWithdrawal(String name,MinecraftServer server,Vec3 pos,double yaw,double pitch,
//$$                                                        ResourceKey<Level> dimension,GameType mode,boolean flying,
//$$                                                        CallbackInfoReturnable<Boolean> cir) {
//$$         if (PlayerSortOfflineWithdrawal.isLocked(name)) cir.setReturnValue(false);
//$$     }
//$$ }
//#endif
