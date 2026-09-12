package carpet.fga.mixin;

//#if MC >= 1.21 && MC <= 26.2
import carpet.fga.PlayerPossessionManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
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
public abstract class PossessionPlayerInfoMixin {
    @Shadow @Final @Mutable
    private List<ClientboundPlayerInfoUpdatePacket.Entry> entries;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fga$decoratePossessionNames(CallbackInfo callback) {
        MinecraftServer server = carpet.CarpetServer.minecraft_server;
        if (server == null || !FGASettingsBridge.enabled()) return;

        boolean changed = false;
        List<ClientboundPlayerInfoUpdatePacket.Entry> updated = new ArrayList<>(entries.size());
        for (ClientboundPlayerInfoUpdatePacket.Entry entry : entries) {
            GameProfile profile = entry.profile();
            String fallback = profile == null ? null : profile.getName();
            Component display = PlayerPossessionManager.decoratePlayerInfo(
                    entry.profileId(), entry.displayName(), fallback, server);
            if (display != entry.displayName()) {
                changed = true;
                updated.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                        entry.profileId(), profile, entry.listed(), entry.latency(), entry.gameMode(), display,
                        //#if MC >= 1.21.4
                        //$$ entry.showHat(),
                        //#endif
                        //#if MC >= 1.21.2
                        //$$ entry.listOrder(),
                        //#endif
                        entry.chatSession()));
            } else {
                updated.add(entry);
            }
        }
        if (changed) entries = List.copyOf(updated);
    }

    private static final class FGASettingsBridge {
        private static boolean enabled() {
            return carpet.fga.FGASettings.showControllerPrefix;
        }
    }
}
//#endif
