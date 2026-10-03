//#if MC == 26.3
//$$ package carpet.fga;
//$$
//$$ import carpet.fga.mixin.*;
//$$ import com.google.gson.JsonParser;
//$$ import net.minecraft.world.entity.LivingEntity;
//$$ import net.minecraft.world.entity.Entity;
//$$ import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
//$$ import net.minecraft.world.entity.vehicle.minecart.Minecart;
//$$ import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
//$$ import net.minecraft.world.level.block.entity.BarrelBlockEntity;
//$$ import net.minecraft.server.level.ServerPlayer;
//$$ import net.minecraft.server.network.ServerGamePacketListenerImpl;
//$$ import org.junit.jupiter.api.Test;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import java.util.Arrays;
//$$ import java.io.InputStreamReader;
//$$ import java.nio.charset.StandardCharsets;
//$$ import java.util.Set;
//$$ import java.util.stream.Collectors;
//$$ import static org.junit.jupiter.api.Assertions.*;
//$$
//$$ final class NewFeatureMixinTargetTest {
//$$     @Test void newInjectionSignaturesExistInTheBaselineGame() throws Exception {
//$$         check(FastEatingMixin.class, LivingEntity.class);
//$$         check(VehicleNoCrammingMixin.class, LivingEntity.class);
//$$         check(PlayerVehicleInputMixin.class, ServerPlayer.class);
//$$         check(PlayerBoatFeaturesMixin.class, AbstractBoat.class);
//$$         check(PlayerVehicleCapacityMixin.class, Entity.class);
//$$         check(PlayerMinecartInteractionMixin.class, Minecart.class);
//$$         check(DoubleBarrelCapacityMixin.class, BarrelBlockEntity.class);
//$$         check(AbnormalDisconnectNoticeMixin.class, ServerGamePacketListenerImpl.class);
//$$         check(FlatBedrockGenerationMixin.class, NoiseBasedChunkGenerator.class);
//$$     }
//$$
//$$     @Test void allBaselineHooksRemainRequiredAndPackaged() throws Exception {
//$$         try (var stream = getClass().getResourceAsStream("/carpet-fga-addition.mixins.json")) {
//$$             assertNotNull(stream);
//$$             var config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
//$$             assertTrue(config.get("required").getAsBoolean());
//$$             assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
//$$             Set<String> mixins = java.util.stream.StreamSupport.stream(config.getAsJsonArray("mixins").spliterator(), false)
//$$                     .map(element -> element.getAsString()).collect(Collectors.toSet());
//$$             assertTrue(mixins.containsAll(Set.of("NaturalWeatherFeaturesMixin", "NaturalWorldgenFeaturesMixin",
//$$                     "FastEatingMixin", "DoubleBarrelCapacityMixin", "AbnormalDisconnectNoticeMixin",
//$$                     "FlatBedrockGenerationMixin", "PlayerVehicleInputMixin", "PlayerBoatFeaturesMixin",
//$$                     "PlayerVehicleCapacityMixin", "PlayerMinecartInteractionMixin", "PlayerMinecartJumpMixin",
//$$                     "VehicleNoCrammingMixin")));
//$$         }
//$$     }
//$$
//$$     private static void check(Class<?> mixin, Class<?> target) throws Exception {
//$$         for (var method : mixin.getDeclaredMethods()) {
//$$             Inject annotation = method.getAnnotation(Inject.class);
//$$             if (annotation == null) continue;
//$$             Class<?>[] args = method.getParameterTypes();
//$$             for (String name : annotation.method()) {
//$$                 assertNotNull(target.getDeclaredMethod(name, Arrays.copyOf(args, args.length - 1)));
//$$             }
//$$         }
//$$     }
//$$ }
//#endif
