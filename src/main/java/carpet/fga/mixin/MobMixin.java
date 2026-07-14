package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Mob.class)
public abstract class MobMixin {
    @ModifyArg(
        method = "dropCustomDeathLoot",
        at = @At(
            value = "INVOKE",
            target =
                //#if MC >= 1.21.8
                //$$ "Lnet/minecraft/world/entity/Mob;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"
                //#else
                "Lnet/minecraft/world/entity/Mob;spawnAtLocation(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"
                //#endif
        ),
        index =
            //#if MC >= 1.21.8
            //$$ 1
            //#else
            0
            //#endif
    )
    private ItemStack carpetFga$filterZombifiedPiglinGoldEquipment(ItemStack stack) {
        if (!((Object) this instanceof ZombifiedPiglin)
                || !FGASettings.blocksZombifiedPiglinGoldEquipment()) {
            return stack;
        }

        if (stack.is(Items.GOLDEN_HELMET)
                || stack.is(Items.GOLDEN_CHESTPLATE)
                || stack.is(Items.GOLDEN_LEGGINGS)
                || stack.is(Items.GOLDEN_BOOTS)
                || stack.is(Items.GOLDEN_SWORD)
                || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("golden_spear")) {
            return ItemStack.EMPTY;
        }
        return stack;
    }
}
