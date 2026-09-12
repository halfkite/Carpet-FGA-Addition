package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.2
import carpet.fga.PlayerPossessionManager;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents a real target's connection from moving or interacting with its watched body. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PossessionInputMixin {
    private boolean fga$blocked() {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        return PlayerPossessionManager.isWatchedTarget(listener.player);
    }

    private boolean fga$blockedAndCorrectPosition() {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        if (!fga$blocked()) return false;
        listener.teleport(listener.player.getX(), listener.player.getY(), listener.player.getZ(),
                listener.player.getYRot(), listener.player.getXRot());
        return true;
    }

    @Inject(method = "handlePlayerInput", at = @At("HEAD"), cancellable = true)
    private void fga$blockPlayerInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        if (fga$blocked()) ci.cancel();
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void fga$blockMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (fga$blockedAndCorrectPosition()) ci.cancel();
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void fga$blockVehicleMove(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (fga$blockedAndCorrectPosition()) ci.cancel();
    }

    @Inject(method = "handlePaddleBoat", at = @At("HEAD"), cancellable = true)
    private void fga$blockPaddle(ServerboundPaddleBoatPacket packet, CallbackInfo ci) {
        if (fga$blocked()) ci.cancel();
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void fga$blockAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (fga$blocked()) ci.cancel();
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void fga$blockUseOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (fga$blocked()) ci.cancel();
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void fga$blockUse(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (fga$blocked()) ci.cancel();
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void fga$blockInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (fga$blocked()) ci.cancel();
    }

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void fga$blockHotbar(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (fga$blocked()) ci.cancel();
    }

    @Inject(method = "handlePlayerCommand", at = @At("HEAD"), cancellable = true)
    private void fga$blockPlayerCommand(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
        if (fga$blocked()) ci.cancel();
    }

    @Inject(method = "handleAnimate", at = @At("HEAD"), cancellable = true)
    private void fga$blockAnimate(ServerboundSwingPacket packet, CallbackInfo ci) {
        if (fga$blocked()) ci.cancel();
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void fga$blockContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (fga$blocked()) ci.cancel();
    }

    @Inject(method = "handleContainerButtonClick", at = @At("HEAD"), cancellable = true)
    private void fga$blockContainerButton(ServerboundContainerButtonClickPacket packet, CallbackInfo ci) {
        if (fga$blocked()) ci.cancel();
    }

    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void fga$blockRecipe(ServerboundPlaceRecipePacket packet, CallbackInfo ci) {
        if (fga$blocked()) ci.cancel();
    }

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void fga$blockCreativeSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        if (fga$blocked()) ci.cancel();
    }
}
//#endif
