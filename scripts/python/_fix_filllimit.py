from pathlib import Path
path = Path(r"D:/ai/carpet-fga/src/main/java/carpet/fga/FGAExtension.java")
text = path.read_text(encoding="utf-8")
old = '''    private static void syncCarpetFillLimitForLegacyVersions(boolean unlimited) {
        //#if MC < 1.19.4
        try {
            // Direct field write bypasses /carpet validators; 20M is Carpet's documented max,
            // but MAX_VALUE is accepted by the fill mixin path and keeps parity with newer gamerule unlock.
            carpet.CarpetSettings.fillLimit = unlimited ? Integer.MAX_VALUE : 32768;
        } catch (Throwable ignored) {
            // Carpet version without fillLimit should not break startup.
        }
        //#endif
    }'''
new = '''    private static void syncCarpetFillLimitForLegacyVersions(boolean unlimited) {
        //#if MC < 1.19.4
        //$$ try {
        //$$     // Direct field write bypasses /carpet validators; 20M is Carpet's documented max,
        //$$     // but MAX_VALUE is accepted by the fill mixin path and keeps parity with newer gamerule unlock.
        //$$     carpet.CarpetSettings.fillLimit = unlimited ? Integer.MAX_VALUE : 32768;
        //$$ } catch (Throwable ignored) {
        //$$     // Carpet version without fillLimit should not break startup.
        //$$ }
        //#endif
    }'''
if old not in text:
    raise SystemExit('block not found')
path.write_text(text.replace(old, new, 1), encoding='utf-8', newline='\n')
print('fixed fillLimit root gating')

# Also check registerUnlimitedFillLegacyBridge - the MC < 1.19.4 outer should be disabled on root
text = path.read_text(encoding='utf-8')
# show the bridge method
start = text.find('registerUnlimitedFillLegacyBridge')
print(text[start:start+900])