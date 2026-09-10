//#if MC >= 1.21
package carpet.fga;

import java.util.ArrayList;
import java.util.List;

/** Pure container-source allocation for one full-shulker stonecutter transaction. */
final class StonecutterBoxAllocation {
    static final int MAX_OUTPUT_BOXES = 4096;

    private StonecutterBoxAllocation() {
    }

    static Plan allocate(int outputBoxes, boolean retainsSource, int availableEmptyBoxes) {
        if (outputBoxes <= 0 || outputBoxes > MAX_OUTPUT_BOXES || availableEmptyBoxes < 0) {
            return null;
        }
        int retainedSourceBoxes = retainsSource ? 1 : 0;
        int requiredEmptyBoxes;
        try {
            requiredEmptyBoxes = Math.subtractExact(outputBoxes, 1 - retainedSourceBoxes);
        } catch (ArithmeticException exception) {
            return null;
        }
        if (requiredEmptyBoxes < 0 || requiredEmptyBoxes > availableEmptyBoxes) return null;

        List<Source> outputSources = new ArrayList<>(outputBoxes);
        int emptyIndex = 0;
        if (!retainsSource) outputSources.add(Source.sourceBox());
        while (outputSources.size() < outputBoxes) {
            outputSources.add(Source.emptyBox(emptyIndex++));
        }
        if (emptyIndex != requiredEmptyBoxes
                || 1 + requiredEmptyBoxes != outputBoxes + retainedSourceBoxes) return null;
        return new Plan(List.copyOf(outputSources), retainsSource, requiredEmptyBoxes);
    }

    record Plan(List<Source> outputSources, boolean retainsSource, int requiredEmptyBoxes) {
    }

    record Source(Kind kind, int emptyBoxIndex) {
        private static Source sourceBox() {
            return new Source(Kind.SOURCE, -1);
        }

        private static Source emptyBox(int index) {
            return new Source(Kind.EMPTY, index);
        }
    }

    enum Kind { SOURCE, EMPTY }
}
//#endif
