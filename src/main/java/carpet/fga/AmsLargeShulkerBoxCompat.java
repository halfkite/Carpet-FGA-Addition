//#if MC >= 1.21 && MC <= 26.2
package carpet.fga;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/** Optional compatibility with both package generations of Carpet AMS Addition. */
final class AmsLargeShulkerBoxCompat {
    private static final String MOD_ID = "carpet-ams-addition";
    private static final LazyRuleAccess RULE = findRule();

    private AmsLargeShulkerBoxCompat() {
    }

    static boolean isEnabled() {
        return RULE != null && RULE.isEnabled();
    }

    private static LazyRuleAccess findRule() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return null;
        for (String className : new String[]{
                "carpetamsaddition.CarpetAMSAdditionLazySettings",
                "club.mcams.carpet.AmsServerLazySettings"
        }) {
            LazyRuleAccess access = LazyRuleAccess.create(className);
            if (access != null) return access;
        }
        return null;
    }

    private record LazyRuleAccess(Method method, Object rule) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        private static LazyRuleAccess create(String className) {
            try {
                ClassLoader loader = AmsLargeShulkerBoxCompat.class.getClassLoader();
                Class<?> settingsClass = Class.forName(className, false, loader);
                Class<? extends Enum> ruleClass = Class.forName(className + "$Rule", false, loader)
                        .asSubclass(Enum.class);
                Object rule = Enum.valueOf(ruleClass, "LARGE_SHULKER_BOX");
                return new LazyRuleAccess(settingsClass.getMethod("isEnabled", ruleClass), rule);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }

        private boolean isEnabled() {
            try {
                return Boolean.TRUE.equals(method.invoke(null, rule));
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return false;
            }
        }
    }
}
//#endif
