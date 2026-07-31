//#if MC >= 1.19.4
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
    @ModifyVariable(method = "fillBlocks", at = @At(value = "STORE"), index = 6, require = 0)
    private static int carpetFga$unlimitedFillVolume(int effectiveLimit) {
        return FGASettings.unlimitedFillCommands ? Integer.MAX_VALUE : effectiveLimit;
    }
    //#elseif MC >= 1.21.11
    //$$ @Redirect(
    //$$         method = "fillBlocks",
    //$$         at = @At(
    //$$                 value = "INVOKE",
    //$$                 target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"
    //$$         ),
    //$$         require = 0
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
    //$$         ),
    //$$         require = 0
    //$$ )
    //$$ private static int carpetFga$unlimitedFillVolume(GameRules rules,
    //$$                                                    GameRules.Key<GameRules.IntegerValue> key) {
    //$$     return FGASettings.unlimitedFillCommands ? Integer.MAX_VALUE : rules.getInt(key);
    //$$ }
    //#endif
}
//#endif
//#endif
