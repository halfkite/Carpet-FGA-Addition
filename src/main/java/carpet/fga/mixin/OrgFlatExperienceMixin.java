package carpet.fga.mixin;

import carpet.fga.FlatExperienceManager;
import carpet.fga.FlatExperienceMath;
import carpet.fga.compat.OrgExperienceAccess;
import carpet.fga.compat.OrgFlatExperienceCompat;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;

/** Optional adapter for the checked ORG ExperienceTransfer implementations. */
@Pseudo
@Mixin(targets =
        //#if MC < 26.0
        "org.carpetorgaddition.wheel.ExperienceTransfer"
        //#else
        //#if MC < 26.2
        //$$ "boat.carpetorgaddition.wheel.ExperienceTransfer"
        //#else
        //#if MC < 26.3
        //$$ "boat.carpetorgaddition.wheel.misc.ExperienceTransfer"
        //#else
        //$$ "boat.carpetorgaddition.command.XpTransferCommand$ExperienceTransfer"
        //#endif
        //#endif
        //#endif
        , remap = false)
public abstract class OrgFlatExperienceMixin implements OrgExperienceAccess {
    @Shadow @Final private ServerPlayer player;

    @Override
    public ServerPlayer carpetFga$experiencePlayer() { return player; }

    @Inject(method = "calculateTotalExperience()Ljava/math/BigInteger;", at = @At("HEAD"), cancellable = true)
    private void carpetFga$total(CallbackInfoReturnable<BigInteger> cir) {
        var mode = FlatExperienceManager.mode();
        if (mode != null) cir.setReturnValue(BigInteger.valueOf(OrgFlatExperienceCompat.total(player, mode)));
    }

    @Inject(method = "calculateUpgradeExperience(II)Ljava/math/BigInteger;", at = @At("HEAD"), cancellable = true)
    private static void carpetFga$upgrade(int from, int to, CallbackInfoReturnable<BigInteger> cir) {
        var mode = FlatExperienceManager.mode();
        if (mode != null && from >= 0 && to >= 0) {
            cir.setReturnValue(BigInteger.valueOf(FlatExperienceMath.atLevel(to, mode)
                    - FlatExperienceMath.atLevel(from, mode)));
        }
    }

    @Inject(method = "transferAllTo", at = @At("HEAD"), cancellable = true)
    private void carpetFga$all(@Coerce Object other, CallbackInfoReturnable<BigInteger> cir)
            throws CommandSyntaxException {
        var mode = FlatExperienceManager.mode();
        if (mode != null) {
            cir.setReturnValue(OrgFlatExperienceCompat.transfer(player,
                    ((OrgExperienceAccess) other).carpetFga$experiencePlayer(),
                    BigInteger.valueOf(OrgFlatExperienceCompat.total(player, mode)), mode));
        }
    }

    @Inject(method = "transferHalfTo", at = @At("HEAD"), cancellable = true)
    private void carpetFga$half(@Coerce Object other, CallbackInfoReturnable<BigInteger> cir)
            throws CommandSyntaxException {
        var mode = FlatExperienceManager.mode();
        if (mode != null) {
            cir.setReturnValue(OrgFlatExperienceCompat.transfer(player,
                    ((OrgExperienceAccess) other).carpetFga$experiencePlayer(),
                    BigInteger.valueOf(OrgFlatExperienceCompat.total(player, mode) / 2), mode));
        }
    }

    @Inject(method = "transferTo", at = @At("HEAD"), cancellable = true)
    private void carpetFga$points(@Coerce Object other, BigInteger amount, CallbackInfo ci)
            throws CommandSyntaxException {
        var mode = FlatExperienceManager.mode();
        // Let ORG produce its own insufficient-experience feedback.
        if (mode != null && amount.signum() >= 0
                && amount.compareTo(BigInteger.valueOf(OrgFlatExperienceCompat.total(player, mode))) <= 0) {
            OrgFlatExperienceCompat.transfer(player,
                    ((OrgExperienceAccess) other).carpetFga$experiencePlayer(), amount, mode);
            ci.cancel();
        }
    }
}
