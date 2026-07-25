package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.FGACompat;
import carpet.fga.MobEquipmentContainer;
import carpet.fga.MobEquipmentMenu;
import carpet.fga.VillagerBreedingAnimalization;
//#if MC >= 1.19.3
import net.minecraft.core.registries.BuiltInRegistries;
//#else
//$$ import net.minecraft.core.Registry;
//#endif
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//#if MC >= 26.1.2
//$$ import net.minecraft.world.phys.Vec3;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void carpetFga$handleShiftInteraction(Player player, InteractionHand hand,
                                                   //#if MC >= 26.1.2
                                                   //$$ Vec3 interactionLocation,
                                                   //#endif
                                                   CallbackInfoReturnable<InteractionResult> cir) {
        Mob mob = (Mob) (Object) this;
        if (!player.isShiftKeyDown() || !mob.isAlive()) {
            return;
        }

        if (mob instanceof Villager villager
                && VillagerBreedingAnimalization.tryFeed(player, villager, hand)) {
            cir.setReturnValue(carpetFga$sidedSuccess(player));
            return;
        }

        if (FGASettings.hostileMobInventoryAccess
                && mob instanceof Enemy
                && !player.isSpectator()
                && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().isEmpty()) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new MobEquipmentMenu(
                                containerId, inventory, new MobEquipmentContainer(mob)),
                        mob.getDisplayName()));
            }
            cir.setReturnValue(carpetFga$sidedSuccess(player));
        }
    }

    private static InteractionResult carpetFga$sidedSuccess(Player player) {
        //#if MC >= 1.21.2
        //$$ return player.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        //#else
        return InteractionResult.sidedSuccess(FGACompat.level(player).isClientSide());
        //#endif
    }

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
    private ItemStack carpetFga$filterZombifiedPiglinGoldEquipment(ItemStack stack) {
        if (!((Object) this instanceof ZombifiedPiglin)
                || !FGASettings.blocksZombifiedPiglinGoldEquipment()) {
            return stack;
        }

        if (FGACompat.isItem(stack, Items.GOLDEN_HELMET)
                || FGACompat.isItem(stack, Items.GOLDEN_CHESTPLATE)
                || FGACompat.isItem(stack, Items.GOLDEN_LEGGINGS)
                || FGACompat.isItem(stack, Items.GOLDEN_BOOTS)
                || FGACompat.isItem(stack, Items.GOLDEN_SWORD)
                ||
                //#if MC >= 1.19.3
                BuiltInRegistries.ITEM.getKey(stack.getItem())
                //#else
                //$$ Registry.ITEM.getKey(stack.getItem())
                //#endif
                .getPath().equals("golden_spear")) {
            return ItemStack.EMPTY;
        }
        return stack;
    }
}
