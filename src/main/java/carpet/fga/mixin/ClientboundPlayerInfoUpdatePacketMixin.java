//#if MC >= 1.19.3
package carpet.fga.mixin;

import carpet.fga.FakePlayerNameAlias;
//#if MC >= 1.19.4
import carpet.fga.PlayerHealthDisplay;
//#endif
import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
//#if MC >= 1.19.4
import net.minecraft.server.level.ServerPlayer;
//#endif
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public abstract class ClientboundPlayerInfoUpdatePacketMixin {
    @Shadow
    @Final
    @Mutable
    private List<ClientboundPlayerInfoUpdatePacket.Entry> entries;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceLongNames(CallbackInfo ci) {
        boolean changed = false;
        List<ClientboundPlayerInfoUpdatePacket.Entry> aliases = new ArrayList<>(entries.size());
        for (ClientboundPlayerInfoUpdatePacket.Entry entry : entries) {
            GameProfile profile = entry.profile();
            GameProfile networkProfile = profile == null ? null : FakePlayerNameAlias.networkProfile(profile);
            //#if MC >= 1.19.4
            ServerPlayer player = PlayerHealthDisplay.getOnlinePlayer(entry.profileId());
            net.minecraft.network.chat.Component displayName = player == null
                    ? entry.displayName()
                    : PlayerHealthDisplay.tabDisplayName(player, entry.displayName());
            //#else
            //$$ net.minecraft.network.chat.Component displayName = entry.displayName();
            //#endif
            if (networkProfile != profile || displayName != entry.displayName()) {
                changed = true;
                aliases.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                        entry.profileId(), networkProfile, entry.listed(), entry.latency(), entry.gameMode(),
                        displayName,
                        //#if MC >= 1.21.4
                        //$$ entry.showHat(),
                        //#endif
                        //#if MC >= 1.21.2
                        //$$ entry.listOrder(),
                        //#endif
                        entry.chatSession()));
            } else {
                aliases.add(entry);
            }
        }
        if (changed) {
            entries = List.copyOf(aliases);
        }
    }
}
//#endif
