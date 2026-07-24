package carpet.fga;

import carpet.CarpetServer;
import net.fabricmc.api.ModInitializer;

/**
 * Carpet FGA Addition 模组入口。
 * 在 Fabric ModInitializer 阶段注册 Carpet 扩展。
 */
public class CarpetFGAAddition implements ModInitializer {

    @Override
    public void onInitialize() {
        CarpetServer.manageExtension(new FGAExtension());
    }
}
