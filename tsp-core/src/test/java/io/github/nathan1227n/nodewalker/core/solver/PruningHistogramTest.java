package io.github.nathan1227n.nodewalker.core.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PruningHistogramTest {
    @ParameterizedTest
    @CsvSource({"7, 7, 1", "7, 6, 1", "7, 5, 2", "7, 4, 6", "7, 3, 24", "7, 2, 120", "7, 1, 720"})
    void onePruneAccountsForAllSuffixPermutationsIncludingTheLeaf(int n, int depth, long leaves) {
        PruningHistogram histogram = new PruningHistogram(n);

        histogram.record(depth);

        // Literais independentes: 0!, 1!, 2!, 3!, 4!, 5!, 6!.
        assertEquals(BigInteger.valueOf(leaves), histogram.discardedRoutes());
    }

    @Test
    void mixedDepthsAccumulateMonotonicallyAndDoNotChangeEarlierSnapshots() {
        PruningHistogram histogram = new PruningHistogram(5);
        BigInteger empty = histogram.discardedRoutes();
        histogram.record(5);
        BigInteger leaf = histogram.discardedRoutes();
        histogram.record(3);
        BigInteger internal = histogram.discardedRoutes();
        histogram.record(2);
        histogram.record(3);

        assertEquals(BigInteger.ZERO, empty);
        assertEquals(BigInteger.ONE, leaf);
        assertEquals(BigInteger.valueOf(3), internal);
        assertEquals(BigInteger.valueOf(11), histogram.discardedRoutes());
        assertTrue(empty.compareTo(leaf) < 0);
        assertTrue(leaf.compareTo(internal) < 0);
        assertTrue(internal.compareTo(histogram.discardedRoutes()) < 0);
    }

    @Test
    void zeroCostTwentyFiveCityHistogramUsesBigIntegerBeyondLongWithoutEnumeratingRoutes() {
        PruningHistogram histogram = new PruningHistogram(25);
        BigInteger previous = BigInteger.ZERO;
        for (int depth = 24; depth >= 2; depth--) {
            for (int sibling = 0; sibling < 25 - depth; sibling++) {
                histogram.record(depth);
                BigInteger current = histogram.discardedRoutes();
                assertTrue(current.compareTo(previous) > 0);
                previous = current;
            }
        }
        assertEquals(new BigInteger("620448401733239439359999"), previous);
    }

    @Test
    void counterOverflowPreservesThePreviousExactSnapshotAndCopiesInitialState() {
        long[] initial = {0, Long.MAX_VALUE, 0};
        PruningHistogram histogram = new PruningHistogram(initial);
        initial[1] = 0;
        BigInteger before = histogram.discardedRoutes();

        assertThrows(ArithmeticException.class, () -> histogram.record(2));

        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), before);
        assertEquals(before, histogram.discardedRoutes());
        histogram.record(3);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), histogram.discardedRoutes());
    }

    @Test
    void rejectsInvalidCountsAndDepths() {
        assertThrows(IllegalArgumentException.class, () -> new PruningHistogram(2));
        assertThrows(IllegalArgumentException.class, () -> new PruningHistogram(new long[] {0, 0}));
        assertThrows(IllegalArgumentException.class, () -> new PruningHistogram(new long[] {0, -1, 0}));
        PruningHistogram histogram = new PruningHistogram(3);
        assertThrows(IllegalArgumentException.class, () -> histogram.record(0));
        assertThrows(IllegalArgumentException.class, () -> histogram.record(4));
        assertEquals(BigInteger.ZERO, histogram.discardedRoutes());
    }
}
