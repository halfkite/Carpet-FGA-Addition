package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.FGACompat;
//#if MC <= 26.2
import carpet.fga.DeathDropPreStackManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
//#endif
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    //#if MC <= 26.2
    @Shadow
    protected abstract void dropAllDeathLoot(
            //#if MC >= 1.21
            ServerLevel serverLevel,
            //#endif
            DamageSource damageSource);

    @Redirect(
            method = "die",
            at = @At(
                    value = "INVOKE",
                    //#if MC >= 1.21
                    target = "Lnet/minecraft/world/entity/LivingEntity;dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V"
                    //#else
                    //$$ target = "Lnet/minecraft/world/entity/LivingEntity;dropAllDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;)V"
                    //#endif
            )
    )
    private void carpetFga$preStackSelectedMobDrops(LivingEntity entity,
                                                     //#if MC >= 1.21
                                                     ServerLevel level,
                                                     //#endif
                                                     DamageSource damageSource) {
        //#if MC < 1.21
        //$$ ServerLevel level = (ServerLevel) FGACompat.level(entity);
        //#endif
        if (entity instanceof Player || !FGASettings.shouldPreStackDeathDrops(entity)) {
            this.dropAllDeathLoot(
                    //#if MC >= 1.21
                    level,
                    //#endif
                    damageSource);
            return;
        }

        DeathDropPreStackManager.begin(entity, level);
        boolean completedNormally = false;
        try {
            this.dropAllDeathLoot(
                    //#if MC >= 1.21
                    level,
                    //#endif
                    damageSource);
            completedNormally = true;
        } finally {
            DeathDropPreStackManager.finish(entity, completedNormally);
        }
    }
    //#endif

    @ModifyArg(
        method =
            //#if MC >= 1.21.10
            //$$ "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;ZLnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V",
            //#elseif MC >= 1.21.2
            //$$ "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V",
            //#else
            "dropFromLootTable(Lnet/minecraft/world/damagesource/DamageSource;Z)V",
            //#endif
        at = @At(
            value = "INVOKE",
            //#if MC >= 1.20
            target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V"
            //#else
            //$$ target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V"
            //#endif
        ),
        //#if MC >= 1.20
        index = 2
        //#else
        //$$ index = 1
        //#endif
    )
    private Consumer<ItemStack> carpetFga$filterZombifiedPiglinLoot(Consumer<ItemStack> consumer) {
        if ((Object) this instanceof ZombifiedPiglin
                && FGASettings.blocksZombifiedPiglinRottenFlesh()) {
            return stack -> {
                if (!FGACompat.isItem(stack, Items.ROTTEN_FLESH)) {
                    consumer.accept(stack);
                }
            };
        }
        return consumer;
    }
}
