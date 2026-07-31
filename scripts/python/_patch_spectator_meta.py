from pathlib import Path
import re

root = Path(r"D:/ai/carpet-fga")

def read(path):
    return path.read_text(encoding="utf-8")

def write(path, text):
    path.write_text(text.replace("\r\n", "\n"), encoding="utf-8", newline="\n")
    print(f"updated {path}")

# --- FGASettings ---
settings_path = root / "src/main/java/carpet/fga/FGASettings.java"
settings = read(settings_path)
rule_block = '''
    //#if MC >= 1.21.1
    @Rule(
        desc = "Allows non-OP spectators to use /tp and /teleport on themselves only",
        category = {FGA, FEATURE},
        condition = FGASettings.Minecraft1_21_1OnlyCondition.class
    )
    public static boolean spectatorFreeTeleport = false;
    //#endif

'''
if "spectatorFreeTeleport" not in settings:
    anchor = "    public static boolean unicodeArgumentsSupport = false;"
    if anchor not in settings:
        raise SystemExit("anchor not found in FGASettings")
    settings = settings.replace(anchor, anchor + "\n" + rule_block, 1)
    write(settings_path, settings)
else:
    print("FGASettings already has spectatorFreeTeleport")

# --- FGAExtension ---
ext_path = root / "src/main/java/carpet/fga/FGAExtension.java"
ext = read(ext_path)
if "previousSpectatorFreeTeleportRule" not in ext:
    ext = ext.replace(
        "    private boolean previousBeeCollisionBoxRule;",
        "    private boolean previousBeeCollisionBoxRule;\n    //#if MC >= 1.21.1\n    private boolean previousSpectatorFreeTeleportRule;\n    //#endif",
        1,
    )
    # onTick refresh
    old_tick_end = """        if (previousBeeCollisionBoxRule != FGASettings.restorePre26BeeCollisionBox) {
            previousBeeCollisionBoxRule = FGASettings.restorePre26BeeCollisionBox;
            BeeDimensions.refreshLoadedBees(server);
        }
    }"""
    new_tick_end = """        if (previousBeeCollisionBoxRule != FGASettings.restorePre26BeeCollisionBox) {
            previousBeeCollisionBoxRule = FGASettings.restorePre26BeeCollisionBox;
            BeeDimensions.refreshLoadedBees(server);
        }
        //#if MC >= 1.21.1
        if (previousSpectatorFreeTeleportRule != FGASettings.spectatorFreeTeleport) {
            previousSpectatorFreeTeleportRule = FGASettings.spectatorFreeTeleport;
            server.getPlayerList().getPlayers().forEach(player -> server.getCommands().sendCommands(player));
        }
        //#endif
    }"""
    if old_tick_end not in ext:
        raise SystemExit("onTick anchor not found")
    ext = ext.replace(old_tick_end, new_tick_end, 1)
    # onServerClosed reset
    old_close = """        RangeActionManager.clear();
        previousBeeCollisionBoxRule = false;
    }"""
    new_close = """        RangeActionManager.clear();
        previousBeeCollisionBoxRule = false;
        //#if MC >= 1.21.1
        previousSpectatorFreeTeleportRule = false;
        //#endif
    }"""
    if old_close not in ext:
        raise SystemExit("onServerClosed anchor not found")
    ext = ext.replace(old_close, new_close, 1)
    write(ext_path, ext)
else:
    print("FGAExtension already patched")

# --- mixins.json ---
mixins_path = root / "src/main/resources/carpet-fga-addition.mixins.json"
mixins = read(mixins_path)
if "TeleportCommandMixin" not in mixins:
    needle = '''        //#if MC >= 1.21.1
        ,"ClientDimensionIdPacketMixin"
        //#endif'''
    repl = '''        //#if MC >= 1.21.1
        ,"ClientDimensionIdPacketMixin",
        "TeleportCommandMixin",
        "ServerPlayerSpectatorTeleportMixin"
        //#endif'''
    if needle not in mixins:
        raise SystemExit("mixins anchor not found")
    mixins = mixins.replace(needle, repl, 1)
    write(mixins_path, mixins)
else:
    print("mixins already patched")

