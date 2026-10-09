package carpet.fga.mixin;

import carpet.fga.MobBackpackAccess;
import carpet.fga.MobBackpackData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
//#if MC >= 1.21.6
//$$ import net.minecraft.world.level.storage.ValueInput;
//$$ import net.minecraft.world.level.storage.ValueOutput;
//#else
import net.minecraft.nbt.CompoundTag;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobBackpackMixin implements MobBackpackAccess {
    @Unique private final MobBackpackData carpetFga$backpackData = new MobBackpackData();

    @Override
    public MobBackpackData carpetFga$getBackpackData() {
        return carpetFga$backpackData;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
//#if MC >= 1.21.6
//$$     private void carpetFga$saveBackpack(ValueOutput output, CallbackInfo ci) {
//$$         carpetFga$backpackData.save(output);
//$$     }
//#else
    private void carpetFga$saveBackpack(CompoundTag output, CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        carpetFga$backpackData.save(output, mob.level().registryAccess());
    }
//#endif

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
//#if MC >= 1.21.6
//$$     private void carpetFga$loadBackpack(ValueInput input, CallbackInfo ci) {
//$$         carpetFga$backpackData.load(input);
//$$     }
//#else
    private void carpetFga$loadBackpack(CompoundTag input, CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        carpetFga$backpackData.load(input, mob.level().registryAccess());
    }
//#endif

    @Inject(method = "dropCustomDeathLoot", at = @At("TAIL"))
    private void carpetFga$dropBackpackContents(
            ServerLevel level, DamageSource source, boolean recentlyHit, CallbackInfo ci) {
        carpetFga$backpackData.dropContents((Mob) (Object) this, level);
    }
}
