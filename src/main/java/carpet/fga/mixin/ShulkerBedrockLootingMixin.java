//#if MC == 1.21.1
package carpet.fga.mixin;

import carpet.fga.FGASettings;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class ShulkerBedrockLootingMixin {
    @Inject(method = "dropFromLootTable", at = @At("HEAD"), cancellable = true)
    private void carpetFga$bedrockShulkerShellDrop(DamageSource damageSource, boolean recentlyHit, CallbackInfo ci) {
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
            Holder<Enchantment> lootingEnchantment = serverLevel.registryAccess()
                    .registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.LOOTING);
            looting = EnchantmentHelper.getEnchantmentLevel(lootingEnchantment, attacker);
        }
        int count = 1 + dying.getRandom().nextInt(looting + 1);
        dying.spawnAtLocation(new ItemStack(Items.SHULKER_SHELL, count));
    }
}
//#endif
