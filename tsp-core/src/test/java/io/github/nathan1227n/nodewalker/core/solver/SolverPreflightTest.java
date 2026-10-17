package io.github.nathan1227n.nodewalker.core.solver;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nathan1227n.nodewalker.core.SearchSpace;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class SolverPreflightTest {
    @ParameterizedTest
    @ValueSource(ints = {3, 4, 10, 25})
    void reportsExactSpaceAndEffectiveOptionsWhenSmall(int n) {
        var options = SolverExecutionOptions.limits(300, 1000);
        var report = SolverPreflight.analyze(SolverAlgorithm.BACKTRACKING, n, options);
        assertEquals(n, report.cityCount());
        assertEquals(0, report.originIndex());
        assertEquals(SolverAlgorithm.BACKTRACKING, report.algorithm());
        assertSame(options, report.options());
        assertTrue(report.exactSearchSpace());
        assertEquals(SearchSpace.completeRoutes(n), report.theoreticalRoutes());
        assertTrue(report.executionAllowed());
        assertTrue(report.warnings().getFirst().contains("fatorial"));
        assertThrows(UnsupportedOperationException.class, () -> report.warnings().clear());
    }

    @Test
    void distinguishesAnExactBoundaryFromItsStrictLowerBound() {
        int firstInexact = 3;
        while (SolverPreflight.analyze(SolverAlgorithm.BRUTE_FORCE, firstInexact,
                SolverExecutionOptions.nodeLimit(0)).exactSearchSpace()) {
            firstInexact++;
        }
        var report = SolverPreflight.analyze(SolverAlgorithm.BRUTE_FORCE, firstInexact,
                SolverExecutionOptions.nodeLimit(0));
        assertEquals(SearchSpace.completeRoutes(firstInexact - 1), report.theoreticalRoutes());
        assertTrue(report.theoreticalRoutes().compareTo(SearchSpace.completeRoutes(firstInexact)) < 0);
        assertTrue(report.theoreticalRoutes().bitLength() <= 256);
    }

    @ParameterizedTest
    @EnumSource(SolverAlgorithm.class)
    void refusesImpracticalUnboundedRequestsWithoutComputingGiantFactorials(SolverAlgorithm algorithm) {
        var report = SolverPreflight.analyze(algorithm, Integer.MAX_VALUE, SolverExecutionOptions.unlimited());
        assertFalse(report.exactSearchSpace());
        assertTrue(report.theoreticalRoutes().bitLength() <= 256);
        assertTrue(report.theoreticalRoutes().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
        assertTrue(report.requiresConfirmation());
        assertFalse(report.executionAllowed());
        String reason = assertThrows(IllegalArgumentException.class, report::requireAllowed).getMessage();
        assertTrue(reason.contains(">="));
        assertTrue(reason.contains("espaço exato não calculado"));
    }

    @ParameterizedTest
    @EnumSource(SolverAlgorithm.class)
    void doesNotImposeAMaximumNOnBoundedExecution(SolverAlgorithm algorithm) {
        var report = SolverPreflight.analyze(algorithm, Integer.MAX_VALUE, SolverExecutionOptions.nodeLimit(1));
        assertTrue(report.executionAllowed());
        assertTrue(report.outsideRecommendedRange());
        assertDoesNotThrow(report::requireAllowed);
    }

    @Test
    void appliesAnInclusiveMeasuredRecommendationPerAlgorithm() {
        var options = SolverExecutionOptions.unlimited().withPolicy(new SolverExecutionPolicy(4, 6, "teste"));
        assertTrue(SolverPreflight.analyze(SolverAlgorithm.BRUTE_FORCE, 4, options).executionAllowed());
        assertFalse(SolverPreflight.analyze(SolverAlgorithm.BRUTE_FORCE, 5, options).executionAllowed());
        assertTrue(SolverPreflight.analyze(SolverAlgorithm.BACKTRACKING, 6, options).executionAllowed());
        assertFalse(SolverPreflight.analyze(SolverAlgorithm.BACKTRACKING, 7, options).executionAllowed());
        assertFalse(SolverPreflight.analyze(SolverAlgorithm.BRUTE_FORCE, 4, options).requiresConfirmation());
        assertTrue(SolverPreflight.analyze(SolverAlgorithm.BRUTE_FORCE, 5, options).requiresConfirmation());
    }

    @Test
    void rejectsInvalidInputBeforeCalculatingSpace() {
        assertThrows(IllegalArgumentException.class,
                () -> SolverPreflight.analyze(SolverAlgorithm.BRUTE_FORCE, 2, SolverExecutionOptions.unlimited()));
        assertThrows(NullPointerException.class,
                () -> SolverPreflight.analyze(null, 3, SolverExecutionOptions.unlimited()));
        assertThrows(NullPointerException.class, () -> SolverPreflight.analyze(SolverAlgorithm.BRUTE_FORCE, 3, null));
    }
}
