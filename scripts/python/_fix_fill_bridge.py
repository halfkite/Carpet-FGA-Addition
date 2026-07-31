from pathlib import Path

# 1) Remove CarpetFillCommandLimitMixin file content by gating always false / delete
Path(r"D:/ai/carpet-fga/src/main/java/carpet/fga/mixin/CarpetFillCommandLimitMixin.java").write_text(
"""// Removed: Mixin cannot target another mixin class (carpet.mixins.FillCommandMixin).
// Pre-1.19.4 fill unlimited is handled by syncing carpet.CarpetSettings.fillLimit
// from FGAExtension when unlimitedFillCommands changes.
""",
encoding="utf-8", newline="\n")

# 2) Update mixins.json - remove CarpetFillCommandLimitMixin registration
mix_path = Path(r"D:/ai/carpet-fga/src/main/resources/carpet-fga-addition.mixins.json")
mix = mix_path.read_text(encoding="utf-8")
old = """        //#if MC <= 26.2
        //#if MC < 1.19.4
        ,\"CarpetFillCommandLimitMixin\"
        //#else
        ,\"FillCommandLimitMixin\"
        //#endif
        //#if MC >= 1.19.4
        ,\"FillBiomeCommandLimitMixin\"
        //#endif
        //#endif"""
new = """        //#if MC <= 26.2
        //#if MC >= 1.19.4
        ,\"FillCommandLimitMixin\"
        ,\"FillBiomeCommandLimitMixin\"
        //#endif
        //#endif"""
if old not in mix:
    print("mixins block missing")
    for i,l in enumerate(mix.splitlines(),1):
        if "Fill" in l or "CarpetFill" in l or "MC <= 26.2" in l:
            print(i,l)
else:
    mix_path.write_text(mix.replace(old,new), encoding="utf-8", newline="\n")
    print("mixins ok")

# 3) common.gradle: always exclude empty CarpetFillCommandLimitMixin; FillCommand only <1.19.4 exclude
cg_path = Path(r"D:/ai/carpet-fga/common.gradle")
cg = cg_path.read_text(encoding="utf-8")
old_cg = """if (mcVersion < 1_19_04) {
    sourceSets.main.java.exclude(
            'carpet/fga/mixin/FillBiomeCommandLimitMixin.java',
            'carpet/fga/mixin/FillCommandLimitMixin.java'
    )
} else {
    sourceSets.main.java.exclude(
            'carpet/fga/mixin/CarpetFillCommandLimitMixin.java'
    )
}"""
new_cg = """if (mcVersion < 1_19_04) {
    sourceSets.main.java.exclude(
            'carpet/fga/mixin/FillBiomeCommandLimitMixin.java',
            'carpet/fga/mixin/FillCommandLimitMixin.java',
            'carpet/fga/mixin/CarpetFillCommandLimitMixin.java'
    )
} else {
    sourceSets.main.java.exclude(
            'carpet/fga/mixin/CarpetFillCommandLimitMixin.java'
    )
}"""
if old_cg not in cg:
    print("common block missing")
else:
    cg_path.write_text(cg.replace(old_cg, new_cg), encoding="utf-8", newline="\n")
    print("common ok")

# 4) Patch FGAExtension for fillLimit sync on MC < 1.19.4
ext_path = Path(r"D:/ai/carpet-fga/src/main/java/carpet/fga/FGAExtension.java")
ext = ext_path.read_text(encoding="utf-8")

# Replace onGameStarted fill observer section and add helper + onServerLoaded sync
if "syncCarpetFillLimitForLegacyVersions" not in ext:
    ext = ext.replace(
"""    @Override
    public void onGameStarted() {
        VillagerBreedingAnimalization.registerRuleObserver();
        //#if MC < 1.19
        //$$ carpet.settings.SettingsManager.addGlobalRuleObserver((source, rule, userInput) -> {
        //$$     if (rule.name.equals("unlimitedFillCommands")) {
        //$$         carpet.CarpetSettings.fillLimit = Boolean.TRUE.equals(rule.get()) ? Integer.MAX_VALUE : 32768;
        //$$     }
        //$$ });
        //#endif
        // ??FGA ????????carpet ??SettingsManager???????????? /carpet ??
        carpet.settings.SettingsManager carpetManager = CarpetServer.settingsManager;
""",
"""    @Override
    public void onGameStarted() {
        VillagerBreedingAnimalization.registerRuleObserver();
        registerUnlimitedFillLegacyBridge();
        // ? FGA ????? carpet ? SettingsManager???????? /carpet ?
        carpet.settings.SettingsManager carpetManager = CarpetServer.settingsManager;
""")

    # add onServerLoaded sync
    ext = ext.replace(
"""    public void onServerLoaded(MinecraftServer server) {
        //#if MC >= 1.21.1
        VillagerPerformanceConfig.load(server);
        //#endif
        //#if MC <= 26.2
        DroppedItemStackLimitConfig.load(server);
        DroppedItemStackLimitConfig.warnLegacyRule(server);
        //#endif
    }
""",
"""    public void onServerLoaded(MinecraftServer server) {
        //#if MC >= 1.21.1
        VillagerPerformanceConfig.load(server);
        //#endif
        //#if MC <= 26.2
        DroppedItemStackLimitConfig.load(server);
        DroppedItemStackLimitConfig.warnLegacyRule(server);
        //#endif
        syncCarpetFillLimitForLegacyVersions(FGASettings.unlimitedFillCommands);
    }
""")

    helper = '''
    /**
     * Pre-1.19.4 Carpet already owns the /fill 32768 constant via fillLimit.
     * Sync that value instead of fighting Carpet's FillCommandMixin.
     */
    private static void registerUnlimitedFillLegacyBridge() {
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
    }

    private static void syncCarpetFillLimitForLegacyVersions(boolean unlimited) {
        //#if MC < 1.19.4
        try {
            // Direct field write bypasses /carpet validators; 20M is Carpet's documented max,
            // but MAX_VALUE is accepted by the fill mixin path and keeps parity with newer gamerule unlock.
            carpet.CarpetSettings.fillLimit = unlimited ? Integer.MAX_VALUE : 32768;
        } catch (Throwable ignored) {
            // Carpet version without fillLimit should not break startup.
        }
        //#endif
    }

'''
    # insert helper before version() method
    if "private static void registerUnlimitedFillLegacyBridge()" not in ext:
        ext = ext.replace(
            "    @Override\n    public String version() {",
            helper + "    @Override\n    public String version() {"
        )
    ext_path.write_text(ext, encoding="utf-8", newline="\n")
    print("extension patched")
else:
    print("extension already patched")

print("done")
print(mix_path.read_text(encoding='utf-8').split('Fill')[0][-80:])
