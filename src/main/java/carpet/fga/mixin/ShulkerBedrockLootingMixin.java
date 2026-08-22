//#if MC >= 1.20.1 && MC <= 26.2
package carpet.fga.mixin;

import carpet.fga.FGASettings;
//#if MC >= 1.21
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
//#endif
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//#if MC >= 1.21
import net.minecraft.world.item.enchantment.Enchantment;
//#endif
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class ShulkerBedrockLootingMixin {
    //#if MC >= 1.21.3
    //$$ @Inject(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V", at = @At("HEAD"), cancellable = true)
    //$$ private void carpetFga$bedrockShulkerShellDrop(ServerLevel level, DamageSource damageSource,
    //$$                                                   boolean recentlyHit, CallbackInfo ci) {
    //$$     carpetFga$handleDrop(damageSource, ci);
    //$$ }
    //#else
    @Inject(method = "dropFromLootTable(Lnet/minecraft/world/damagesource/DamageSource;Z)V", at = @At("HEAD"), cancellable = true)
    private void carpetFga$bedrockShulkerShellDrop(DamageSource damageSource, boolean recentlyHit, CallbackInfo ci) {
        carpetFga$handleDrop(damageSource, ci);
    }
    //#endif

    private void carpetFga$handleDrop(DamageSource damageSource, CallbackInfo ci) {
        // The doMobLoot gamerule and shouldDropLoot gate run in dropAllDeathLoot before this method.
        LivingEntity dying = (LivingEntity) (Object) this;
        if (!FGASettings.shulkerBedrockLooting || !(dying instanceof Shulker)
                || !(dying.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ci.cancel();
        // Bedrock: a flat 50% chance to drop at all, then 1 to 1+Looting shells uniformly,
        // giving averages 0.50 / 0.75 / 1.00 / 1.25 for Looting 0-3.
        if (dying.getRandom().nextFloat() >= 0.5F) {
            return;
        }
        int looting = 0;
        if (damageSource.getEntity() instanceof LivingEntity attacker) {
            //#if MC >= 1.21
            Holder<Enchantment> lootingEnchantment = serverLevel.registryAccess()
                    //#if MC >= 1.21.3
                    //$$ .lookupOrThrow(Registries.ENCHANTMENT)
                    //$$ .getOrThrow(Enchantments.LOOTING);
                    //#else
                    .registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.LOOTING);
                    //#endif
            looting = EnchantmentHelper.getEnchantmentLevel(lootingEnchantment, attacker);
            //#elseif MC >= 1.20.5
            //$$ looting = EnchantmentHelper.getEnchantmentLevel(Enchantments.LOOTING, attacker);
            //#else
//$$             looting = EnchantmentHelper.getEnchantmentLevel(Enchantments.MOB_LOOTING, attacker);
            //#endif
        }
        int count = 1 + dying.getRandom().nextInt(looting + 1);
        //#if MC >= 1.21.2
        //$$ dying.spawnAtLocation(serverLevel, new ItemStack(Items.SHULKER_SHELL, count));
        //#else
        dying.spawnAtLocation(new ItemStack(Items.SHULKER_SHELL, count));
        //#endif
    }
}
//#endif
