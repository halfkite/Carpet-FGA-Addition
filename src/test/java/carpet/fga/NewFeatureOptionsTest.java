//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NewFeatureOptionsTest {
    @Test void iceProbabilitiesAreExclusiveAndLeaveTheSpecifiedRemainder() {
        int[] counts = new int[3];
        for (int roll = 0; roll < 100; roll++) counts[NewFeatureOptions.iceChoice("30,10", roll)]++;
        assertArrayEquals(new int[]{60, 30, 10}, counts);
        for (int roll = 0; roll < 100; roll++) {
            assertEquals(0, NewFeatureOptions.iceChoice("0,0", roll));
            assertEquals(0, NewFeatureOptions.iceChoice("false", roll));
            assertEquals(1, NewFeatureOptions.iceChoice("100,0", roll));
            assertEquals(2, NewFeatureOptions.iceChoice("0,100", roll));
        }
    }

    @Test void invalidIceValuesCannotReachWorldGeneration() {
        for (String value : new String[]{"-1,0", "0,-1", "101,0", "0,101", "60,60", "1.5,2",
                "30", "30,10,1", "true", "", "2147483648,0", "99999999999999999999,0"}) {
            assertFalse(NewFeatureOptions.validIce(value), value);
        }
        for (String value : new String[]{"false", "0,0", "30,10", "100,0", "0,100", "99,1"}) {
            assertTrue(NewFeatureOptions.validIce(value), value);
        }
    }

    @Test void passengerCapacitySupportsPresetsAndCustomValuesWithoutIntegerOverflow() {
        for (String value : new String[]{"false", "1", "4", "8", "24", "25", "128", "2147483647"}) {
            assertTrue(NewFeatureOptions.validCapacity(value), value);
        }
        for (String value : new String[]{"true", "0", "-1", "1.5", "2147483648", "", "NaN"}) {
            assertFalse(NewFeatureOptions.validCapacity(value), value);
        }
        assertEquals(0, NewFeatureOptions.capacity("false"));
        assertEquals(25, NewFeatureOptions.capacity("25"));
    }

    @Test void flatBedrockOnlyAcceptsTheApprovedOneToFiveLayers() {
        assertEquals(0, NewFeatureOptions.bedrockLayers("false"));
        assertEquals(1, NewFeatureOptions.bedrockLayers("true"));
        for (int i = 1; i <= 5; i++) {
            assertTrue(NewFeatureOptions.validBedrock(Integer.toString(i)));
            assertEquals(i, NewFeatureOptions.bedrockLayers(Integer.toString(i)));
        }
        for (String value : new String[]{"0", "6", "-1", "1.5", "", "2147483648"}) {
            assertFalse(NewFeatureOptions.validBedrock(value), value);
        }
    }
}
//#endif
