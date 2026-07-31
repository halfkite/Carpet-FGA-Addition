from pathlib import Path

fill = """//#if MC >= 1.19.4
//#if MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.server.commands.FillCommand;
//#if MC >= 1.21.11
//$$ import net.minecraft.world.level.gamerules.GameRule;
//$$ import net.minecraft.world.level.gamerules.GameRules;
//#else
import net.minecraft.world.level.GameRules;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FillCommand.class)
public abstract class FillCommandLimitMixin {
    // 1.19.4-1.21.1: gamerule limit stored into local 6
    //#if MC <= 1.21.1
    @ModifyVariable(method = "fillBlocks", at = @At(value = "STORE"), index = 6)
    private static int carpetFga$unlimitedFillVolume(int effectiveLimit) {
        return FGASettings.unlimitedFillCommands ? Integer.MAX_VALUE : effectiveLimit;
    }
    //#elseif MC >= 1.21.11
    //$$ @Redirect(
    //$$         method = "fillBlocks",
    //$$         at = @At(
    //$$                 value = "INVOKE",
    //$$                 target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"
    //$$         )
    //$$ )
    //$$ private static Object carpetFga$unlimitedFillVolume(GameRules rules, GameRule<?> rule) {
    //$$     return FGASettings.unlimitedFillCommands ? Integer.MAX_VALUE : rules.get(rule);
    //$$ }
    //#else
    //$$ @Redirect(
    //$$         method = "fillBlocks",
    //$$         at = @At(
    //$$                 value = "INVOKE",
    //$$                 target = "Lnet/minecraft/world/level/GameRules;getInt(Lnet/minecraft/world/level/GameRules$Key;)I"
    //$$         )
    //$$ )
    //$$ private static int carpetFga$unlimitedFillVolume(GameRules rules,
    //$$                                                    GameRules.Key<GameRules.IntegerValue> key) {
    //$$     return FGASettings.unlimitedFillCommands ? Integer.MAX_VALUE : rules.getInt(key);
    //$$ }
    //#endif
}
//#endif
//#endif
"""

carpet_fill = """//#if MC < 1.19.4
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Carpet already replaces vanilla fill 32768 via fillLimit.
 * Hook that Carpet handler so unlimitedFillCommands can still lift the cap.
 */
@Mixin(targets = "carpet.mixins.FillCommandMixin", remap = false)
public abstract class CarpetFillCommandLimitMixin {
    @Inject(method = "fillLimit", at = @At("HEAD"), cancellable = true, remap = false)
    private static void carpetFga$unlimitedFillVolume(int original, CallbackInfoReturnable<Integer> cir) {
        if (FGASettings.unlimitedFillCommands) {
            cir.setReturnValue(Integer.MAX_VALUE);
        }
    }
}
//#endif
"""

Path(r"D:/ai/carpet-fga/src/main/java/carpet/fga/mixin/FillCommandLimitMixin.java").write_text(fill, encoding="utf-8", newline="\n")
Path(r"D:/ai/carpet-fga/src/main/java/carpet/fga/mixin/CarpetFillCommandLimitMixin.java").write_text(carpet_fill, encoding="utf-8", newline="\n")

mix_path = Path(r"D:/ai/carpet-fga/src/main/resources/carpet-fga-addition.mixins.json")
mix = mix_path.read_text(encoding="utf-8")
old = """        //#if MC <= 26.2
        ,\"FillCommandLimitMixin\"
        //#if MC >= 1.19.4
        ,\"FillBiomeCommandLimitMixin\"
        //#endif
        //#endif"""
new = """        //#if MC <= 26.2
        //#if MC < 1.19.4
        ,\"CarpetFillCommandLimitMixin\"
        //#else
        ,\"FillCommandLimitMixin\"
        //#endif
        //#if MC >= 1.19.4
        ,\"FillBiomeCommandLimitMixin\"
        //#endif
        //#endif"""
if old not in mix:
    print("mixins block not found")
    for i, line in enumerate(mix.splitlines(), 1):
        if "Fill" in line or "MC <= 26.2" in line:
            print(i, line)
else:
    mix_path.write_text(mix.replace(old, new), encoding="utf-8", newline="\n")
    print("mixins updated")

print("ok")