# --- lang en_us ---
en_path = root / "src/main/resources/assets/carpet-fga-addition/lang/en_us.json"
en = read(en_path)
if "spectatorFreeTeleport" not in en:
    en_entry = '''  "carpet.rule.spectatorFreeTeleport.name": "Spectator Free Teleport",
  "carpet.rule.spectatorFreeTeleport.desc": "Allows non-OP players in spectator mode to use /tp and /teleport on themselves only. Real OPs keep full teleport permissions.",
  "carpet.rule.spectatorFreeTeleport.extra.0": "false: Keep vanilla teleport permission requirements.",
  "carpet.rule.spectatorFreeTeleport.extra.1": "true: Non-OP spectators may teleport themselves to coordinates or entities. Teleporting other entities is still denied.",
  "carpet-fga-addition.command.spectatorFreeTeleport.selfOnly": "Non-OP spectators may only teleport themselves",
'''
    # insert before final closing - after villagerPerformance extra if present, else before last }
    if '"carpet.rule.villagerPerformanceOptimization.extra.0"' in en:
        en = en.replace(
            '"carpet.rule.villagerPerformanceOptimization.extra.0": "Use /villagerPerformance help for clickable trade and Hero of the Village gift configuration."\n}',
            '"carpet.rule.villagerPerformanceOptimization.extra.0": "Use /villagerPerformance help for clickable trade and Hero of the Village gift configuration.",\n' + en_entry.rstrip() + "\n}",
            1,
        )
    else:
        en = en.rstrip()
        if en.endswith("}"):
            en = en[:-1].rstrip()
            if not en.endswith(","):
                en += ","
            en += "\n" + en_entry + "}\n"
    write(en_path, en)
else:
    print("en_us already patched")

# --- lang zh_cn ---
zh_path = root / "src/main/resources/assets/carpet-fga-addition/lang/zh_cn.json"
zh = read(zh_path)
if "spectatorFreeTeleport" not in zh:
    zh_entry = '''  "carpet.rule.spectatorFreeTeleport.name": "旁观者免权限自身传送",
  "carpet.rule.spectatorFreeTeleport.desc": "允许非 OP 的旁观模式玩家使用 /tp 与 /teleport，但只能传送自己。真正的 OP 仍可完整传送。",
  "carpet.rule.spectatorFreeTeleport.extra.0": "false：保持原版传送权限要求。",
  "carpet.rule.spectatorFreeTeleport.extra.1": "true：非 OP 旁观者可传送自己到坐标或其他实体；传送其他实体仍会被拒绝。",
  "carpet-fga-addition.command.spectatorFreeTeleport.selfOnly": "非 OP 旁观者只能传送自己",
'''
    if '"carpet.rule.villagerPerformanceOptimization.extra.0"' in zh:
        # match whatever the current last villager line is
        m = re.search(r'("carpet\.rule\.villagerPerformanceOptimization\.extra\.0":\s*".*?")\s*}\s*$', zh, re.S)
        if not m:
            raise SystemExit("zh villager extra end not found")
        zh = zh[:m.end(1)] + ",\n" + zh_entry.rstrip() + "\n}\n"
    else:
        zh = zh.rstrip()
        if zh.endswith("}"):
            zh = zh[:-1].rstrip()
            if not zh.endswith(","):
                zh += ","
            zh += "\n" + zh_entry + "}\n"
    write(zh_path, zh)
else:
    print("zh_cn already patched")

# --- README ---
readme_path = root / "README.md"
readme = read(readme_path)
if "spectatorFreeTeleport" not in readme:
    section = '''
## 旁观者免权限自身传送（Minecraft 1.21.1）

```text
/carpet spectatorFreeTeleport true
```

默认关闭。开启后，**旁观模式且非 OP** 的玩家可以使用原版 `/tp` 与 `/teleport`，但**只能传送自己**（例如 `/tp ~ ~10 ~`、`/tp <实体>`、`/tp @s <坐标>`）。真正权限等级 ≥ 2 的 OP 不受额外限制。关闭规则时完全保持原版权限。

切换旁观模式或改动该规则后，会刷新命令树，无需重进游戏即可看到 `/tp` 补全。

'''
    # insert after Unicode section or near top features
    anchor = "## Unicode 指令参数支持"
    if anchor in readme:
        readme = readme.replace(anchor, section + anchor, 1)
    else:
        readme = section + readme
    write(readme_path, readme)
else:
    print("README already patched")

print("all metadata updates done")