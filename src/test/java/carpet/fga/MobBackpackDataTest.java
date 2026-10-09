package carpet.fga;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

//#if MC >= 26.3
//$$ import net.minecraft.SharedConstants;
//$$ import net.minecraft.core.HolderLookup;
//$$ import net.minecraft.core.registries.BuiltInRegistries;
//$$ import net.minecraft.data.registries.VanillaRegistries;
//$$ import net.minecraft.server.Bootstrap;
//$$ import net.minecraft.util.ProblemReporter;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.item.Items;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.core.component.DataComponents;
//$$ import net.minecraft.world.level.storage.TagValueInput;
//$$ import net.minecraft.world.level.storage.TagValueOutput;
//$$ import org.junit.jupiter.api.BeforeAll;
//#endif

final class MobBackpackDataTest {
    @Test void eightStorageSlotsDoNotOverlapTheSixEquipmentSlots() {
        Set<Integer> backpack = java.util.stream.IntStream.range(0, MobEquipmentContainer.CONTAINER_SIZE)
                .filter(MobEquipmentContainer::isBackpackIndex).boxed().collect(Collectors.toSet());
        assertEquals(8, backpack.size());
        assertTrue(backpack.stream().noneMatch(MobEquipmentContainer::isEquipmentIndex));
        assertTrue(backpack.stream().allMatch(index -> index >= 0 && index < MobEquipmentContainer.CONTAINER_SIZE));
    }

//#if MC >= 26.3
//$$     private static HolderLookup.Provider registry;
//$$
//$$     @BeforeAll static void bootstrap() {
//$$         SharedConstants.tryDetectVersion();
//$$         Bootstrap.bootStrap();
//$$         registry = VanillaRegistries.createWorldLookup();
//$$         BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registry).forEach(pending -> pending.apply());
//$$     }
//$$
//$$     @Test void backpackStacksAndComponentsSurviveEntityNbtRoundTrip() {
//$$         MobBackpackData original = new MobBackpackData();
//$$         ItemStack named = new ItemStack(Items.DIAMOND, 32);
//$$         named.set(DataComponents.CUSTOM_NAME, Component.literal("stored safely"));
//$$         original.items().set(0, named);
//$$         original.items().set(MobBackpackData.SIZE - 1, new ItemStack(Items.ENDER_PEARL, 7));
//$$
//$$         MobBackpackData restored = new MobBackpackData();
//$$         TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registry);
//$$         original.save(output);
//$$         restored.load(TagValueInput.create(ProblemReporter.DISCARDING, registry, output.buildResult()));
//$$
//$$         assertEquals(32, restored.items().get(0).getCount());
//$$         assertEquals("stored safely", restored.items().get(0).getHoverName().getString());
//$$         assertTrue(restored.items().get(0).is(Items.DIAMOND));
//$$         assertEquals(7, restored.items().get(MobBackpackData.SIZE - 1).getCount());
//$$         assertTrue(restored.items().get(MobBackpackData.SIZE - 1).is(Items.ENDER_PEARL));
//$$     }
//#endif
}
