package carpet.fga;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StonecutterBoxAllocationTest {
    @Test
    void sourceBacksTheOnlyOutputWhenFullyConsumed() {
        StonecutterBoxAllocation.Plan plan = requirePlan(1, false, 0);

        assertEquals(0, plan.requiredEmptyBoxes());
        assertEquals(StonecutterBoxAllocation.Kind.SOURCE, plan.outputSources().get(0).kind());
        assertConserved(plan);
    }

    @Test
    void additionalOutputsUseDistinctEmptyBoxes() {
        StonecutterBoxAllocation.Plan plan = requirePlan(4, false, 3);

        assertEquals(3, plan.requiredEmptyBoxes());
        assertEquals(StonecutterBoxAllocation.Kind.SOURCE, plan.outputSources().get(0).kind());
        assertDistinctEmptySources(plan.outputSources().subList(1, 4), 3);
        assertConserved(plan);
    }

    @Test
    void retainedSourceForcesEveryOutputToUseAnEmptyBox() {
        StonecutterBoxAllocation.Plan plan = requirePlan(3, true, 3);

        assertEquals(3, plan.requiredEmptyBoxes());
        assertDistinctEmptySources(plan.outputSources(), 3);
        assertConserved(plan);
    }

    @Test
    void oneOutputWithRemainderStillNeedsOneEmptyBox() {
        StonecutterBoxAllocation.Plan plan = requirePlan(1, true, 1);

        assertEquals(1, plan.requiredEmptyBoxes());
        assertDistinctEmptySources(plan.outputSources(), 1);
        assertConserved(plan);
    }

    @Test
    void missingEmptyBoxesBlocksAllocation() {
        assertNull(StonecutterBoxAllocation.allocate(2, false, 0));
        assertNull(StonecutterBoxAllocation.allocate(1, true, 0));
        assertNull(StonecutterBoxAllocation.allocate(4, true, 3));
    }

    @Test
    void rejectsInvalidCounts() {
        assertNull(StonecutterBoxAllocation.allocate(0, false, 0));
        assertNull(StonecutterBoxAllocation.allocate(-1, false, 0));
        assertNull(StonecutterBoxAllocation.allocate(1, false, -1));
        assertNull(StonecutterBoxAllocation.allocate(
                StonecutterBoxAllocation.MAX_OUTPUT_BOXES + 1, false, Integer.MAX_VALUE));
        assertNull(StonecutterBoxAllocation.allocate(Integer.MAX_VALUE, true, Integer.MAX_VALUE));
    }

    @Test
    void availableBoxesBeyondTheRequirementAreNotAllocated() {
        StonecutterBoxAllocation.Plan plan = requirePlan(2, false, 10);

        assertEquals(1, plan.requiredEmptyBoxes());
        assertEquals(List.of(0), plan.outputSources().stream()
                .filter(source -> source.kind() == StonecutterBoxAllocation.Kind.EMPTY)
                .map(StonecutterBoxAllocation.Source::emptyBoxIndex)
                .toList());
        assertConserved(plan);
    }

    private static StonecutterBoxAllocation.Plan requirePlan(int outputs, boolean retainsSource,
                                                              int availableEmptyBoxes) {
        StonecutterBoxAllocation.Plan plan = StonecutterBoxAllocation.allocate(
                outputs, retainsSource, availableEmptyBoxes);
        assertNotNull(plan);
        return plan;
    }

    private static void assertDistinctEmptySources(List<StonecutterBoxAllocation.Source> sources,
                                                   int expected) {
        assertTrue(sources.stream().allMatch(
                source -> source.kind() == StonecutterBoxAllocation.Kind.EMPTY));
        Set<Integer> indices = new HashSet<>();
        for (StonecutterBoxAllocation.Source source : sources) indices.add(source.emptyBoxIndex());
        assertEquals(expected, indices.size());
    }

    private static void assertConserved(StonecutterBoxAllocation.Plan plan) {
        int retained = plan.retainsSource() ? 1 : 0;
        assertEquals(1 + plan.requiredEmptyBoxes(), plan.outputSources().size() + retained);
    }
}
