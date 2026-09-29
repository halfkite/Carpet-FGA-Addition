//#if MC >= 26.2 && MC <= 26.3
//$$ package carpet.fga;
//$$
//$$ import org.junit.jupiter.api.Test;
//$$
//$$ import static org.junit.jupiter.api.Assertions.assertEquals;
//$$ import static org.junit.jupiter.api.Assertions.assertFalse;
//$$ import static org.junit.jupiter.api.Assertions.assertThrows;
//$$ import static org.junit.jupiter.api.Assertions.assertTrue;
//$$
//$$ class SulfurCubeGrowthTimeTest {
//$$     @Test
//$$     void acceptsOnlyVanillaSentinelOrPositiveSecondsWithinTickRange() {
//$$         assertTrue(SulfurCubeGrowthTime.isValid(-1));
//$$         assertTrue(SulfurCubeGrowthTime.isValid(1));
//$$         assertTrue(SulfurCubeGrowthTime.isValid(SulfurCubeGrowthTime.MAX_SECONDS));
//$$         assertFalse(SulfurCubeGrowthTime.isValid(0));
//$$         assertFalse(SulfurCubeGrowthTime.isValid(-2));
//$$         assertFalse(SulfurCubeGrowthTime.isValid(SulfurCubeGrowthTime.MAX_SECONDS + 1));
//$$     }
//$$
//$$     @Test
//$$     void convertsSecondsToNegativeBabyAgeTicksAndPreservesVanillaSentinel() {
//$$         assertEquals(-24_000, SulfurCubeGrowthTime.resolveBabyStartAge(-1, -24_000));
//$$         assertEquals(-20, SulfurCubeGrowthTime.resolveBabyStartAge(1, -24_000));
//$$         assertEquals(-12_000, SulfurCubeGrowthTime.resolveBabyStartAge(600, -24_000));
//$$         assertEquals(-24_000, SulfurCubeGrowthTime.resolveBabyStartAge(1_200, -24_000));
//$$         assertEquals(-2_147_483_640,
//$$                 SulfurCubeGrowthTime.resolveBabyStartAge(SulfurCubeGrowthTime.MAX_SECONDS, -24_000));
//$$     }
//$$
//$$     @Test
//$$     void rejectsInvalidDurationsBeforeConverting() {
//$$         assertThrows(IllegalArgumentException.class, () -> SulfurCubeGrowthTime.resolveBabyStartAge(0, -24_000));
//$$         assertThrows(IllegalArgumentException.class, () -> SulfurCubeGrowthTime.resolveBabyStartAge(-2, -24_000));
//$$         assertThrows(IllegalArgumentException.class,
//$$                 () -> SulfurCubeGrowthTime.resolveBabyStartAge(SulfurCubeGrowthTime.MAX_SECONDS + 1, -24_000));
//$$     }
//$$ }
//#endif
