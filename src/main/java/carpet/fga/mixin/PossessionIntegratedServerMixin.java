//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import carpet.fga.PlayerPossessionManager;
import carpet.fga.PossessionPermissionContext;
import net.minecraft.client.server.IntegratedServer;
//#if MC >= 1.21.11
//$$ import net.minecraft.server.permissions.LevelBasedPermissionSet;
//#endif
//#if MC >= 1.21.10
//$$ import net.minecraft.server.players.NameAndId;
//#else
import com.mojang.authlib.GameProfile;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** IntegratedServer overrides the base permission method and recognizes its host by name. */
@Mixin(IntegratedServer.class)
public abstract class PossessionIntegratedServerMixin {
    @Inject(method = "isSingleplayerOwner", at = @At("HEAD"), cancellable = true)
    private void fga$originalOwner(
            //#if MC >= 1.21.10
            //$$ NameAndId profile,
            //#else
            GameProfile profile,
            //#endif
            CallbackInfoReturnable<Boolean> callback) {
        if (PossessionPermissionContext.useOriginalPermission()) return;
        var server = (IntegratedServer) (Object) this;
        var original = PlayerPossessionManager.originalProfile(
                //#if MC >= 1.21.10
                //$$ profile.id(), server);
                //#else
                profile.getId(), server);
                //#endif
        if (original == null) return;
        callback.setReturnValue(PossessionPermissionContext.withOriginalPermission(
                //#if MC >= 1.21.10
                //$$ () -> server.isSingleplayerOwner(new NameAndId(original))));
                //#else
                () -> server.isSingleplayerOwner(original)));
                //#endif
    }

    // Older integrated servers inherit this method; their base-class Mixin already applies.
    //#if MC == 26.3
    //$$ @Inject(method = "getProfilePermissions", at = @At("HEAD"), cancellable = true)
    //$$ private void fga$originalPermissions(
            //#if MC >= 1.21.10
            //$$ NameAndId profile,
            //#else
            //$$ GameProfile profile,
            //#endif
            //#if MC >= 1.21.11
            //$$ CallbackInfoReturnable<LevelBasedPermissionSet> callback) {
            //#else
            //$$ CallbackInfoReturnable<Integer> callback) {
            //#endif
        //#if MC >= 1.21.10
        //$$ var id = profile.id();
        //#else
        //$$ var id = profile.getId();
        //#endif
        //$$ if (PossessionPermissionContext.useOriginalPermission()
                //$$ || !PlayerPossessionManager.isParticipantId(id)) return;
        //$$ var server = (IntegratedServer) (Object) this;
        //$$ var permissionId = !FGASettings.permissionSwapsToo && PlayerPossessionManager.isControllerId(id)
                //$$ ? PlayerPossessionManager.swapPartner(id) : id;
        //$$ var original = PlayerPossessionManager.originalProfile(permissionId, server);
        //$$ if (original == null) return;
        // Run vanilla's integrated-server policy on the selected original identity.
        // In particular, do not turn a cheats-disabled host into an operator.
        //$$ callback.setReturnValue(PossessionPermissionContext.withOriginalPermission(
                //#if MC >= 1.21.10
                //$$ () -> server.getProfilePermissions(new NameAndId(original))));
                //#else
                //$$ () -> server.getProfilePermissions(original)));
                //#endif
    //$$ }
    //#endif
}
//#endif
