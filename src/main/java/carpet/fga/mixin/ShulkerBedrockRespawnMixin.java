//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.ShulkerBullet;
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

    @Unique
    private Vec3 carpetFga$preHurtPosition;

    @Inject(method = "hurt", at = @At("HEAD"))
    private void carpetFga$capturePreHurtPosition(DamageSource damageSource, float amount,
                                                  CallbackInfoReturnable<Boolean> callback) {
        if (FGASettings.shulkerBedrockDuplication) {
            this.carpetFga$preHurtPosition = ((Shulker) (Object) this).position();
        }
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void carpetFga$respawnOnShulkerBulletKill(DamageSource damageSource, float amount,
                                                      CallbackInfoReturnable<Boolean> callback) {
        if (!FGASettings.shulkerBedrockDuplication || !callback.getReturnValueZ()) {
            return;
        }
        Shulker dying = (Shulker) (Object) this;
        if (dying.getHealth() > 0.0F || !(dying.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(damageSource.getDirectEntity() instanceof ShulkerBullet)) {
            return;
        }
        // Vanilla may teleport a dying shulker inside hurt(); respawn at the pre-hurt position like Bedrock does.
        Vec3 respawnPos = this.carpetFga$preHurtPosition != null ? this.carpetFga$preHurtPosition : dying.position();
        Shulker respawned = EntityType.SHULKER.create(serverLevel);
        if (respawned == null) {
            return;
        }
        respawned.setVariant(dying.getVariant());
        // Copy the attach face so the respawned shulker passes canStayAt on its first tick and stays in place.
        respawned.getEntityData().set(DATA_ATTACH_FACE_ID, dying.getAttachFace());
        respawned.moveTo(respawnPos);
        serverLevel.addFreshEntity(respawned);
    }
}
//#endif
