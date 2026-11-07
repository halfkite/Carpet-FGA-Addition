package carpet.fga.mixin;

//#if MC >= 1.21.5
//$$ import net.minecraft.world.entity.EntityReference;
//#endif
import carpet.fga.PossessionOwners;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class CombatBodyCreditMixin {
    @ModifyVariable(method = "setLastHurtByMob", at = @At("HEAD"), argsOnly = true)
    private LivingEntity fga$rememberAttacker(LivingEntity attacker) {
        return (LivingEntity) PossessionOwners.counterpart(attacker);
    }

    //#if MC >= 1.21.5
    //$$ @ModifyVariable(method = "setLastHurtByPlayer(Lnet/minecraft/world/entity/player/Player;I)V", at = @At("HEAD"), argsOnly = true)
    //$$ private Player fga$rememberPlayer(Player attacker) {
    //$$     return (Player) PossessionOwners.counterpart(attacker);
    //$$ }
    //$$
    //$$ @ModifyExpressionValue(method = {"getLastHurtByPlayer", "getKillCredit", "resolvePlayerResponsibleForDamage"},
    //$$         at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;lastHurtByPlayer:Lnet/minecraft/world/entity/EntityReference;", opcode = Opcodes.GETFIELD))
    //$$ private EntityReference<Player> fga$playerCredit(EntityReference<Player> stored) {
    //$$     return PossessionOwners.bodyReference(stored);
    //$$ }
    //$$
    //$$ @ModifyExpressionValue(method = {"getLastHurtByMob", "getKillCredit"},
    //$$         at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;lastHurtByMob:Lnet/minecraft/world/entity/EntityReference;"))
    //$$ private EntityReference<LivingEntity> fga$mobCredit(EntityReference<LivingEntity> stored) {
    //$$     return PossessionOwners.bodyReference(stored);
    //$$ }
    //#else
    //#if MC == 1.21.4
    //$$ @WrapOperation(method = {"setLastHurtByPlayer", "resolvePlayerResponsibleForDamage"},
    //#elseif MC == 1.21.3
    //$$ @WrapOperation(method = {"setLastHurtByPlayer", "hurtServer"},
    //#else
    @WrapOperation(method = {"setLastHurtByPlayer", "hurt"},
    //#endif
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;lastHurtByPlayer:Lnet/minecraft/world/entity/player/Player;", opcode = Opcodes.PUTFIELD))
    private void fga$rememberPlayer(LivingEntity victim, Player attacker, Operation<Void> write) {
        write.call(victim, (Player) PossessionOwners.counterpart(attacker));
    }

    //#if MC == 1.21.4
    //$$ @ModifyExpressionValue(method = {"dropFromLootTable", "getKillCredit", "resolvePlayerResponsibleForDamage"},
    //#else
    @ModifyExpressionValue(method = {"dropFromLootTable", "getKillCredit"},
    //#endif
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;lastHurtByPlayer:Lnet/minecraft/world/entity/player/Player;", opcode = Opcodes.GETFIELD))
    private Player fga$playerCredit(Player stored) {
        return (Player) PossessionOwners.counterpart(stored);
    }

    @ModifyExpressionValue(method = {"baseTick", "getLastHurtByMob", "getKillCredit"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;lastHurtByMob:Lnet/minecraft/world/entity/LivingEntity;"))
    private LivingEntity fga$mobCredit(LivingEntity stored) {
        return (LivingEntity) PossessionOwners.counterpart(stored);
    }
    //#endif
}
