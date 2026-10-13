package io.github.nathan1227n.nodewalker.core.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SolverMetricsTest {
    @Test
    void distinguishesUnreportedMetricsFromReportedZero() {
        SolverMetrics empty = SolverMetrics.empty();
        SolverMetrics zero = new SolverMetrics(OptionalLong.of(0), OptionalLong.of(0),
                OptionalLong.of(0), Optional.of(BigInteger.ZERO));

        assertTrue(empty.elapsedNanos().isEmpty());
        assertTrue(empty.completeRoutesEvaluated().isEmpty());
        assertTrue(empty.visitedNodes().isEmpty());
        assertTrue(empty.discardedRoutes().isEmpty());
        assertNotEquals(empty, zero);
        assertEquals(0L, zero.elapsedNanos().orElseThrow());
        assertEquals(0L, zero.completeRoutesEvaluated().orElseThrow());
        assertEquals(0L, zero.visitedNodes().orElseThrow());
        assertEquals(BigInteger.ZERO, zero.discardedRoutes().orElseThrow());
    }

    @Test
    void preservesReportedSnapshotValuesByValue() {
        SolverMetrics metrics = new SolverMetrics(OptionalLong.of(17), OptionalLong.of(6),
                OptionalLong.of(10), Optional.of(BigInteger.valueOf(24)));

        assertEquals(17L, metrics.elapsedNanos().orElseThrow());
        assertEquals(6L, metrics.completeRoutesEvaluated().orElseThrow());
        assertEquals(10L, metrics.visitedNodes().orElseThrow());
        assertEquals(BigInteger.valueOf(24), metrics.discardedRoutes().orElseThrow());
        assertEquals(metrics, new SolverMetrics(OptionalLong.of(17), OptionalLong.of(6),
                OptionalLong.of(10), Optional.of(BigInteger.valueOf(24))));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void eachMetricCanBeReportedIndependently(int field) {
        SolverMetrics metrics = new SolverMetrics(field == 0 ? OptionalLong.of(1) : OptionalLong.empty(),
                field == 1 ? OptionalLong.of(1) : OptionalLong.empty(),
                field == 2 ? OptionalLong.of(1) : OptionalLong.empty(),
                field == 3 ? Optional.of(BigInteger.ONE) : Optional.empty());

        assertEquals(field == 0, metrics.elapsedNanos().isPresent());
        assertEquals(field == 1, metrics.completeRoutesEvaluated().isPresent());
        assertEquals(field == 2, metrics.visitedNodes().isPresent());
        assertEquals(field == 3, metrics.discardedRoutes().isPresent());
    }

    @ParameterizedTest
    @CsvSource({"0, -1", "0, -9223372036854775808", "1, -1", "1, -9223372036854775808",
            "2, -1", "2, -9223372036854775808"})
    void rejectsNegativeLongMetrics(int field, long value) {
        assertThrows(IllegalArgumentException.class, () -> new SolverMetrics(
                field == 0 ? OptionalLong.of(value) : OptionalLong.empty(),
                field == 1 ? OptionalLong.of(value) : OptionalLong.empty(),
                field == 2 ? OptionalLong.of(value) : OptionalLong.empty(), Optional.empty()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "-9223372036854775808", "-18446744073709551616"})
    void rejectsNegativeDiscardedLeafCounts(String value) {
        assertThrows(IllegalArgumentException.class, () -> new SolverMetrics(OptionalLong.empty(),
                OptionalLong.empty(), OptionalLong.empty(), Optional.of(new BigInteger(value))));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void rejectsNullOptionalFields(int field) {
        assertThrows(NullPointerException.class, () -> new SolverMetrics(
                field == 0 ? null : OptionalLong.empty(), field == 1 ? null : OptionalLong.empty(),
                field == 2 ? null : OptionalLong.empty(), field == 3 ? null : Optional.empty()));
    }

    @Test
    void supportsMaximumObservedCountersAndArbitraryPrecisionDiscardedLeaves() {
        BigInteger leaves = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        SolverMetrics metrics = new SolverMetrics(OptionalLong.of(Long.MAX_VALUE), OptionalLong.of(Long.MAX_VALUE),
                OptionalLong.of(Long.MAX_VALUE), Optional.of(leaves));

        assertEquals(Long.MAX_VALUE, metrics.completeRoutesEvaluated().orElseThrow());
        assertEquals(Long.MAX_VALUE, metrics.visitedNodes().orElseThrow());
        assertEquals(leaves, metrics.discardedRoutes().orElseThrow());
    }
}
