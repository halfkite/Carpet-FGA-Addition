package carpet.fga.mixin;

//#if MC == 1.21.1
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
                    target = "Lnet/minecraft/world/entity/Mob;spawnAtLocation(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"
            ),
            index = 0
    )
    private ItemStack carpetFga$filterEquipmentDrop(ItemStack stack) {
        Mob mob = (Mob) (Object) this;
        return EntityDropRemovalConfig.shouldRemoveEquipment(mob, stack) ? ItemStack.EMPTY : stack;
    }
}
//#endif
