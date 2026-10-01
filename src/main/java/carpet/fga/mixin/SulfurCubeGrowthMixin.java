//#if MC >= 26.2 && MC <= 26.3
//$$ package carpet.fga.mixin;
//$$
//$$ import carpet.fga.FGASettings;
//$$ import carpet.fga.SulfurCubeGrowthTime;
//$$ import net.minecraft.world.entity.AgeableMob;
//$$ import net.minecraft.world.entity.monster.cubemob.SulfurCube;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//$$
//$$ @Mixin(AgeableMob.class)
//$$ abstract class SulfurCubeGrowthMixin {
//$$     @Inject(method = "getBabyStartAge()I", at = @At("RETURN"), cancellable = true)
//$$     private void carpetFga$configureSulfurCubeGrowthTime(CallbackInfoReturnable<Integer> callback) {
//$$         if (FGASettings.sulfurCubeGrowthTime == -1 || !((Object) this instanceof SulfurCube)) return;
//$$         callback.setReturnValue(SulfurCubeGrowthTime.resolveBabyStartAge(
//$$                 FGASettings.sulfurCubeGrowthTime, callback.getReturnValue()));
//$$     }
//$$ }
//#endif
