package carpet.fga.mixin;

import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class OrgExperienceMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> SUPPORTED_ORG_VERSIONS = Set.of(
            //#if MC < 26.0
            "1.41.5", "1.41.6"
            //#else
            //#if MC < 26.2
            //$$ "1.44.0"
            //#else
            //#if MC < 26.3
            //$$ "1.45.1"
            //#else
            //$$ "1.46.0"
            //#endif
            //#endif
            //#endif
    );

    private boolean supported;

    @Override
    public void onLoad(String mixinPackage) {
        var org = FabricLoader.getInstance().getModContainer("carpet-org-addition");
        supported = org.map(mod -> SUPPORTED_ORG_VERSIONS.contains(
                mod.getMetadata().getVersion().getFriendlyString())).orElse(false);
        if (org.isPresent() && !supported) {
            LoggerFactory.getLogger("CarpetFGAAddition").warn(
                    "ORG flat XP transfer compatibility is not verified for installed ORG version {}; adapter not enabled",
                    org.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
        }
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return supported; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
