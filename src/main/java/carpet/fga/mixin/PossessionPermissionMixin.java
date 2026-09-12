package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.2
import carpet.fga.FGASettings;
import carpet.fga.PlayerPossessionManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
//#if MC >= 1.21.10
//$$ import net.minecraft.server.players.NameAndId;
//#endif
//#if MC >= 1.21.11
//$$ import net.minecraft.server.permissions.LevelBasedPermissionSet;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** Applies the requested permission policy while the live player bodies are swapped. */
@Mixin(MinecraftServer.class)
public abstract class PossessionPermissionMixin {
    private static final ThreadLocal<Boolean> FGA_PERMISSION_REENTRY =
            ThreadLocal.withInitial(() -> false);

    @Shadow
    public abstract PlayerList getPlayerList();

    //#if MC >= 1.21.11
    //$$ @Inject(method = "getProfilePermissions", at = @At("RETURN"), cancellable = true)
    //$$ private void fga$permissionForPossession(NameAndId profile,
    //$$         CallbackInfoReturnable<LevelBasedPermissionSet> callback) {
    //$$     if (!FGASettings.permissionSwapsToo || FGA_PERMISSION_REENTRY.get()) return;
    //$$     UUID id = profile.id();
    //$$     if (id == null || !PlayerPossessionManager.isParticipantId(id)) return;
    //$$     UUID partnerId = PlayerPossessionManager.swapPartner(id);
    //$$     ServerPlayer partner = partnerId == null ? null : getPlayerList().getPlayer(partnerId);
    //$$     if (partner == null) return;
    //$$     FGA_PERMISSION_REENTRY.set(true);
    //$$     try {
    //$$         callback.setReturnValue(((MinecraftServer) (Object) this).getProfilePermissions(
    //$$                 new NameAndId(partner.getGameProfile())));
    //$$     } finally {
    //$$         FGA_PERMISSION_REENTRY.set(false);
    //$$     }
    //$$ }
    //#elseif MC >= 1.21.10
    //$$ @Inject(method = "getProfilePermissions", at = @At("RETURN"), cancellable = true)
    //$$ private void fga$permissionForPossession(NameAndId profile, CallbackInfoReturnable<Integer> callback) {
    //$$     if (!FGASettings.permissionSwapsToo || FGA_PERMISSION_REENTRY.get()) return;
    //$$     UUID id = profile.id();
    //$$     if (id == null || !PlayerPossessionManager.isParticipantId(id)) return;
    //$$     UUID partnerId = PlayerPossessionManager.swapPartner(id);
    //$$     ServerPlayer partner = partnerId == null ? null : getPlayerList().getPlayer(partnerId);
    //$$     if (partner == null) return;
    //$$     FGA_PERMISSION_REENTRY.set(true);
    //$$     try {
    //$$         callback.setReturnValue(((MinecraftServer) (Object) this).getProfilePermissions(
    //$$                 new NameAndId(partner.getGameProfile())));
    //$$     } finally {
    //$$         FGA_PERMISSION_REENTRY.set(false);
    //$$     }
    //$$ }
    //#else
    @Inject(method = "getProfilePermissions", at = @At("RETURN"), cancellable = true)
    private void fga$permissionForPossession(GameProfile profile, CallbackInfoReturnable<Integer> callback) {
        if (!FGASettings.permissionSwapsToo || FGA_PERMISSION_REENTRY.get()) return;
        UUID id = profile.getId();
        if (id == null || !PlayerPossessionManager.isParticipantId(id)) return;
        UUID partnerId = PlayerPossessionManager.swapPartner(id);
        ServerPlayer partner = partnerId == null ? null : getPlayerList().getPlayer(partnerId);
        if (partner == null) return;
        FGA_PERMISSION_REENTRY.set(true);
        try {
            callback.setReturnValue(((MinecraftServer) (Object) this).getProfilePermissions(partner.getGameProfile()));
        } finally {
            FGA_PERMISSION_REENTRY.set(false);
        }
    }
    //#endif
}
//#endif
