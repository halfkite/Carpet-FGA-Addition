//#if MC >= 1.21.1
package carpet.fga.mixin;

import carpet.fga.VillagerTradeOnlyManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Brain.class, priority = 1101)
public abstract class BrainVillagerPerformanceMixin {
    @WrapOperation(method = "startEachNonRunningBehavior", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;tryStart(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;J)Z"))
    private boolean carpetFga$filterBehaviorStart(BehaviorControl<LivingEntity> behavior,
                                                  ServerLevel level, LivingEntity entity, long time,
                                                  Operation<Boolean> original) {
        return VillagerTradeOnlyManager.allowTryStart(behavior, entity)
                && original.call(behavior, level, entity, time);
    }

    @WrapOperation(method = "tickEachRunningBehavior", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;tickOrStop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;J)V"))
    private void carpetFga$filterBehaviorTick(BehaviorControl<LivingEntity> behavior,
                                               ServerLevel level, LivingEntity entity, long time,
                                               Operation<Void> original) {
        if (VillagerTradeOnlyManager.allowTick(behavior, entity)) original.call(behavior, level, entity, time);
        else behavior.doStop(level, entity, time);
    }
}
//#endif
