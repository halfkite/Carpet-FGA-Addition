package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.2
import carpet.fga.EntityDropRemovalConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Consumer;

@Mixin(LivingEntity.class)
public abstract class EntityDropRemovalLootMixin {
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
                    target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V"
            ),
            index = 2
    )
    private Consumer<ItemStack> carpetFga$filterEntityLoot(Consumer<ItemStack> consumer) {
        LivingEntity entity = (LivingEntity) (Object) this;
        return stack -> {
            if (!EntityDropRemovalConfig.shouldRemoveLoot(entity, stack)) consumer.accept(stack);
        };
    }
}
//#endif
