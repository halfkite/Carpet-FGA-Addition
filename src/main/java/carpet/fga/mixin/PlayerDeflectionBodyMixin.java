package carpet.fga.mixin;

//#if MC >= 1.21.10 && MC <= 26.3
//$$ import carpet.fga.PossessionOwners;
//$$ import net.minecraft.world.entity.player.Player;
//$$ import net.minecraft.world.level.entity.UniquelyIdentifyable;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.ModifyArg;
//$$
//$$ @Mixin(Player.class)
//$$ public abstract class PlayerDeflectionBodyMixin {
//$$     @ModifyArg(
//#if MC >= 1.21.11
//$$             method = "deflectProjectile",
//#else
//$$             method = "attack",
//#endif
//$$             at = @At(
//$$                     value = "INVOKE",
//$$                     target = "Lnet/minecraft/world/entity/EntityReference;of(Lnet/minecraft/world/level/entity/UniquelyIdentifyable;)Lnet/minecraft/world/entity/EntityReference;"
//$$             ),
//$$             index = 0
//$$     )
//$$     private UniquelyIdentifyable fga$deflectCounterpart(UniquelyIdentifyable actor) {
//$$         return actor instanceof Player player ? PossessionOwners.counterpart(player) : actor;
//$$     }
//$$ }
//#endif
