//#if MC >= 1.20.5
package carpet.fga.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class InventoryAdvancementMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> CONFLICTS = Set.of("achiopt", "cerulean", "icterine");

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            for (String id : CONFLICTS) {
                if (isInventoryAdvancementMixin(mixinClassName) && FabricLoader.getInstance().isModLoaded(id)) {
                    System.setProperty("invadvopt.mixin.conflict", "true");
                    return false;
                }
            }
        } catch (Throwable ignored) {
            // The mod entry point repeats this check after loading is complete.
        }
        return true;
    }

    private static boolean isInventoryAdvancementMixin(String mixinClassName) {
        return mixinClassName.endsWith("InventoryChangeTriggerMixin")
                || mixinClassName.endsWith("ItemPredicateMixin")
                || mixinClassName.endsWith("MinecraftServerReloadMixin")
                || mixinClassName.endsWith("SimpleCriterionTriggerMixin")
                || mixinClassName.endsWith("SimpleCriterionTriggerAccessor");
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        String hook = switch (targetClassName) {
            case "net.minecraft.advancements.critereon.SimpleCriterionTrigger" -> "listener";
            case "net.minecraft.advancements.critereon.InventoryChangeTrigger" -> "trigger";
            case "net.minecraft.advancements.critereon.ItemPredicate" -> "predicate";
            case "net.minecraft.advancements.triggers.InventoryChangeTrigger" -> "trigger";
            case "net.minecraft.server.MinecraftServer" -> "reload";
            default -> null;
        };
        if (hook != null && containsInjectedCall(targetClass, "invadvopt$")) {
            System.setProperty("invadvopt.mixin." + hook, "true");
        }
    }

    private static boolean containsInjectedCall(ClassNode targetClass, String marker) {
        for (MethodNode method : targetClass.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode invocation && invocation.name.contains(marker)) {
                    return true;
                }
            }
        }
        return false;
    }
}
//#endif


