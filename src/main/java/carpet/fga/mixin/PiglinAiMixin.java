package carpet.fga.mixin;

import carpet.fga.FGASettings;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

@Mixin(PiglinAi.class)
public abstract class PiglinAiMixin {
    private static final int MAX_REROLLS = 4096;
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-fga-addition/piglin-barter");

    @Redirect(
        method = "getBarterResponseItems",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"
        )
    )
    private static ObjectArrayList<ItemStack> carpetFga$excludeBarterItems(
            LootTable lootTable, LootParams lootParams) {
        Set<ResourceLocation> exclusions = FGASettings.getPiglinBarterItemExclusions();
        if (exclusions.isEmpty()) {
            return lootTable.getRandomItems(lootParams);
        }

        for (int attempt = 0; attempt < MAX_REROLLS; attempt++) {
            ObjectArrayList<ItemStack> result = lootTable.getRandomItems(lootParams);
            boolean allowed = result.stream().allMatch(stack ->
                    !exclusions.contains(BuiltInRegistries.ITEM.getKey(stack.getItem())));
            if (allowed) {
                return result;
            }
        }

        LOGGER.warn("猪灵交易连续 {} 次未抽到允许物品，请检查交易战利品表和排除规则", MAX_REROLLS);
        return new ObjectArrayList<>();
    }
}
