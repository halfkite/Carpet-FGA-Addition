from pathlib import Path
p = Path(r"D:/ai/carpet-fga/src/main/java/carpet/fga/FGAExtension.java")
text = p.read_text(encoding="utf-8")
old = '''    @Override
    public void onGameStarted() {
        VillagerBreedingAnimalization.registerRuleObserver();
        //#if MC < 1.19
        //$$ carpet.settings.SettingsManager.addGlobalRuleObserver((source, rule, userInput) -> {
        //$$     if (rule.name.equals("unlimitedFillCommands")) {
        //$$         carpet.CarpetSettings.fillLimit = Boolean.TRUE.equals(rule.get()) ? Integer.MAX_VALUE : 32768;
        //$$     }
        //$$ });
        //#endif
        // ??FGA ????????carpet ??SettingsManager???????????? /carpet ??        carpet.settings.SettingsManager carpetManager = CarpetServer.settingsManager;
        if (carpetManager != null) {
            carpetManager.parseSettingsClass(FGASettings.class);
        }
    }'''
# The comment may have encoding issues - use a more flexible replace
import re
new_on_game = '''    @Override
    public void onGameStarted() {
        VillagerBreedingAnimalization.registerRuleObserver();
        registerUnlimitedFillLegacyBridge();
        // Register FGA rules into carpet's main SettingsManager so they appear under /carpet.
        carpet.settings.SettingsManager carpetManager = CarpetServer.settingsManager;
        if (carpetManager != null) {
            carpetManager.parseSettingsClass(FGASettings.class);
        }
    }'''
pat = re.compile(r"    @Override\r?\n    public void onGameStarted\(\) \{.*?\n    \}", re.S)
m = pat.search(text)
if not m:
    print('onGameStarted not found')
else:
    text = pat.sub(new_on_game, text, count=1)
    p.write_text(text, encoding='utf-8', newline='\n')
    print('onGameStarted replaced')

# Verify rule observer API - check Carpet 1.19.2 for addGlobalRuleObserver and rule.name()/value()
print('helpers present', 'registerUnlimitedFillLegacyBridge' in text, 'syncCarpetFillLimitForLegacyVersions' in text)
print(text[text.find('onGameStarted'):text.find('onGameStarted')+500])
