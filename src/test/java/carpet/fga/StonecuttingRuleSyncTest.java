//#if MC == 26.3
//$$ package carpet.fga;
//$$
//$$ import net.fabricmc.api.EnvType;
//$$ import net.fabricmc.loader.api.FabricLoader;
//$$ import net.fabricmc.loader.impl.launch.FabricLauncher;
//$$ import net.fabricmc.loader.impl.launch.FabricLauncherBase;
//$$ import net.minecraft.SharedConstants;
//$$ import net.minecraft.core.registries.Registries;
//$$ import net.minecraft.resources.Identifier;
//$$ import net.minecraft.resources.ResourceKey;
//$$ import net.minecraft.server.Bootstrap;
//$$ import net.minecraft.world.item.crafting.RecipeHolder;
//$$ import net.minecraft.world.item.crafting.StonecutterRecipe;
//$$ import net.minecraft.world.item.crafting.Ingredient;
//$$ import net.minecraft.world.item.crafting.SelectableRecipe;
//$$ import net.minecraft.world.item.Items;
//$$ import org.junit.jupiter.api.BeforeAll;
//$$ import org.junit.jupiter.api.Test;
//$$ import java.lang.reflect.Field;
//$$ import java.lang.reflect.Proxy;
//$$ import java.util.List;
//$$ import java.util.Optional;
//$$ import static org.junit.jupiter.api.Assertions.*;
//$$
//$$ final class StonecuttingRuleSyncTest {
//$$     @BeforeAll static void bootstrap() {
//$$         SharedConstants.tryDetectVersion();
//$$         Bootstrap.bootStrap();
//$$     }
//$$
//$$     @Test void integratedServerHonoursRulesInPhysicalClientEnvironment() throws Exception {
//$$         Field launcherField = FabricLauncherBase.class.getDeclaredField("launcher");
//$$         launcherField.setAccessible(true);
//$$         Object previousLauncher = launcherField.get(null);
//$$         boolean previousWood = FGASettings.woodStonecuttingRecipes;
//$$         boolean previousLight = FGASettings.lightSourceStonecuttingRecipes;
//$$         FabricLauncher clientLauncher = (FabricLauncher) Proxy.newProxyInstance(
//$$                 FabricLauncher.class.getClassLoader(), new Class<?>[]{FabricLauncher.class},
//$$                 (proxy, method, args) -> method.getName().equals("getEnvironmentType") ? EnvType.CLIENT : null);
//$$         try {
//$$             launcherField.set(null, clientLauncher);
//$$             assertEquals(EnvType.CLIENT, FabricLoader.getInstance().getEnvironmentType());
//$$             RecipeHolder<StonecutterRecipe> wood = holder("carpet-fga-addition:oak_log_to_oak_planks_from_wood_stonecutting");
//$$             RecipeHolder<StonecutterRecipe> light = holder("carpet-fga-addition:light_level_01_15_from_light_source_stonecutting");
//$$             for (boolean enabled : new boolean[]{false, true, false}) {
//$$                 FGASettings.woodStonecuttingRecipes = enabled;
//$$                 FGASettings.lightSourceStonecuttingRecipes = enabled;
//$$                 assertEquals(!enabled, DeepslateStonecuttingRecipes.isDisabledFgaRecipe(wood));
//$$                 assertEquals(!enabled, DeepslateStonecuttingRecipes.isDisabledFgaRecipe(light));
//$$                 assertFalse(DeepslateStonecuttingRecipes.isDisabledFgaRecipe(holder("minecraft:stone_slab")));
//$$                 assertFalse(DeepslateStonecuttingRecipes.isDisabledFgaRecipe(holder("other:oak_log_to_oak_planks_from_wood_stonecutting")));
//$$             }
//$$             FGASettings.woodStonecuttingRecipes = true;
//$$             FGASettings.lightSourceStonecuttingRecipes = false;
//$$             assertFalse(DeepslateStonecuttingRecipes.isDisabledFgaRecipe(wood));
//$$             assertTrue(DeepslateStonecuttingRecipes.isDisabledFgaRecipe(light));
//$$             FGASettings.woodStonecuttingRecipes = false;
//$$             FGASettings.lightSourceStonecuttingRecipes = true;
//$$             assertTrue(DeepslateStonecuttingRecipes.isDisabledFgaRecipe(wood));
//$$             assertFalse(DeepslateStonecuttingRecipes.isDisabledFgaRecipe(light));
//$$             assertFalse(DeepslateStonecuttingRecipes.isDisabledFgaRecipe(null));
//$$         } finally {
//$$             launcherField.set(null, previousLauncher);
//$$             FGASettings.woodStonecuttingRecipes = previousWood;
//$$             FGASettings.lightSourceStonecuttingRecipes = previousLight;
//$$         }
//$$     }
//$$
//$$     @Test void clientDisplayOnlyRecipesKeepTheServersSelection() {
//$$         var ingredient = Ingredient.of(Items.STONE);
//$$         var entry = new SelectableRecipe.SingleInputEntry<StonecutterRecipe>(ingredient,
//$$                 new SelectableRecipe<>(ingredient.display(), Optional.empty()));
//$$         var recipes = new SelectableRecipe.SingleInputSet<>(List.of(entry));
//$$         assertEquals(List.of(entry), DeepslateStonecuttingRecipes.filter(recipes).entries());
//$$     }
//$$
//$$     private static RecipeHolder<StonecutterRecipe> holder(String id) {
//$$         return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.parse(id)), null);
//$$     }
//$$ }
//#endif
