package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Consumer;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyArg(
        method =
            //#if MC >= 26.0
            //$$ "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;ZLnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V",
            //#elseif MC >= 1.21.2
            //$$ "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V",
            //#else
            "dropFromLootTable(Lnet/minecraft/world/damagesource/DamageSource;Z)V",
            //#endif
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V"
        ),
        index = 2
    )
    private Consumer<ItemStack> carpetFga$filterZombifiedPiglinLoot(Consumer<ItemStack> consumer) {
        if ((Object) this instanceof ZombifiedPiglin
                && FGASettings.blocksZombifiedPiglinRottenFlesh()) {
            return stack -> {
                if (!stack.is(Items.ROTTEN_FLESH)) {
                    consumer.accept(stack);
                }
            };
        }
        return consumer;
    }
}
