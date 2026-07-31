from pathlib import Path
p = Path(r"D:/ai/carpet-fga/src/main/java/carpet/fga/FGAExtension.java")
text = p.read_text(encoding="utf-8")
old = '''    private static void registerUnlimitedFillLegacyBridge() {
        //#if MC < 1.19.4
        //#if MC >= 1.19
        carpet.api.settings.SettingsManager.addGlobalRuleObserver((source, rule, userInput) -> {
            if ("unlimitedFillCommands".equals(rule.name())) {
                syncCarpetFillLimitForLegacyVersions(Boolean.TRUE.equals(rule.value()));
            }
        });
        //#else
        //$$ carpet.settings.SettingsManager.addGlobalRuleObserver((source, rule, userInput) -> {
        //$$     if (rule.name.equals("unlimitedFillCommands")) {
        //$$         syncCarpetFillLimitForLegacyVersions(Boolean.TRUE.equals(rule.get()));
        //$$     }
        //$$ });
        //#endif
        //#endif
    }'''
new = '''    private static void registerUnlimitedFillLegacyBridge() {
        //#if MC < 1.19.4
        //#if MC >= 1.19
        carpet.api.settings.SettingsManager.registerGlobalRuleObserver((source, rule, userInput) -> {
            if ("unlimitedFillCommands".equals(rule.name())) {
                syncCarpetFillLimitForLegacyVersions(Boolean.TRUE.equals(rule.value()));
            }
        });
        //#else
        //$$ carpet.settings.SettingsManager.addGlobalRuleObserver((source, rule, userInput) -> {
        //$$     if ("unlimitedFillCommands".equals(rule.name)) {
        //$$         syncCarpetFillLimitForLegacyVersions(Boolean.TRUE.equals(rule.get()));
        //$$     }
        //$$ });
        //#endif
        //#endif
    }'''
if old not in text:
    print('helper block not exact match')
    idx = text.find('registerUnlimitedFillLegacyBridge')
    print(repr(text[idx:idx+500]))
else:
    p.write_text(text.replace(old, new), encoding='utf-8', newline='\n')
    print('helper fixed')

# Also fix SettingsManager import usage - carpet.settings.SettingsManager vs api
# onGameStarted uses carpet.settings.SettingsManager - for 1.19+ might need api
# Check existing code - it used carpet.settings.SettingsManager carpetManager = CarpetServer.settingsManager before, so keep that.

print('onGameStarted calls bridge', 'registerUnlimitedFillLegacyBridge()' in p.read_text(encoding='utf-8'))
