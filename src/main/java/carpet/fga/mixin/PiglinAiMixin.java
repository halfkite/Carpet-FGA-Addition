package carpet.fga.mixin;

import carpet.fga.FGASettings;
//#if MC >= 1.19.3
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
//#else
//$$ import net.minecraft.core.Registry;
//#endif
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
//#if MC >= 1.20
import net.minecraft.world.level.storage.loot.LootParams;
//#else
//$$ import net.minecraft.world.level.storage.loot.LootContext;
//#endif
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
            //#if MC >= 1.20
            target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"
            //#else
            //$$ target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Ljava/util/List;"
            //#endif
        )
    )
    private static
            //#if MC >= 1.20
            ObjectArrayList<ItemStack>
            //#else
            //$$ java.util.List<ItemStack>
            //#endif
            carpetFga$excludeBarterItems(LootTable lootTable,
                                         //#if MC >= 1.20
                                         LootParams lootParams
                                         //#else
                                         //$$ LootContext lootParams
                                         //#endif
    ) {
        Set<ResourceLocation> exclusions = FGASettings.getPiglinBarterItemExclusions();
        if (exclusions.isEmpty()) {
            return lootTable.getRandomItems(lootParams);
        }

        for (int attempt = 0; attempt < MAX_REROLLS; attempt++) {
            //#if MC >= 1.20
            ObjectArrayList<ItemStack> result = lootTable.getRandomItems(lootParams);
            //#else
            //$$ java.util.List<ItemStack> result = lootTable.getRandomItems(lootParams);
            //#endif
            boolean allowed = result.stream().allMatch(stack ->
                    !exclusions.contains(
                            //#if MC >= 1.19.3
                            BuiltInRegistries.ITEM.getKey(stack.getItem())
                            //#else
                            //$$ Registry.ITEM.getKey(stack.getItem())
                            //#endif
                    ));
            if (allowed) {
                return result;
            }
        }

        LOGGER.warn("猪灵交易连续 {} 次未抽到允许物品，请检查交易战利品表和排除规则", MAX_REROLLS);
        //#if MC >= 1.20
        return new ObjectArrayList<>();
        //#else
        //$$ return java.util.List.of();
        //#endif
    }
}
