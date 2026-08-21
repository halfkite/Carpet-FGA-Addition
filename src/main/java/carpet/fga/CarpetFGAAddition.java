package carpet.fga;

import carpet.CarpetServer;
//#if MC >= 1.21 && MC <= 26.2
import com.yiyihehe.quickcraft.litematica.QuickLitematicaEntityPlacementPayloads;
//#endif
import net.fabricmc.api.ModInitializer;
//#if MC >= 1.21 && MC <= 26.2
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//#endif

/**
 * Carpet FGA Addition 模组入口。
 * 在 Fabric ModInitializer 阶段注册 Carpet 扩展。
 */
public class CarpetFGAAddition implements ModInitializer {

    @Override
    public void onInitialize() {
        //#if MC >= 1.21 && MC <= 26.2
        //#if MC >= 26.1.2
        //$$ PayloadTypeRegistry.serverboundPlay().register(
        //#else
        PayloadTypeRegistry.playC2S().register(
        //#endif
                QuickLitematicaEntityPlacementPayloads.HelloPayload.ID,
                QuickLitematicaEntityPlacementPayloads.HelloPayload.CODEC
        );
        //#if MC >= 26.1.2
        //$$ PayloadTypeRegistry.serverboundPlay().register(
        //#else
        PayloadTypeRegistry.playC2S().register(
        //#endif
                QuickLitematicaEntityPlacementPayloads.RequestPayload.ID,
                QuickLitematicaEntityPlacementPayloads.RequestPayload.CODEC
        );
        //#if MC >= 26.1.2
        //$$ PayloadTypeRegistry.clientboundPlay().register(
        //#else
        PayloadTypeRegistry.playS2C().register(
        //#endif
                QuickLitematicaEntityPlacementPayloads.CapabilityPayload.ID,
                QuickLitematicaEntityPlacementPayloads.CapabilityPayload.CODEC
        );
        //#if MC >= 26.1.2
        //$$ PayloadTypeRegistry.clientboundPlay().register(
        //#else
        PayloadTypeRegistry.playS2C().register(
        //#endif
                QuickLitematicaEntityPlacementPayloads.ResultPayload.ID,
                QuickLitematicaEntityPlacementPayloads.ResultPayload.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(
                QuickLitematicaEntityPlacementPayloads.HelloPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> QuickCraftEntityPlacementServer.handleHello(context.player(), payload))
        );
        ServerPlayNetworking.registerGlobalReceiver(
                QuickLitematicaEntityPlacementPayloads.RequestPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> QuickCraftEntityPlacementServer.handleRequest(context.player(), payload))
        );
        //#endif
        CarpetServer.manageExtension(new FGAExtension());
    }
}
