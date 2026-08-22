//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.FGACompat;
import net.minecraft.core.Direction;
//#if MC >= 26.2
//$$ import net.minecraft.core.registries.BuiltInRegistries;
//#endif
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.ShulkerBullet;
//#if MC >= 1.21.2
//$$ import net.minecraft.world.entity.EntitySpawnReason;
//#endif
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Shulker.class)
public abstract class ShulkerBedrockRespawnMixin {
    @Shadow
    @Final
    protected static EntityDataAccessor<Direction> DATA_ATTACH_FACE_ID;

    @Shadow
    @Final
    protected static EntityDataAccessor<Byte> DATA_COLOR_ID;

    @Unique
    private Vec3 carpetFga$preHurtPosition;

    @Inject(method =
            //#if MC >= 1.21.3
            //$$ "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            //#else
            "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            //#endif
            , at = @At("HEAD"))
    private void carpetFga$capturePreHurtPosition(
                                                  //#if MC >= 1.21.3
                                                  //$$ ServerLevel injectedServerLevel,
                                                  //#endif
                                                  DamageSource damageSource, float amount,
                                                  CallbackInfoReturnable<Boolean> callback) {
        if (FGASettings.shulkerBedrockDuplication) {
            this.carpetFga$preHurtPosition = ((Shulker) (Object) this).position();
        }
    }

    @Inject(method =
            //#if MC >= 1.21.3
            //$$ "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            //#else
            "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            //#endif
            , at = @At("RETURN"))
    private void carpetFga$respawnOnShulkerBulletKill(
                                                      //#if MC >= 1.21.3
                                                      //$$ ServerLevel injectedServerLevel,
                                                      //#endif
                                                      DamageSource damageSource, float amount,
                                                      CallbackInfoReturnable<Boolean> callback) {
        if (!FGASettings.shulkerBedrockDuplication || !callback.getReturnValueZ()) {
            return;
        }
        Shulker dying = (Shulker) (Object) this;
        if (dying.getHealth() > 0.0F || !(dying.level() instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) dying.level();
        if (!(damageSource.getDirectEntity() instanceof ShulkerBullet)) {
            return;
        }
        // Vanilla may teleport a dying shulker inside hurt(); respawn at the pre-hurt position like Bedrock does.
        Vec3 respawnPos = this.carpetFga$preHurtPosition != null ? this.carpetFga$preHurtPosition : dying.position();
        Shulker respawned =
                //#if MC >= 1.21.2
                //#if MC >= 26.2
                //$$ (Shulker) BuiltInRegistries.ENTITY_TYPE.getValue(FGACompat.vanillaId("shulker"))
                //$$         .create(serverLevel, EntitySpawnReason.COMMAND);
                //#else
                //$$ EntityType.SHULKER.create(serverLevel, EntitySpawnReason.COMMAND);
                //#endif
                //#else
                EntityType.SHULKER.create(serverLevel);
                //#endif
        if (respawned == null) {
            return;
        }
        respawned.getEntityData().set(DATA_COLOR_ID, dying.getEntityData().get(DATA_COLOR_ID));
        // Copy the attach face so the respawned shulker passes canStayAt on its first tick and stays in place.
        respawned.getEntityData().set(DATA_ATTACH_FACE_ID, dying.getAttachFace());
        respawned.moveTo(respawnPos);
        serverLevel.addFreshEntity(respawned);
    }
}
//#endif
