//#if MC >= 26.1
//$$ package carpet.fga.mixin;

//$$ import carpet.fga.ModernVillagerTradeManager;
//$$ import net.minecraft.core.Holder;
//$$ import net.minecraft.world.entity.npc.villager.Villager;
//$$ import net.minecraft.world.entity.npc.villager.VillagerData;
//$$ import net.minecraft.world.entity.npc.villager.VillagerProfession;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//$$ @Mixin(Villager.class)
//$$ public abstract class ModernVillagerProfessionMixin {
//$$     @Inject(method = "setVillagerData", at = @At("HEAD"))
//$$     private void carpetFga$captureProfession(VillagerData data, CallbackInfo ci) {
//$$         Villager villager = (Villager) (Object) this;
//$$         ModernVillagerTradeManager.professionChanged(villager,
//$$                 villager.getVillagerData().profession(), data.profession());
//$$     }
//$$ }
//#endif
