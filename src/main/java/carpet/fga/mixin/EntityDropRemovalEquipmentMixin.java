package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.2
import carpet.fga.EntityDropRemovalConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Mob.class)
public abstract class EntityDropRemovalEquipmentMixin {
    @ModifyArg(
            method = "dropCustomDeathLoot",
            at = @At(
                    value = "INVOKE",
                    target =
                            //#if MC >= 1.21.2
                            //$$ "Lnet/minecraft/world/entity/Mob;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"
                            //#else
                            "Lnet/minecraft/world/entity/Mob;spawnAtLocation(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"
                            //#endif
            ),
            index =
                    //#if MC >= 1.21.2
                    //$$ 1
                    //#else
                    0
                    //#endif
    )
    private ItemStack carpetFga$filterEquipmentDrop(ItemStack stack) {
        Mob mob = (Mob) (Object) this;
        return EntityDropRemovalConfig.shouldRemoveEquipment(mob, stack) ? ItemStack.EMPTY : stack;
    }
}
//#endif
