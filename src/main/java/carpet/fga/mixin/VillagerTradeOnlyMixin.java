//#if MC >= 1.20.1
package carpet.fga.mixin;

import carpet.fga.VillagerTradeOnlyManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerTradeOnlyMixin {
    @Redirect(
            method = "customServerAiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"
            )
    )
    private void carpetFga$disableBrainTick(Brain<LivingEntity> brain, ServerLevel level, LivingEntity entity) {
        VillagerTradeOnlyManager.tickBrain(brain, level, (Villager) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void carpetFga$tickTradeOnlyState(CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        VillagerTradeOnlyManager.tickRestock(villager);
        VillagerTradeOnlyManager.applyStatic(villager);
    }
}
//#endif
