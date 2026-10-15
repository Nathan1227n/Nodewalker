package io.github.nathan1227n.nodewalker.core.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nathan1227n.nodewalker.core.ContractTestProblems;
import io.github.nathan1227n.nodewalker.core.model.TspRoute;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SolverRunMetricsTest {
    @Test
    void observedCounterOverflowCannotWrapAndErrorPreservesExactSnapshot() {
        SolverRunMetrics metrics = new SolverRunMetrics(() -> 0, Long.MAX_VALUE, Long.MAX_VALUE);
        metrics.start();
        SolverMetrics before = metrics.snapshot(Optional.of(BigInteger.ZERO));

        ArithmeticException failure = assertThrows(ArithmeticException.class, metrics::visit);
        assertThrows(ArithmeticException.class, metrics::nextEvaluationCount);
        assertEquals(before, metrics.snapshot(Optional.of(BigInteger.ZERO)));

        TspResult result = metrics.errorResult("teste", ContractTestProblems.triangle(),
                new TspRoute(List.of(0, 1, 2, 0)), 45, null, failure);

        assertEquals(SolverStatus.ERRO, result.status());
        assertFalse(result.optimalityGuaranteed());
        assertEquals(45, result.cost().orElseThrow());
        assertEquals(Long.MAX_VALUE, result.metrics().visitedNodes().orElseThrow());
        assertEquals(Long.MAX_VALUE, result.metrics().completeRoutesEvaluated().orElseThrow());
        assertEquals(0, result.metrics().elapsedNanos().orElseThrow());
        assertTrue(result.reason().orElseThrow().contains("ArithmeticException"));
        assertTrue(before.elapsedNanos().isEmpty());
    }

    @Test
    void failedPruningCounterDoesNotInventAnAdditionalDiscardedLeaf() {
        SolverRunMetrics metrics = new SolverRunMetrics(() -> 0);
        PruningHistogram histogram = new PruningHistogram(new long[] {0, 0, Long.MAX_VALUE});
        ArithmeticException failure = assertThrows(ArithmeticException.class, () -> histogram.record(3));

        TspResult result = metrics.errorResult("teste", ContractTestProblems.triangle(), null, 0, histogram, failure);

        assertEquals(SolverStatus.ERRO, result.status());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), result.metrics().discardedRoutes().orElseThrow());
        assertEquals(0, result.metrics().completeRoutesEvaluated().orElseThrow());
    }

    @Test
    void reservesEvaluationsWithoutPublishingUntilTheCycleIsCalculated() {
        SolverRunMetrics metrics = new SolverRunMetrics(() -> 0, Long.MAX_VALUE - 1, Long.MAX_VALUE - 1);
        SolverMetrics before = metrics.snapshot(Optional.empty());

        metrics.visit();
        long reservation = metrics.nextEvaluationCount();
        assertEquals(Long.MAX_VALUE - 1, metrics.snapshot(Optional.empty()).completeRoutesEvaluated().orElseThrow());
        metrics.completeEvaluation(reservation);

        assertEquals(Long.MAX_VALUE, metrics.snapshot(Optional.empty()).completeRoutesEvaluated().orElseThrow());
        assertEquals(Long.MAX_VALUE, metrics.snapshot(Optional.empty()).visitedNodes().orElseThrow());
        assertEquals(Long.MAX_VALUE - 1, before.completeRoutesEvaluated().orElseThrow());
        assertEquals(Long.MAX_VALUE - 1, before.visitedNodes().orElseThrow());
    }

    @Test
    void checksCoverageBeforeSuccessAndPreservesValidIncumbentOnMismatch() {
        SolverRunMetrics metrics = new SolverRunMetrics(() -> 0, 1, 3);
        metrics.requireCompleteCoverage(3, BigInteger.ONE);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> metrics.requireCompleteCoverage(3, BigInteger.ZERO));
        TspResult result = metrics.errorResult("teste", ContractTestProblems.triangle(),
                new TspRoute(List.of(0, 1, 2, 0)), 45, null, failure);

        assertEquals(SolverStatus.ERRO, result.status());
        assertEquals(45, result.cost().orElseThrow());
        assertTrue(result.reason().orElseThrow().contains("não cobrem"));
    }

    @Test
    void invalidIncumbentCannotReplaceTheOriginalDiagnosisOrBecomeSuccess() {
        SolverRunMetrics metrics = new SolverRunMetrics(() -> 0);

        TspResult result = metrics.errorResult("teste", ContractTestProblems.triangle(),
                new TspRoute(List.of(0, 1, 2, 0)), 99, null, new IllegalStateException("Falha original"));

        assertEquals(SolverStatus.ERRO, result.status());
        assertTrue(result.route().isEmpty());
        assertTrue(result.cost().isEmpty());
        assertTrue(result.reason().orElseThrow().contains("Falha original"));
        assertTrue(result.reason().orElseThrow().contains("validar incumbente"));
    }

    @Test
    void rejectsNegativeInitialObservedCounts() {
        assertThrows(IllegalArgumentException.class, () -> new SolverRunMetrics(() -> 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SolverRunMetrics(() -> 0, 0, -1));
    }
}
