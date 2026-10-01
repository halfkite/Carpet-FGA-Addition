//#if MC >= 26.2 && MC <= 26.3
//$$ package carpet.fga;
//$$
//$$ public final class SulfurCubeGrowthTime {
//$$     public static final int TICKS_PER_SECOND = 20;
//$$     public static final int MAX_SECONDS = Integer.MAX_VALUE / TICKS_PER_SECOND;
//$$
//$$     private SulfurCubeGrowthTime() {
//$$     }
//$$
//$$     public static boolean isValid(int seconds) {
//$$         return seconds == -1 || seconds >= 1 && seconds <= MAX_SECONDS;
//$$     }
//$$
//$$     public static int resolveBabyStartAge(int seconds, int vanillaAge) {
//$$         if (seconds == -1) return vanillaAge;
//$$         if (!isValid(seconds)) {
//$$             throw new IllegalArgumentException("Sulfur Cube growth time must be -1 or a positive number of seconds");
//$$         }
//$$         return -seconds * TICKS_PER_SECOND;
//$$     }
//$$ }
//#endif
