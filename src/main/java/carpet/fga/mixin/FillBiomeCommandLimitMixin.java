//#if MC >= 1.19.4
//#if MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.server.commands.FillBiomeCommand;
//#if MC >= 1.21.11
//$$ import net.minecraft.world.level.gamerules.GameRule;
//$$ import net.minecraft.world.level.gamerules.GameRules;
//#else
import net.minecraft.world.level.GameRules;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FillBiomeCommand.class)
public abstract class FillBiomeCommandLimitMixin {
    //#if MC >= 1.21.11
    //$$ @Redirect(
    //$$         method = "fill(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Holder;Ljava/util/function/Predicate;Ljava/util/function/Consumer;)Lcom/mojang/datafixers/util/Either;",
    //$$         at = @At(
    //$$                 value = "INVOKE",
    //$$                 target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"
    //$$         ),
    //$$         require = 0
    //$$ )
    //$$ private static Object carpetFga$unlimitedFillBiomeVolume(GameRules rules, GameRule<?> rule) {
    //$$     return FGASettings.unlimitedFillCommands ? Integer.MAX_VALUE : rules.get(rule);
    //$$ }
    //#elseif MC >= 1.20.2
    @Redirect(
            method = "fill(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Holder;Ljava/util/function/Predicate;Ljava/util/function/Consumer;)Lcom/mojang/datafixers/util/Either;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/GameRules;getInt(Lnet/minecraft/world/level/GameRules$Key;)I"
            ),
            require = 0
    )
    private static int carpetFga$unlimitedFillBiomeVolume(GameRules rules,
                                                              GameRules.Key<GameRules.IntegerValue> key) {
        return FGASettings.unlimitedFillCommands ? Integer.MAX_VALUE : rules.getInt(key);
    }
    //#else
    //$$ @Redirect(
    //$$         method = "fill(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Holder$Reference;Ljava/util/function/Predicate;)I",
    //$$         at = @At(
    //$$                 value = "INVOKE",
    //$$                 target = "Lnet/minecraft/world/level/GameRules;getInt(Lnet/minecraft/world/level/GameRules$Key;)I"
    //$$         ),
    //$$         require = 0
    //$$ )
    //$$ private static int carpetFga$unlimitedFillBiomeVolume(GameRules rules,
    //$$                                                           GameRules.Key<GameRules.IntegerValue> key) {
    //$$     return FGASettings.unlimitedFillCommands ? Integer.MAX_VALUE : rules.getInt(key);
    //$$ }
    //#endif
}
//#endif
//#endif
