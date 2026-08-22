//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.core.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
//#if MC >= 1.21.3
//$$ import net.minecraft.server.level.ServerLevel;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Shulker.class)
public abstract class ShulkerAttackArmorStandMixin extends Mob {
    protected ShulkerAttackArmorStandMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "registerGoals", at = @At("RETURN"))
    private void carpetFga$addArmorStandTargetGoal(CallbackInfo ci) {
        // Extends Mob so the inherited protected targetSelector is reachable; @Shadow cannot see superclass fields.
        this.targetSelector.addGoal(4, new CarpetFgaArmorStandTargetGoal((Shulker) (Object) this));
    }

    static final class CarpetFgaArmorStandTargetGoal extends NearestAttackableTargetGoal<ArmorStand> {
        CarpetFgaArmorStandTargetGoal(Shulker shulker) {
            super(shulker, ArmorStand.class, 10, true, false,
                    //#if MC >= 1.21.3
                    //$$ CarpetFgaArmorStandTargetGoal::carpetFga$isEligibleWithLevel);
                    //#else
                    CarpetFgaArmorStandTargetGoal::carpetFga$isEligible);
                    //#endif
        }

        private static boolean carpetFga$isEligible(LivingEntity target) {
            String rule = FGASettings.shulkerAttackArmorStand;
            if ("pumpkin".equals(rule)) {
                return target.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN);
            }
            return "true".equals(rule);
        }

        //#if MC >= 1.21.3
        //$$ private static boolean carpetFga$isEligibleWithLevel(LivingEntity target, ServerLevel ignored) {
        //$$     return carpetFga$isEligible(target);
        //$$ }
        //#endif

        @Override
        public boolean canUse() {
            if (this.mob.level().getDifficulty() == Difficulty.PEACEFUL) {
                return false;
            }
            return super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            // ArmorStand.canBeSeenByAnyone ignores isAlive, so vanilla's TargetGoal death check never
            // releases a dead armor stand target and this goal would lock the TARGET flag forever.
            LivingEntity target = this.mob.getTarget();
            if (target instanceof ArmorStand && (!target.isAlive() || !carpetFga$isEligible(target))) {
                return false;
            }
            return super.canContinueToUse();
        }

        @Override
        protected AABB getTargetSearchArea(double distance) {
            // Mirror the vanilla shulker search box: 4 blocks along the attach axis, follow distance elsewhere.
            Direction direction = ((Shulker) this.mob).getAttachFace();
            if (direction.getAxis() == Direction.Axis.X) {
                return this.mob.getBoundingBox().inflate(4.0, distance, distance);
            }
            return direction.getAxis() == Direction.Axis.Z
                    ? this.mob.getBoundingBox().inflate(distance, distance, 4.0)
                    : this.mob.getBoundingBox().inflate(distance, 4.0, distance);
        }
    }
}
//#endif
