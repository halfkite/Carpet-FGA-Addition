//#if MC >= 1.20.6 && MC <= 26.3
package carpet.fga.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Creative slot updates are validated against the item level stack limit and
 * silently ignored when the client sends more than that. A scoped limit raises
 * the real capacity of the receiving slot, so the client keeps stacks the
 * server never accepted and they are gone after the next resync. Validate
 * against the slot that will receive the stack instead.
 *
 * Earlier versions do not run that item level check at all, so no update is lost there.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplStackLimitMixin {
    @ModifyExpressionValue(
            method = "handleSetCreativeModeSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I")
    )
    private int carpetFga$creativeSlotCapacity(int original, ServerboundSetCreativeModeSlotPacket packet) {
        ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player;
        int slotIndex = packet.slotNum();
        if (player == null || slotIndex < 1 || slotIndex >= player.inventoryMenu.slots.size()) {
            return original;
        }
        Slot slot = player.inventoryMenu.getSlot(slotIndex);
        return Math.max(original, slot.getMaxStackSize(packet.itemStack()));
    }
}
//#endif
